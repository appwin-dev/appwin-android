@file:OptIn(AppwinInternalApi::class)

package io.appwin.support.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.appwin.core.AppwinCore
import io.appwin.core.AppwinInternalApi
import io.appwin.core.network.RealtimeHub
import io.appwin.support.AppwinSupport
import io.appwin.support.data.ApiSupportRepository
import io.appwin.support.data.SupportRepository
import io.appwin.support.domain.Attachment
import io.appwin.support.domain.AttachmentInput
import io.appwin.support.domain.Conversation
import io.appwin.support.domain.FaqGroup
import io.appwin.support.domain.Message
import io.appwin.support.domain.MessageAuthorType
import io.appwin.support.domain.MessengerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** Local pick waiting on / finished with Core upload. */
internal data class PendingUpload(
  val id: String,
  val filename: String,
  val mimeType: String,
  val previewUri: String?,
  val progress: Float = 0f,
  val ref: AttachmentInput? = null,
)

/**
 * The last configuration the server returned, for the length of the process.
 *
 * The messenger opens on a spinner while it fetches its configuration, and the
 * studio's accent is *in* that configuration: with nothing to fall back on, the
 * first thing the user sees is the SDK's default orange, then the sheet repaints
 * in the studio's colour. Holding the previous answer makes every open after the
 * first start in the right palette.
 *
 * Deliberately not persisted: a colour cached across launches would outlive a
 * change made in the dashboard, and the spinner is not worth that risk.
 */
internal object MessengerConfigCache {
  @Volatile
  var last: MessengerConfig? = null
}

/**
 * The conversation whose thread is on screen, `null` when none is.
 *
 * What the in-app banner tests before announcing a reply. Composition is not the
 * signal: embedded in a host app's tab bar the messenger stays composed on every
 * tab, so suppressing while it exists would silence the banner for the whole app.
 * Suppressing while its *thread* is open is the thing actually meant - the
 * message is already visible.
 */
internal object OpenThread {
  @Volatile
  var conversationId: String? = null
}

/** Messenger home state. */
internal data class SupportUiState(
  val isLoading: Boolean = true,
  val loadFailed: Boolean = false,
  val config: MessengerConfig = MessengerConfigCache.last ?: MessengerConfig(),
  val conversations: List<Conversation> = emptyList(),
  val faqGroups: List<FaqGroup> = emptyList(),
  /** ISO 639-1 from the customer record; drives date / day-label locale. */
  val customerLanguage: String? = null,
)

/** State of one conversation thread. */
internal data class ThreadUiState(
  val isLoading: Boolean = true,
  val loadFailed: Boolean = false,
  val messages: List<Message> = emptyList(),
  val nextCursor: String? = null,
  val sending: Boolean = false,
  /** Agent currently typing, from the realtime `support.typing` event. */
  val peerIsTyping: Boolean = false,
  /** Customer message currently being edited in the composer, if any. */
  val editingMessageId: String? = null,
)

/**
 * Messenger state.
 *
 * One `ViewModel` for both home and the open thread: the configuration drives
 * both, and loading it separately would produce two offset loading states on the
 * same screen.
 */
internal class SupportViewModel(
  private val repository: SupportRepository = ApiSupportRepository(),
) : ViewModel() {

  private val _state = MutableStateFlow(SupportUiState())
  val state: StateFlow<SupportUiState> = _state.asStateFlow()

  private val _thread = MutableStateFlow(ThreadUiState())
  val thread: StateFlow<ThreadUiState> = _thread.asStateFlow()

  private val _pendingUploads = MutableStateFlow<List<PendingUpload>>(emptyList())
  val pendingUploads: StateFlow<List<PendingUpload>> = _pendingUploads.asStateFlow()

  /**
   * One-shot URLs to open after [openAttachment]. Collected by the thread UI
   * (Intent), so the ViewModel stays free of Android Context.
   */
  private val attachmentOpenRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
  val attachmentOpens: SharedFlow<String> = attachmentOpenRequests.asSharedFlow()

  private var openConversationId: String? = null

  private val realtimeSubIds = mutableListOf<UUID>()
  private var realtimeHub: RealtimeHub? = null

  /** Last typing state we posted, to avoid spamming the endpoint. */
  private var lastTypingSent = false
  private var typingStopJob: Job? = null
  private var peerTypingClearJob: Job? = null

  init {
    load()
    installRealtime()
    observeIdentity()
  }

  /** A messenger left open across an identify or a logout shows someone else's inbox. */
  private fun observeIdentity() {
    viewModelScope.launch {
      AppwinCore.identityChanges.collect { refreshConversationsSilently() }
    }
  }

  /**
   * Live updates over the shared realtime hub (ADR-0028 §9), as on iOS.
   *
   * The events carry a minimal payload and are delivered at most once, so each
   * one triggers a silent refetch rather than being applied as a patch: a lost
   * event costs a stale second, not a thread that never catches up.
   *
   * `onConnected` resyncs too, which is what covers a reconnection after the
   * screen was open with no network.
   *
   * Typing is the exception: it is ephemeral and applied as a local flag, not
   * refetched (there is nothing durable to read back).
   */
  private fun installRealtime() {
    val hub = AppwinCore.realtimeHub() ?: return
    realtimeHub = hub

    val resync = { refreshSilently() }
    realtimeSubIds += hub.on("support.message.created") { resync() }
    realtimeSubIds += hub.on("support.message.updated") { resync() }
    realtimeSubIds += hub.on("support.message.deleted") { resync() }
    realtimeSubIds += hub.on("support.conversation.created") { refreshConversationsSilently() }
    realtimeSubIds += hub.on("support.conversation.updated") { refreshConversationsSilently() }
    realtimeSubIds += hub.on("support.typing") { raw ->
      val payload = parseTypingPayload(raw) ?: return@on
      if (payload.typingActor != "agent") return@on
      viewModelScope.launch {
        applyPeerTyping(payload.conversationId, payload.isTyping)
      }
    }
    realtimeSubIds += hub.onConnected { resync() }
    hub.start()
  }

  private fun applyPeerTyping(conversationId: String?, isTyping: Boolean) {
    if (conversationId == null || conversationId != openConversationId) return
    peerTypingClearJob?.cancel()
    _thread.update { it.copy(peerIsTyping = isTyping) }
    if (isTyping) {
      peerTypingClearJob = viewModelScope.launch {
        delay(3_000)
        _thread.update { it.copy(peerIsTyping = false) }
      }
    }
  }

  /**
   * Draft changed in the composer: notify agents while a conversation exists.
   * Auto-clears after 2s of idle, same as iOS / dashboard.
   */
  fun onDraftChange(text: String) {
    val typing = text.trim().isNotEmpty()
    emitTyping(typing)
    typingStopJob?.cancel()
    if (typing) {
      typingStopJob = viewModelScope.launch {
        delay(2_000)
        emitTyping(false)
      }
    }
  }

  private fun emitTyping(isTyping: Boolean) {
    val conversationId = openConversationId ?: return
    if (lastTypingSent == isTyping) return
    lastTypingSent = isTyping
    viewModelScope.launch {
      runCatching { repository.emitTyping(conversationId, isTyping) }
    }
  }

  private fun clearTyping() {
    typingStopJob?.cancel()
    typingStopJob = null
    if (lastTypingSent) {
      lastTypingSent = false
      val conversationId = openConversationId ?: return
      viewModelScope.launch {
        runCatching { repository.emitTyping(conversationId, false) }
      }
    }
  }

  /**
   * Refetches the open thread and the conversation list without touching the
   * loading flags: a live update must not blank a screen the user is reading.
   */
  private fun refreshSilently() {
    refreshConversationsSilently()
    val conversationId = openConversationId ?: return
    // Only mark read while the thread is still on screen (same guard as iOS).
    if (conversationId != OpenThread.conversationId) return
    viewModelScope.launch {
      runCatching { repository.messages(conversationId) }
        .onSuccess { page ->
          _thread.update {
            it.copy(
              messages = page.items.sortedBy { m -> m.createdAtMillis },
              nextCursor = page.nextCursor,
            )
          }
        }
      // A message read while the thread is open must not come back unread.
      if (openConversationId == conversationId && OpenThread.conversationId == conversationId) {
        runCatching { repository.markRead(conversationId) }
          .onSuccess { markLocallyRead(conversationId) }
      }
    }
  }

  private fun refreshConversationsSilently() {
    viewModelScope.launch {
      runCatching { repository.conversations() }
        .onSuccess { page ->
          _state.update { state ->
            state.copy(
              conversations = page.items.sortedByDescending { c ->
                c.lastMessageAtMillis ?: c.createdAtMillis
              },
            )
          }
        }
    }
  }

  override fun onCleared() {
    clearTyping()
    // The hub is shared with the other products: we drop our own subscriptions
    // and leave it running.
    realtimeHub?.let { hub -> realtimeSubIds.forEach(hub::off) }
    realtimeSubIds.clear()
    realtimeHub = null
    OpenThread.conversationId = null
    super.onCleared()
  }

  fun load() {
    viewModelScope.launch {
      _state.update { it.copy(isLoading = true, loadFailed = false) }

      val config = runCatching { repository.config() }
      if (config.isFailure) {
        _state.update { it.copy(isLoading = false, loadFailed = true) }
        return@launch
      }
      MessengerConfigCache.last = config.getOrThrow()

      val conversations = runCatching { repository.conversations() }
        .map { it.items }
        .getOrDefault(emptyList())

      // The FAQ only loads when the studio has enabled it, and its failure must
      // not prevent writing to support, which is the screen's main purpose.
      val faq = if (config.getOrThrow().modules.faqEnabled) {
        runCatching { repository.faqGroups() }.getOrDefault(emptyList())
      } else {
        emptyList()
      }

      val language =
        runCatching { AppwinSupport.refreshCustomer().language }
          .getOrElse { AppwinSupport.currentCustomerLanguage() }

      _state.update {
        it.copy(
          isLoading = false,
          config = config.getOrThrow(),
          conversations = conversations.sortedByDescending { c ->
            c.lastMessageAtMillis ?: c.createdAtMillis
          },
          faqGroups = faq,
          customerLanguage = language,
        )
      }
    }
  }

  fun openConversation(conversationId: String) {
    clearTyping()
    peerTypingClearJob?.cancel()
    openConversationId = conversationId
    OpenThread.conversationId = conversationId
    _thread.value = ThreadUiState()

    viewModelScope.launch {
      runCatching { repository.messages(conversationId) }
        .onSuccess { page ->
          _thread.update {
            it.copy(
              isLoading = false,
              // The server paginates newest to oldest; a thread reads the other
              // way round.
              messages = page.items.sortedBy { m -> m.createdAtMillis },
              nextCursor = page.nextCursor,
            )
          }
        }
        .onFailure { _thread.update { it.copy(isLoading = false, loadFailed = true) } }

      // Mark read after displaying: the reverse would clear the badge with
      // nothing shown if the read failed.
      runCatching { repository.markRead(conversationId) }
        .onSuccess { markLocallyRead(conversationId) }
    }
  }

  /** Previous page of a thread: the older messages. */
  fun loadOlderMessages() {
    val conversationId = openConversationId ?: return
    val cursor = _thread.value.nextCursor ?: return

    viewModelScope.launch {
      runCatching { repository.messages(conversationId, cursor) }
        .onSuccess { page ->
          _thread.update {
            it.copy(
              messages = (page.items + it.messages).sortedBy { m -> m.createdAtMillis },
              nextCursor = page.nextCursor,
            )
          }
        }
    }
  }

  fun enqueueAttachment(
    bytes: ByteArray,
    mimeType: String,
    filename: String,
    previewUri: String?,
  ) {
    val id = UUID.randomUUID().toString()
    _pendingUploads.update {
      it + PendingUpload(
        id = id,
        filename = filename,
        mimeType = mimeType,
        previewUri = previewUri,
      )
    }
    viewModelScope.launch {
      runCatching {
        AppwinCore.uploadMedia(bytes, mimeType, filename) { progress ->
          _pendingUploads.update { list ->
            list.map { item ->
              if (item.id == id) item.copy(progress = progress.toFloat()) else item
            }
          }
        }
      }.onSuccess { uploaded ->
        _pendingUploads.update { list ->
          list.map { item ->
            if (item.id != id) item
            else item.copy(
              progress = 1f,
              ref = AttachmentInput(
                storageKey = uploaded.storageKey,
                mimeType = uploaded.mimeType,
                sizeBytes = uploaded.sizeBytes,
                filename = uploaded.filename,
              ),
            )
          }
        }
      }.onFailure {
        _pendingUploads.update { list -> list.filterNot { item -> item.id == id } }
      }
    }
  }

  fun removePendingUpload(id: String) {
    _pendingUploads.update { list -> list.filterNot { it.id == id } }
  }

  /** Puts a customer message into the composer for editing. */
  fun beginEdit(message: Message): String? {
    if (message.authorType != MessageAuthorType.CUSTOMER) return null
    if (message.id.startsWith("local:")) return null
    val text = message.body.trim()
    if (text.isEmpty()) return null
    clearTyping()
    _pendingUploads.value = emptyList()
    _thread.update { it.copy(editingMessageId = message.id) }
    return text
  }

  fun cancelEdit() {
    _thread.update { it.copy(editingMessageId = null) }
  }

  fun updateMessage(body: String, onDone: () -> Unit) {
    val conversationId = openConversationId ?: return
    val messageId = _thread.value.editingMessageId ?: return
    val text = body.trim()
    if (text.isEmpty()) return

    _thread.update { state ->
      state.copy(
        messages = state.messages.map { m ->
          if (m.id == messageId) m.copy(body = text, translatedBody = null) else m
        },
        editingMessageId = null,
      )
    }
    onDone()

    viewModelScope.launch {
      runCatching { repository.updateMessage(conversationId, messageId, text) }
        .onSuccess { updated ->
          _thread.update { state ->
            state.copy(messages = state.messages.map { if (it.id == updated.id) updated else it })
          }
          refreshCustomerLanguageSoon()
        }
        .onFailure { softReloadThread(conversationId) }
    }
  }

  fun deleteMessage(messageId: String) {
    if (messageId.startsWith("local:")) {
      removeLocalMessage(messageId)
      return
    }
    val conversationId = openConversationId ?: return
    if (_thread.value.editingMessageId == messageId) {
      cancelEdit()
    }
    removeLocalMessage(messageId)
    viewModelScope.launch {
      runCatching { repository.deleteMessage(conversationId, messageId) }
        .onFailure { softReloadThread(conversationId) }
    }
  }

  fun sendMessage(body: String, onSent: (String) -> Unit) {
    if (_thread.value.editingMessageId != null) {
      updateMessage(body) { onSent(openConversationId.orEmpty()) }
      return
    }
    val text = body.trim()
    val attachments = _pendingUploads.value.mapNotNull { it.ref }
    val stillUploading = _pendingUploads.value.any { it.ref == null }
    if (stillUploading) return
    if (text.isEmpty() && attachments.isEmpty()) return

    clearTyping()

    val localId = "local:${java.util.UUID.randomUUID()}"
    val optimistic = Message(
      id = localId,
      authorType = MessageAuthorType.CUSTOMER,
      authorName = null,
      body = text,
      translatedBody = null,
      readAtMillis = null,
      createdAtMillis = System.currentTimeMillis(),
      attachments = emptyList(),
      reactions = emptyList(),
    )

    // Show the bubble and clear the composer immediately; network follows.
    _pendingUploads.value = emptyList()
    _thread.update {
      it.copy(
        isLoading = false,
        messages = it.messages + optimistic,
      )
    }
    onSent(openConversationId ?: localId)

    viewModelScope.launch {
      val conversationId = openConversationId
      if (conversationId == null) {
        runCatching { repository.createConversation(text, attachments) }
          .onSuccess { conversation ->
            openConversationId = conversation.id
            OpenThread.conversationId = conversation.id
            _state.update { s ->
              s.copy(conversations = listOf(conversation) + s.conversations)
            }
            softReloadThread(conversation.id)
            refreshCustomerLanguageSoon()
          }
          .onFailure {
            removeLocalMessage(localId)
            _thread.update { it.copy(loadFailed = true) }
          }
      } else {
        runCatching { repository.sendMessage(conversationId, text, attachments) }
          .onSuccess { message ->
            replaceLocalMessage(localId, message)
            refreshCustomerLanguageSoon()
          }
          .onFailure {
            removeLocalMessage(localId)
            _thread.update { it.copy(loadFailed = true) }
          }
      }
    }
  }

  private fun refreshCustomerLanguageSoon() {
    viewModelScope.launch {
      delay(2_500)
      val language =
        runCatching { AppwinSupport.refreshCustomer().language }
          .getOrElse { AppwinSupport.currentCustomerLanguage() }
      _state.update { it.copy(customerLanguage = language) }
    }
  }

  private fun removeLocalMessage(localId: String) {
    _thread.update { t -> t.copy(messages = t.messages.filterNot { it.id == localId }) }
  }

  private fun replaceLocalMessage(localId: String, message: Message) {
    _thread.update { t ->
      val withoutLocal = t.messages.filterNot { it.id == localId || it.id == message.id }
      t.copy(messages = (withoutLocal + message).sortedBy { m -> m.createdAtMillis })
    }
  }

  /** Reload the thread without a full-screen loader; keep unmatched local bubbles. */
  private suspend fun softReloadThread(conversationId: String) {
    runCatching { repository.messages(conversationId) }
      .onSuccess { page ->
        _thread.update { t ->
          val locals = t.messages.filter { it.id.startsWith("local:") }
          val server = page.items.sortedBy { m -> m.createdAtMillis }
          t.copy(
            isLoading = false,
            messages = mergeLocals(server, locals),
            nextCursor = page.nextCursor,
          )
        }
      }
      .onFailure { _thread.update { it.copy(isLoading = false, loadFailed = true) } }

    runCatching { repository.markRead(conversationId) }
      .onSuccess { markLocallyRead(conversationId) }
  }

  private fun mergeLocals(server: List<Message>, locals: List<Message>): List<Message> {
    if (locals.isEmpty()) return server
    val kept = locals.filter { local ->
      server.none { it.authorType == MessageAuthorType.CUSTOMER && it.body == local.body }
    }
    return (server + kept).sortedBy { it.createdAtMillis }
  }

  fun toggleReaction(messageId: String, emoji: String) {
    val conversationId = openConversationId ?: return
    viewModelScope.launch {
      runCatching { repository.toggleReaction(conversationId, messageId, emoji) }
        .onSuccess { updated ->
          _thread.update { state ->
            state.copy(messages = state.messages.map { if (it.id == updated.id) updated else it })
          }
        }
    }
  }

  /** Prepares an empty thread; the next message creates the conversation. */
  fun startNewConversation() {
    clearTyping()
    peerTypingClearJob?.cancel()
    openConversationId = null
    OpenThread.conversationId = null
    _pendingUploads.value = emptyList()
    _thread.value = ThreadUiState(isLoading = false)
  }

  /**
   * Leaves the open thread without starting a new one.
   *
   * Clears [openConversationId] so a realtime resync on home does not call
   * markRead on a conversation the user is no longer viewing.
   */
  fun leaveConversation() {
    clearTyping()
    peerTypingClearJob?.cancel()
    openConversationId = null
    OpenThread.conversationId = null
    _pendingUploads.value = emptyList()
  }

  fun openAttachment(attachment: Attachment) {
    viewModelScope.launch {
      val url = resolveAttachmentUrl(attachment)
      attachmentOpenRequests.tryEmit(url)
    }
  }

  /** Fresh signed URL when possible; falls back to the message payload URL. */
  suspend fun resolveAttachmentUrl(attachment: Attachment): String =
    runCatching { repository.freshAttachmentUrl(attachment.id) }.getOrElse { attachment.url }

  private fun markLocallyRead(conversationId: String) {
    val now = System.currentTimeMillis()
    _state.update { state ->
      state.copy(
        conversations = state.conversations.map {
          if (it.id == conversationId) it.copy(lastReadAtMillis = now) else it
        },
      )
    }
  }
}

private data class TypingPayload(
  val conversationId: String?,
  val isTyping: Boolean,
  val typingActor: String?,
)

/** Parses `support.typing` payload from the hub (JsonElement, optionally array-wrapped). */
private fun parseTypingPayload(raw: Any?): TypingPayload? {
  val element = raw as? JsonElement ?: return null
  val obj: JsonObject = when (element) {
    is JsonObject -> element
    is JsonArray -> element.firstOrNull()?.jsonObject ?: return null
    else -> return null
  }
  val conversationId =
    obj["conversationId"]?.jsonPrimitive?.contentOrNull
      ?: obj["resourceId"]?.jsonPrimitive?.contentOrNull
  return TypingPayload(
    conversationId = conversationId,
    isTyping = obj["isTyping"]?.jsonPrimitive?.booleanOrNull ?: false,
    typingActor = obj["typingActor"]?.jsonPrimitive?.contentOrNull,
  )
}

package io.appwin.support.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.appwin.support.data.ApiSupportRepository
import io.appwin.support.data.SupportRepository
import io.appwin.support.domain.Conversation
import io.appwin.support.domain.FaqGroup
import io.appwin.support.domain.Message
import io.appwin.support.domain.MessengerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Messenger home state. */
internal data class SupportUiState(
  val isLoading: Boolean = true,
  val loadFailed: Boolean = false,
  val config: MessengerConfig = MessengerConfig(),
  val conversations: List<Conversation> = emptyList(),
  val faqGroups: List<FaqGroup> = emptyList(),
)

/** State of one conversation thread. */
internal data class ThreadUiState(
  val isLoading: Boolean = true,
  val loadFailed: Boolean = false,
  val messages: List<Message> = emptyList(),
  val nextCursor: String? = null,
  val sending: Boolean = false,
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

  private var openConversationId: String? = null

  init {
    load()
  }

  fun load() {
    viewModelScope.launch {
      _state.update { it.copy(isLoading = true, loadFailed = false) }

      val config = runCatching { repository.config() }
      if (config.isFailure) {
        _state.update { it.copy(isLoading = false, loadFailed = true) }
        return@launch
      }

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

      _state.update {
        it.copy(
          isLoading = false,
          config = config.getOrThrow(),
          conversations = conversations.sortedByDescending { c ->
            c.lastMessageAtMillis ?: c.createdAtMillis
          },
          faqGroups = faq,
        )
      }
    }
  }

  fun openConversation(conversationId: String) {
    openConversationId = conversationId
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

  fun sendMessage(body: String, onSent: (String) -> Unit) {
    val text = body.trim()
    if (text.isEmpty() || _thread.value.sending) return

    viewModelScope.launch {
      _thread.update { it.copy(sending = true) }

      val conversationId = openConversationId
      val result = if (conversationId == null) {
        // No conversation open: the first message creates one. That is the
        // expected path for someone writing in for the first time - they must
        // not have to "create a ticket".
        runCatching { repository.createConversation(text) }
          .onSuccess { conversation ->
            openConversationId = conversation.id
            _state.update { s -> s.copy(conversations = listOf(conversation) + s.conversations) }
            openConversation(conversation.id)
            onSent(conversation.id)
          }
          .map { }
      } else {
        runCatching { repository.sendMessage(conversationId, text) }
          .onSuccess { message ->
            _thread.update { it.copy(messages = it.messages + message) }
          }
          .map { }
      }

      _thread.update { it.copy(sending = false) }
      result.onFailure { _thread.update { it.copy(loadFailed = true) } }
    }
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
    openConversationId = null
    _thread.value = ThreadUiState(isLoading = false)
  }

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

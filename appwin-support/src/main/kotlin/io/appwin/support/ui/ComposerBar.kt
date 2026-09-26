package io.appwin.support.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Composer card: text field, pending thumbnails, Solar toolbar (Figma
 * InputMessageSupport without macros) and send - same surface as iOS
 * `MessengerComposer`.
 */
@Composable
internal fun Composer(
  draft: String,
  onDraftChange: (String) -> Unit,
  pending: List<PendingUpload>,
  sending: Boolean,
  accent: Color,
  onAccent: Color,
  radius: androidx.compose.ui.unit.Dp,
  strings: SupportStrings,
  isEditing: Boolean = false,
  onCancelEdit: () -> Unit = {},
  onEnqueue: (bytes: ByteArray, mimeType: String, filename: String, previewUri: String?) -> Unit,
  onRemovePending: (String) -> Unit,
  onSend: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val shape = RoundedCornerShape(radius)
  val cardShape = RoundedCornerShape(16.dp)
  val uploading = pending.any { it.ref == null }
  val canSend =
    (draft.isNotBlank() || (!isEditing && pending.any { it.ref != null })) && !sending && !uploading
  var showEmojiPicker by remember { mutableStateOf(false) }

  fun ingest(uris: List<Uri>) {
    scope.launch {
      for (uri in uris) {
        val meta = withContext(Dispatchers.IO) { readUri(context.contentResolver, uri) } ?: continue
        onEnqueue(meta.bytes, meta.mimeType, meta.filename, uri.toString())
      }
    }
  }

  val imagePicker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
  ) { uris -> ingest(uris) }

  val videoPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
  ) { uris -> ingest(uris) }

  val filePicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris -> ingest(uris) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .shadowCard(cardShape)
      .clip(cardShape)
      .background(SupportTokens.surface)
      .border(1.dp, SupportTokens.borderSubtle, cardShape),
  ) {
    if (isEditing) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = strings.editingBanner,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          color = SupportTokens.textSecondary,
          modifier = Modifier.weight(1f),
        )
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = strings.cancel,
          tint = SupportTokens.textSecondary,
          modifier = Modifier
            .size(18.dp)
            .clickable(onClick = onCancelEdit),
        )
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(SupportTokens.border),
      )
    }

    if (pending.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        pending.forEach { item ->
          PendingThumb(item, onRemove = { onRemovePending(item.id) })
        }
      }
    }

    BasicTextField(
      value = draft,
      onValueChange = onDraftChange,
      textStyle = TextStyle(
        fontSize = SupportTokens.bodyText,
        // Keep line box tight: a taller lineHeight lifts the caret above the
        // glyph / placeholder (empty-field cursor looks "too high").
        lineHeight = SupportTokens.bodyText,
        color = SupportTokens.textMain,
      ),
      cursorBrush = SolidColor(accent),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      maxLines = 5,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp)
        .padding(top = 12.dp, bottom = 10.dp),
      decorationBox = { field ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 20.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (draft.isEmpty()) {
            Text(
              text = strings.messagePlaceholder,
              fontSize = SupportTokens.bodyText,
              lineHeight = SupportTokens.bodyText,
              color = SupportTokens.textTertiary,
            )
          }
          field()
        }
      },
    )

    // Same hairline as iOS `MessengerComposer` between the field and the toolbar.
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(SupportTokens.border),
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Box {
        ToolbarIcon(
          icon = SupportIcons.SmileCircle,
          label = strings.emoji,
          onClick = { showEmojiPicker = true },
        )
        DropdownMenu(
          expanded = showEmojiPicker,
          onDismissRequest = { showEmojiPicker = false },
        ) {
          ComposerEmojiPicker { emoji ->
            onDraftChange(draft + emoji)
            showEmojiPicker = false
          }
        }
      }

      Box(
        modifier = Modifier
          .width(1.dp)
          .height(16.dp)
          .background(SupportTokens.border),
      )

      ToolbarIcon(
        icon = SupportIcons.Gallery,
        label = strings.attachImage,
        onClick = {
          imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
          )
        },
      )
      ToolbarIcon(
        icon = SupportIcons.Video,
        label = strings.attachVideo,
        onClick = {
          videoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
          )
        },
      )
      ToolbarIcon(
        icon = SupportIcons.Paperclip,
        label = strings.attachFile,
        onClick = { filePicker.launch(arrayOf("*/*")) },
      )

      Box(Modifier.weight(1f))

      val sendEnabled = canSend
      // Capsule + 32dp height matches iOS send (icons row, not a tall pill).
      Row(
        modifier = Modifier
          .height(32.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(if (sendEnabled) accent else accent.copy(alpha = 0.4f))
          .clickable(enabled = sendEnabled, onClick = onSend)
          .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = if (isEditing) strings.save else strings.send,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = if (sendEnabled) onAccent else onAccent.copy(alpha = 0.5f),
        )
        if (sending || uploading) {
          CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = onAccent,
            strokeWidth = 2.dp,
          )
        } else {
          Icon(
            SupportIcons.Send,
            contentDescription = null,
            tint = if (sendEnabled) onAccent else onAccent.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .size(32.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = SupportTokens.textTertiary,
      modifier = Modifier.size(16.dp),
    )
  }
}

/** Compact emoji grid - dashboard / iOS SmileCircle parity. */
@Composable
private fun ComposerEmojiPicker(onPick: (String) -> Unit) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(8),
    modifier = Modifier
      .width(300.dp)
      .height(280.dp)
      .padding(12.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    items(ComposerEmojis) { emoji ->
      Box(
        modifier = Modifier
          .height(36.dp)
          .fillMaxWidth()
          .clickable { onPick(emoji) },
        contentAlignment = Alignment.Center,
      ) {
        Text(text = emoji, fontSize = 22.sp)
      }
    }
  }
}

private val ComposerEmojis = listOf(
  "😀", "😃", "😄", "😁", "😅", "😂", "🤣", "😊",
  "😇", "🙂", "😉", "😍", "🥰", "😘", "😗", "😋",
  "😜", "🤪", "🤨", "🧐", "😎", "🤩", "🥳", "😏",
  "😒", "😞", "😔", "😟", "😕", "🙁", "😣", "😖",
  "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡",
  "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰",
  "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶",
  "👍", "👎", "👏", "🙌", "🤝", "🙏", "💪", "✌️",
  "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔",
  "🔥", "✨", "⭐", "💯", "✅", "❌", "🎉", "🎊",
)

@Composable
private fun PendingThumb(item: PendingUpload, onRemove: () -> Unit) {
  val context = LocalContext.current
  var videoPoster by remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

  LaunchedEffect(item.id, item.previewUri, item.mimeType) {
    if (item.previewUri != null && item.mimeType.startsWith("video/")) {
      videoPoster = withContext(Dispatchers.IO) {
        runCatching {
          val retriever = android.media.MediaMetadataRetriever()
          retriever.setDataSource(context, Uri.parse(item.previewUri))
          val frame = retriever.frameAtTime
          retriever.release()
          frame
        }.getOrNull()
      }
    }
  }

  Box(
    modifier = Modifier
      .size(64.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(SupportTokens.sheetBackground),
  ) {
    when {
      item.previewUri != null && item.mimeType.startsWith("image/") -> {
        AsyncImage(
          model = item.previewUri,
          contentDescription = item.filename,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
      videoPoster != null -> {
        AsyncImage(
          model = videoPoster,
          contentDescription = item.filename,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier = Modifier
            .align(Alignment.Center)
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            SupportIcons.Video,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
          )
        }
      }
      else -> {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = item.filename.take(8),
            fontSize = SupportTokens.captionText,
            color = SupportTokens.textSecondary,
            maxLines = 2,
          )
        }
      }
    }

    if (item.ref == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(
          progress = { item.progress.coerceIn(0.05f, 1f) },
          modifier = Modifier.size(26.dp),
          color = Color.White,
          strokeWidth = 3.dp,
        )
      }
    }

    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(4.dp)
        .size(18.dp)
        .clip(CircleShape)
        .background(Color.Black.copy(alpha = 0.55f))
        .clickable(onClick = onRemove),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
    }
  }
}

private fun Modifier.shadowCard(shape: RoundedCornerShape): Modifier =
  this.then(
    Modifier
      .clip(shape),
  )

private data class UriPayload(val bytes: ByteArray, val mimeType: String, val filename: String)

private fun readUri(resolver: ContentResolver, uri: Uri): UriPayload? {
  val mime = resolver.getType(uri) ?: "application/octet-stream"
  val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    ?.use { cursor ->
      val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
    }
    ?: "file"
  val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
  if (bytes.isEmpty()) return null
  return UriPayload(bytes, mime, name)
}

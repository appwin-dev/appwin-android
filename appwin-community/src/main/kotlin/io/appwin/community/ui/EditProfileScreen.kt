package io.appwin.community.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.appwin.community.domain.CommunityProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EditProfileScreen(
  profile: CommunityProfile,
  viewModel: CommunityViewModel,
  strings: CommunityStrings,
  onBack: () -> Unit,
  onSaved: (CommunityProfile) -> Unit,
  leaveAnonymity: Boolean = false,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var nickname by remember { mutableStateOf(if (profile.isAnonymous) "" else profile.nickname) }
  var bio by remember { mutableStateOf(profile.bio.orEmpty()) }
  var isAnonymous by remember {
    mutableStateOf(if (leaveAnonymity) false else profile.isAnonymous)
  }
  var avatarUrl by remember { mutableStateOf(profile.avatarUrl) }
  var saving by remember { mutableStateOf(false) }
  var uploading by remember { mutableStateOf(false) }
  val bioLimit = 200
  val canSave = !saving && !uploading && (isAnonymous || nickname.trim().length >= 2)

  val picker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
  ) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    uploading = true
    scope.launch {
      runCatching {
        val bytes = withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: error("empty")
        val (compressed, mime) = ImageCompressor.compress(bytes, ImageCompressor.AvatarMaxSidePx)
        viewModel.uploadMedia(compressed, mime, "avatar.jpg")
      }.onSuccess { avatarUrl = it.publicUrl }
      uploading = false
    }
  }

  fun save() {
    if (!canSave) return
    saving = true
    scope.launch {
      runCatching {
        viewModel.updateOwnProfile(
          nickname = if (isAnonymous) null else nickname.trim(),
          bio = bio.trim(),
          avatarUrl = avatarUrl,
          isAnonymous = isAnonymous,
        )
      }.onSuccess(onSaved)
      saving = false
    }
  }

  // Figma « Édition Profil » (116:8212 anonymous, 116:8249 named, 178:2297 typing the name).
  Scaffold(
    containerColor = CommunityColors.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TextNavBar(
        title = strings.editProfileTitle,
        leadingLabel = strings.cancel,
        onLeading = onBack,
        containerColor = CommunityColors.background,
        trailing = {
          CommunityActionPill(title = strings.save, enabled = canSave, loading = saving, onClick = ::save)
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .windowInsetsPadding(communityBottomWithImeInsets())
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      EditableAvatar(
        url = avatarUrl,
        name = nickname.trim().ifBlank { profile.nickname },
        // A photo is only for a named profile: anonymity must stay visible.
        editable = !isAnonymous,
        uploading = uploading,
        label = strings.addPhoto,
        onPick = {
          isAnonymous = false
          picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
      )
      NameRow(
        isAnonymous = isAnonymous,
        anonymousName = profile.nickname,
        nickname = nickname,
        onNicknameChange = { nickname = it },
        strings = strings,
      )
      Separator()
      Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(strings.anonymous, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CommunityColors.textPrimary)
          Text(strings.anonymousHint, fontSize = 12.sp, color = CommunityColors.textTertiary)
        }
        Switch(
          checked = isAnonymous,
          onCheckedChange = {
            isAnonymous = it
            if (it) avatarUrl = null
          },
          colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedTrackColor = CommunityColors.surfaceMuted,
            uncheckedBorderColor = CommunityColors.border,
            uncheckedThumbColor = CommunityColors.surface,
          ),
        )
      }
      Separator()
      CommunityTextArea(
        label = strings.bio,
        value = bio,
        onValueChange = { bio = it },
        placeholder = strings.bioPlaceholder,
        strings = strings,
        limit = bioLimit,
      )
    }
  }
}

/** 112dp avatar on the left; the pen badge only when a photo can be set. */
@Composable
private fun EditableAvatar(
  url: String?,
  name: String,
  editable: Boolean,
  uploading: Boolean,
  label: String,
  onPick: () -> Unit,
) {
  Box(modifier = Modifier.clickable(onClickLabel = label, onClick = onPick)) {
    CommunityAvatar(url, name, size = 112)
    if (editable) {
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .size(32.dp)
          .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = CommunityColors.shadowLow, spotColor = CommunityColors.shadowLow)
          .clip(RoundedCornerShape(12.dp))
          .background(CommunityColors.surface),
        contentAlignment = Alignment.Center,
      ) {
        Icon(SolarModerationIcons.Pen, contentDescription = null, tint = CommunityColors.textPrimary, modifier = Modifier.size(16.dp))
      }
    }
    if (uploading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
  }
}

/**
 * The name: the generated one with its « Pseudo Anonyme » chip when anonymous,
 * else edited in place behind its pen (« Ton pseudo » until one is typed).
 */
@Composable
private fun NameRow(
  isAnonymous: Boolean,
  anonymousName: String,
  nickname: String,
  onNicknameChange: (String) -> Unit,
  strings: CommunityStrings,
) {
  var editing by remember { mutableStateOf(nickname.isBlank()) }
  val focus = remember { FocusRequester() }
  val nameStyle = TextStyle(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = CommunityColors.textPrimary)
  when {
    isAnonymous -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(anonymousName, style = nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
      Text(
        strings.anonymousPseudo,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = CommunityColors.textTertiary,
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(CommunityColors.surfaceMuted)
          .padding(horizontal = 6.dp, vertical = 4.dp),
      )
    }
    editing || nickname.isBlank() -> {
      BasicTextField(
        value = nickname,
        onValueChange = onNicknameChange,
        singleLine = true,
        textStyle = nameStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = { inner ->
          Box {
            if (nickname.isEmpty()) Text(strings.nicknamePlaceholder, style = nameStyle.copy(color = CommunityColors.textTertiary))
            inner()
          }
        },
      )
      LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
    else -> Row(
      modifier = Modifier.clickable(onClickLabel = strings.nickname) { editing = true },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(nickname.trim(), style = nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
      Icon(SolarModerationIcons.Pen, contentDescription = null, tint = CommunityColors.textPrimary, modifier = Modifier.size(16.dp))
    }
  }
}

@Composable
private fun Separator() {
  Box(Modifier.fillMaxWidth().height(1.dp).background(CommunityColors.surfaceMuted))
}

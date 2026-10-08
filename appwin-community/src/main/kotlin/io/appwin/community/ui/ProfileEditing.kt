package io.appwin.community.ui

import io.appwin.community.AppwinCommunity

/** Every SDK entry point that edits nickname, photo or bio goes through here. */
internal fun openProfileEditor(openSdkEditor: () -> Unit) {
  val host = AppwinCommunity.onEditProfile
  if (host != null) host() else openSdkEditor()
}

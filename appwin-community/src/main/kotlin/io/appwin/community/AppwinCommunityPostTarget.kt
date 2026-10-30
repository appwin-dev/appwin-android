package io.appwin.community

/**
 * A post to open, as carried by a Community notification.
 *
 * @property postId the post.
 * @property commentId the root comment whose reply thread should open on top of
 *   the post, `null` to open the post alone.
 */
public data class AppwinCommunityPostTarget(
  public val postId: String,
  public val commentId: String? = null,
)

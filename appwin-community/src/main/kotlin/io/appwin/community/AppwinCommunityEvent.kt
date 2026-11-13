package io.appwin.community

/**
 * Something the current member did in Community, reported through
 * [AppwinCommunity.events].
 *
 * Only the member's own actions, and only once the server has confirmed them:
 * an optimistic update that fails is never reported. Other members' activity
 * does not go through here.
 */
public sealed interface AppwinCommunityEvent {
  /** The member published a post. */
  public data class PostCreated(public val postId: String) : AppwinCommunityEvent

  /** The member commented on a post. */
  public data class CommentCreated(
    public val commentId: String,
    public val postId: String,
  ) : AppwinCommunityEvent

  /**
   * The member replied to a comment.
   *
   * @property commentId the comment replied to.
   */
  public data class ReplyCreated(
    public val replyId: String,
    public val commentId: String,
    public val postId: String,
  ) : AppwinCommunityEvent

  /**
   * The member added, changed or removed their reaction.
   *
   * @property commentId set when the reaction is on a comment of [postId],
   *   `null` when it is on the post itself.
   * @property reaction the reaction key as the API names it (`like`, `love`,
   *   ...), `null` when the reaction was removed.
   */
  public data class ReactionModified(
    public val postId: String,
    public val commentId: String?,
    public val reaction: String?,
  ) : AppwinCommunityEvent

  /** The member's profile changed, from the SDK's editor or through [AppwinCommunity.setUser]. */
  public data class ProfileUpdated(public val profileId: String) : AppwinCommunityEvent
}

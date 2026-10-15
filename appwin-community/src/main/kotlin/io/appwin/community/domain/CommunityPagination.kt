package io.appwin.community.domain

/**
 * Shared page sizes for Community lists.
 *
 * Keep in sync with iOS `CommunityPagination` and the API
 * `CommunityFeedParamsSchema` default (20, max 50).
 */
public object CommunityPagination {
  /** Feed / profile publications page size. */
  public const val FEED_PAGE_SIZE: Int = 20

  /** Root comments page size (offset pagination). */
  public const val COMMENTS_PAGE_SIZE: Int = 20
}

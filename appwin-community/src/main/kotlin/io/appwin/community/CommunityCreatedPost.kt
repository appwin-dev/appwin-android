package io.appwin.community

import android.graphics.BitmapFactory
import io.appwin.community.data.CommunityRepository
import io.appwin.community.domain.CommunityMedia
import io.appwin.community.domain.CommunityMediaType
import io.appwin.community.domain.CommunityPost
import io.appwin.community.ui.ImageCompressor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A post created by [AppwinCommunity.createPost]. */
public data class CommunityCreatedPost(
  /** The post's id, for [AppwinCommunity.openPost]. */
  public val id: String,
  public val status: Status,
) {
  /** Where moderation put the post. */
  public enum class Status(public val wire: String) {
    /** Online: the community sees it. */
    PUBLISHED("published"),

    /**
     * Held for review (or the member is shadow banned): only its author sees
     * it, until a moderator publishes it.
     */
    PENDING_REVIEW("pendingReview"),

    /** Refused by moderation as it was written. Its author is told why in the feed's notifications. */
    REMOVED("removed"),
  }

  /** Shape carried over the Flutter and React Native bridges. */
  public fun toMap(): Map<String, Any> = mapOf("id" to id, "status" to status.wire)

  internal companion object {
    fun from(post: CommunityPost): CommunityCreatedPost = CommunityCreatedPost(
      id = post.id,
      status = when {
        post.isRemoved -> Status.REMOVED
        post.isPendingReview -> Status.PENDING_REVIEW
        else -> Status.PUBLISHED
      },
    )
  }
}

/** The most photos one post carries, the server's own limit. */
internal const val CREATE_POST_MAX_IMAGES: Int = 10

/** The checks [AppwinCommunity.createPost] makes before any request; returns the trimmed text. */
internal fun validateCreatePost(text: String, imageCount: Int): String {
  val body = text.trim()
  require(body.isNotEmpty()) { "createPost: text is empty" }
  require(imageCount <= CREATE_POST_MAX_IMAGES) {
    "createPost: $imageCount images, at most $CREATE_POST_MAX_IMAGES"
  }
  return body
}

/** Downsizes, uploads in parallel, returns in the caller's order. */
internal suspend fun uploadPostImages(
  images: List<ByteArray>,
  repository: CommunityRepository,
): List<CommunityMedia> = coroutineScope {
  images.mapIndexed { index, bytes ->
    async {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      require(bounds.outWidth > 0 && bounds.outHeight > 0) {
        "createPost: image $index could not be decoded"
      }
      val (compressed, mime) = ImageCompressor.compress(bytes, ImageCompressor.PostMaxSidePx)
      val uploaded = repository.uploadMedia(compressed, mime, "post.jpg")
      CommunityMedia(
        url = uploaded.publicUrl,
        width = uploaded.width,
        height = uploaded.height,
        type = CommunityMediaType.IMAGE,
      )
    }
  }.awaitAll()
}

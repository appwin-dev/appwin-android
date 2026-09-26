package io.appwin.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import io.appwin.core.push.AppwinPush
import java.net.URL

/**
 * Displays FCM notification payloads when the app is in the foreground.
 * Background delivery is handled by the system tray (including [RemoteMessage.Notification.imageUrl]).
 */
internal object PushNotificationPresenter {
  private const val CHANNEL_ID = "appwin_push"
  private const val CHANNEL_NAME = "Appwin"

  fun show(context: Context, message: RemoteMessage) {
    val notification = message.notification ?: return
    ensureChannel(context)

    val imageUrl = notification.imageUrl?.toString()?.takeIf { it.isNotBlank() }
      ?: message.data["imageUrl"]?.takeIf { it.isNotBlank() }
    val deeplink = message.data["deeplink"]?.takeIf { it.isNotBlank() }
    val deliveryId = message.data["deliveryId"]?.takeIf { it.isNotBlank() }
    val bitmap = imageUrl?.let { loadBitmap(it) }

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(notification.title)
      .setContentText(notification.body)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)

    val tag = message.messageId ?: deliveryId ?: "appwin"
    if (deeplink != null || AppwinPush.isAppwinPush(message.data)) {
      builder.setContentIntent(tapPendingIntent(context, message.data, tag))
    }

    if (bitmap != null) {
      builder.setLargeIcon(bitmap)
      builder.setStyle(
        NotificationCompat.BigPictureStyle()
          .bigPicture(bitmap)
          .bigLargeIcon(null as Bitmap?),
      )
    }

    NotificationManagerCompat.from(context).notify(tag, tag.hashCode(), builder.build())
  }

  /**
   * Carries the whole `data` map, the same extras the system puts on the
   * launch intent for a notification it displays itself, so both taps go
   * through the same `AppwinPush.handleTap(context, intent)`.
   */
  private fun tapPendingIntent(
    context: Context,
    data: Map<String, String>,
    tag: String,
  ): PendingIntent {
    val intent = Intent(context, PushTapActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      data.forEach { (key, value) -> putExtra(key, value) }
    }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    // One request code per notification: sharing one would let FLAG_UPDATE_CURRENT
    // rewrite the extras of an earlier notification still in the tray.
    return PendingIntent.getActivity(context, tag.hashCode(), intent, flags)
  }

  private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH),
    )
  }

  private fun loadBitmap(url: String): Bitmap? =
    runCatching {
      URL(url).openStream().use { stream -> BitmapFactory.decodeStream(stream) }
    }.getOrNull()
}

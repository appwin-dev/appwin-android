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

    if (deeplink != null) {
      builder.setContentIntent(tapPendingIntent(context, deeplink, deliveryId))
    }

    if (bitmap != null) {
      builder.setLargeIcon(bitmap)
      builder.setStyle(
        NotificationCompat.BigPictureStyle()
          .bigPicture(bitmap)
          .bigLargeIcon(null as Bitmap?),
      )
    }

    val tag = message.messageId ?: deliveryId ?: "appwin"
    NotificationManagerCompat.from(context).notify(tag, tag.hashCode(), builder.build())
  }

  private fun tapPendingIntent(
    context: Context,
    deeplink: String,
    deliveryId: String?,
  ): PendingIntent {
    val intent = Intent(context, PushTapActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(PushDeepLinkHandler.EXTRA_DEEPLINK, deeplink)
      if (deliveryId != null) putExtra(PushDeepLinkHandler.EXTRA_DELIVERY_ID, deliveryId)
    }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getActivity(context, deeplink.hashCode(), intent, flags)
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

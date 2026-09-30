package io.appwin.core.push

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.appwin.core.AppwinInternalApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(AppwinInternalApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppwinPushTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val opened = mutableListOf<String>()
  private var now = 1_000_000L

  private class Recorder(
    private val foreground: Boolean = false,
    private val message: Boolean = false,
  ) : AppwinPushHandler {
    val taps = mutableListOf<AppwinPushPayload>()
    val messages = mutableListOf<AppwinPushPayload>()

    override fun onTap(context: Context, payload: AppwinPushPayload) {
      taps += payload
    }

    override fun onForeground(context: Context, payload: AppwinPushPayload): Boolean = foreground

    override fun onMessage(context: Context, payload: AppwinPushPayload): Boolean {
      messages += payload
      return message
    }
  }

  private val supportPush = mapOf(
    "appwinType" to "support.message",
    "appwinVersion" to "1",
    "deeplink" to "appwin://support/conversation/c-42",
  )

  @Before
  fun setUp() {
    AppwinPush.resetForTesting()
    AppwinPush.urlOpener = { _, url -> opened += url }
    AppwinPush.clock = { now }
  }

  @After
  fun tearDown() {
    AppwinPush.resetForTesting()
  }

  @Test
  fun `typed payload resolves its product from the type prefix`() {
    val payload = AppwinPushPayload.parse(supportPush)!!
    assertEquals("support.message", payload.type)
    assertEquals("support", payload.product)
    assertEquals("support", payload.deeplinkProduct)
    assertFalse(payload.isSilent)
  }

  @Test
  fun `inapp pending belongs to notifications and is silent`() {
    val payload = AppwinPushPayload.parse(mapOf("appwinType" to "inapp.pending"))!!
    assertEquals("notifications", payload.product)
    assertTrue(payload.isSilent)
  }

  @Test
  fun `legacy payloads without a type are still recognised`() {
    assertEquals(
      "support",
      AppwinPushPayload.parse(mapOf("deeplink" to "appwin://support/conversation/c-1"))!!.product,
    )
    assertEquals(
      "support",
      AppwinPushPayload.parse(mapOf("deeplink" to "appwin:///support/conversation/c-1"))!!.product,
    )
    assertEquals(
      "notifications",
      AppwinPushPayload.parse(mapOf("deliveryId" to "d-1", "deeplink" to "https://x.io"))!!.product,
    )
  }

  @Test
  fun `a host push is not Appwin's`() {
    assertFalse(AppwinPush.isAppwinPush(mapOf("deeplink" to "https://example.com")))
    assertFalse(AppwinPush.isAppwinPush(mapOf("title" to "Hi")))
    assertFalse(AppwinPush.handleTap(context, mapOf("title" to "Hi")))
    assertTrue(opened.isEmpty())
  }

  @Test
  fun `keys nested under a data JSON string are read`() {
    val payload = AppwinPushPayload.parse(
      mapOf("data" to """{"appwinType":"support.message","deeplink":"appwin://support/conversation/c-9"}"""),
    )!!
    assertEquals("support", payload.product)
    assertEquals("appwin://support/conversation/c-9", payload.deeplink)
  }

  @Test
  fun `a tap before the product registers is kept then replayed`() {
    assertTrue(AppwinPush.handleTap(context, supportPush))

    val support = Recorder()
    AppwinPush.register("support", support)

    assertEquals(1, support.taps.size)
    assertEquals("appwin://support/conversation/c-42", support.taps.single().deeplink)
    // Replayed once, not on every registration.
    AppwinPush.register("support", support)
    assertEquals(1, support.taps.size)
  }

  @Test
  fun `an appwin deeplink is never handed to the system`() {
    AppwinPush.handleTap(context, supportPush)
    AppwinPush.openDeeplink(context, "appwin://support/conversation/c-1")
    assertTrue(opened.isEmpty())
  }

  @Test
  fun `an external deeplink opens right away, before any product registers`() {
    AppwinPush.handleTap(
      context,
      mapOf("appwinType" to "notifications.campaign", "deeplink" to "https://shop.example/sale"),
    )
    assertEquals(listOf("https://shop.example/sale"), opened)
  }

  @Test
  fun `a campaign linking into support reaches both products`() {
    val support = Recorder()
    val notifications = Recorder()
    AppwinPush.register("support", support)
    AppwinPush.register("notifications", notifications)

    AppwinPush.handleTap(
      context,
      mapOf(
        "appwinType" to "notifications.campaign",
        "deliveryId" to "d-1",
        "deeplink" to "appwin://support/conversation/c-1",
      ),
    )

    assertEquals(1, support.taps.size)
    assertEquals(1, notifications.taps.size)
  }

  @Test
  fun `a legacy support deeplink with a deliveryId is still tracked by notifications`() {
    val notifications = Recorder()
    AppwinPush.register("notifications", notifications)
    AppwinPush.handleTap(
      context,
      mapOf("deliveryId" to "d-1", "deeplink" to "appwin://support/conversation/c-1"),
    )
    assertEquals("d-1", notifications.taps.single().deliveryId)
  }

  @Test
  fun `the same tap routed twice in a row opens once`() {
    val support = Recorder()
    AppwinPush.register("support", support)

    AppwinPush.handleTap(context, supportPush)
    AppwinPush.handleTap(context, supportPush)
    assertEquals(1, support.taps.size)

    now += 60_000
    AppwinPush.handleTap(context, supportPush)
    assertEquals(2, support.taps.size)
  }

  @Test
  fun `a launch intent is routed once and loses its Appwin extras`() {
    val support = Recorder()
    AppwinPush.register("support", support)
    val intent = Intent().apply {
      supportPush.forEach { (key, value) -> putExtra(key, value) }
      putExtra("google.message_id", "m-1")
    }

    assertTrue(AppwinPush.handleTap(context, intent))
    assertNull(intent.getStringExtra("deeplink"))
    assertEquals("m-1", intent.getStringExtra("google.message_id"))

    now += 60_000
    assertFalse(AppwinPush.handleTap(context, intent))
    assertEquals(1, support.taps.size)
  }

  @Test
  fun `foreground returns what the product says`() {
    assertFalse(AppwinPush.handleForeground(context, supportPush, "Agent", "Hello"))
    AppwinPush.register("support", Recorder(foreground = true))
    assertTrue(AppwinPush.handleForeground(context, supportPush, "Agent", "Hello"))
  }

  @Test
  fun `a silent push is consumed in the foreground too, and handed to onMessage`() {
    val notifications = Recorder()
    AppwinPush.register("notifications", notifications)
    val silent = mapOf("appwinType" to "inapp.pending")

    assertTrue(AppwinPush.handleForeground(context, silent))
    assertTrue(AppwinPush.handleMessage(context, silent))
    assertEquals(2, notifications.messages.size)
  }

  @Test
  fun `a visible push is not consumed as a message unless the product says so`() {
    AppwinPush.register("support", Recorder(message = false))
    assertFalse(AppwinPush.handleMessage(context, supportPush))
  }

  @Test
  fun `title and body fall back to the data keys`() {
    val payload = AppwinPushPayload.parse(supportPush + mapOf("title" to "T", "body" to "B"))!!
    assertEquals("T", payload.title)
    assertEquals("B", payload.body)
    assertEquals("X", AppwinPushPayload.parse(supportPush, title = "X")!!.title)
  }
}

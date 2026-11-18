# Appwin SDK for Android

Support, community, push notifications, analytics and attribution for Android
apps, rendered natively in Compose.

Requires `minSdk` 24, `compileSdk` 35 and JDK 17. The product calls
(`initialize()`, `AppwinCore.identify`...) are Kotlin `suspend` functions.

Full guide, per-product APIs and dashboard setup:
https://appwin.io/docs/sdk/installation

## Install

The artefacts are on Maven Central. Add the products you use;
`io.appwin:appwin-core` arrives as a transitive dependency, so you never
declare it yourself:

```kotlin
dependencies {
  implementation("io.appwin:appwin-support:1.0.0")
  // add others the same way: appwin-community, appwin-notifications,
  // appwin-analytics, appwin-attribution, appwin-tiktok-events
}
```

All artefacts share one version and are released together: keep them on the
same number.

## Products

| Product | Purpose |
| --- | --- |
| [`io.appwin:appwin-core`](https://appwin.io/docs/products/appwin-core) | Device identity, session, networking, event tracking and consent. Required, and pulled in by the others. |
| [`io.appwin:appwin-support`](https://appwin.io/docs/products/support) | Messenger, FAQ, conversations. |
| [`io.appwin:appwin-community`](https://appwin.io/docs/products/community) | Feed, comments, profiles. |
| [`io.appwin:appwin-notifications`](https://appwin.io/docs/products/notifications) | Push token, push handling, in-app messages. |
| [`io.appwin:appwin-analytics`](https://appwin.io/docs/products/analytics) | Behavioural events, funnels and experiments, crash reporting, session replay. |
| [`io.appwin:appwin-attribution`](https://appwin.io/docs/products/attribution) | Acquisition signals: Play Install Referrer, advertising id and consent. |
| [`io.appwin:appwin-tiktok-events`](https://appwin.io/docs/products/attribution) | Optional TikTok adapter for Attribution. |

Attribution can relay conversions to ad networks through an optional adapter:
add `io.appwin:appwin-tiktok-events` to your app, nothing else to call. It
wraps the TikTok Business SDK, which is published on JitPack rather than
Maven Central, so declare that repository too:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") } // TikTok Business SDK
  }
}
```

See the [Attribution guide](https://appwin.io/docs/products/attribution).

`appwin-core` depends on `play-services-ads-identifier`, which adds the
`com.google.android.gms.permission.AD_ID` permission to your merged manifest:
declare it in the Play Console. The advertising id is only reported after
`AppwinAttribution.setAdvertisingConsent(AdvertisingConsent.GRANTED)`.

## Quickstart

Configure once at launch, in your `Application`:

```kotlin
class MyApp : Application() {
  override fun onCreate() {
    super.onCreate()
    AppwinCore.configure(this, projectAppId = "your-app-id")
  }
}
```

Then present or initialise the products you use:

```kotlin
AppwinSupport.presentMessenger(activity)          // initialises Support on demand

lifecycleScope.launch {
  if (AppwinCommunity.initialize().isReady) {
    // show your Community tab, which hosts AppwinCommunity.CommunityView()
  }
}
```

The App ID comes from your Appwin dashboard. Until `configure` has run, every
product's `initialize()` returns `NOT_CONFIGURED` and shows nothing. Identity lives in Core
(`AppwinCore.identify`, `AppwinCore.updateUser`, `AppwinCore.logout`, all
`suspend`); the products pick up the current user by themselves. Your own
events go through `AppwinCore.track`, and consent through
`AppwinCore.setConsent`. See the [Quickstart](https://appwin.io/docs/sdk/installation).

## Notifications: Firebase

`appwin-notifications` does not bundle Firebase. Add `firebase-messaging` and
the Google Services plugin (with your `google-services.json`) to your app: the
SDK's `AppwinFirebaseMessagingService` is then merged from its manifest and
registers the token by itself.

```kotlin
// from a coroutine, after AppwinCore.configure
if (AppwinNotifications.initialize().isReady) {
  AppwinNotifications.start(context)
}
```

An app that already has its own `FirebaseMessagingService` removes ours from
its manifest and forwards to `AppwinPush.handleMessage`,
`AppwinPush.handleForeground` and `AppwinPush.handleTap` (see the `AppwinPush`
KDoc).

## Analytics: crashes and session replay

`AppwinAnalytics.initialize()` also starts crash reporting. Uncaught exceptions
are written to disk and uploaded on the next launch; on Android 11+ (API 30)
the ANRs and native crashes of the previous runs are read from the system's
`ApplicationExitInfo`, no NDK needed. A handler installed before ours
(Crashlytics, Sentry) keeps receiving every crash. Same consent as the events.

```kotlin
// from a coroutine: initialize() is a suspend function
AppwinAnalytics.initialize()                       // crash reporting on
AppwinAnalytics.initialize(crashReporting = false) // events only

try { sync() } catch (e: IOException) {
  AppwinAnalytics.recordError(e)                   // non-fatal
}
```

Pass `inAppPackages = listOf("com.example.app")` when your code does not live
under your application id: only frames from your code group crashes into
issues.

Session replay starts with it too, once replay is switched on for the app in
the dashboard (off by default); `initialize(sessionReplay = false)` opts the
build out. Text fields are always masked; hide anything else with
`AppwinReplay.mask(view)` or, in Compose, `Modifier.appwinMask()`.

## Support

Bugs and questions: the issues of this repository. Anything tied to your
account, your billing or your data goes through the support widget in your
Appwin dashboard.

## Licence

Proprietary, see [LICENSE](./LICENSE). This source is public for auditability
and for debugging on the studio's side, not for reuse.

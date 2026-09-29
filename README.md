# Appwin SDK for Android

Support, community, push notifications, analytics and attribution for Android
apps, rendered natively in Compose.

Requires API 24 and JDK 17.

Full guide, per-product APIs and dashboard setup:
https://appwin.io/docs/sdk/installation

## Install

Add the products you use; `io.appwin:appwin-core` arrives as a transitive
dependency, so you never declare it yourself:

```kotlin
dependencies {
  implementation("io.appwin:appwin-support:0.9.0")
  // add others the same way: appwin-community, appwin-notifications,
  // appwin-analytics, appwin-attribution
}
```

All artefacts share one version and are released together.

## Products

| Product | Purpose |
| --- | --- |
| [`io.appwin:appwin-core`](https://appwin.io/docs/products/appwin-core) | Device identity, session, networking. Required, and pulled in by the others. |
| [`io.appwin:appwin-support`](https://appwin.io/docs/products/support) | Messenger, FAQ, conversations. |
| [`io.appwin:appwin-community`](https://appwin.io/docs/products/community) | Feed, comments, profiles. |
| [`io.appwin:appwin-notifications`](https://appwin.io/docs/products/notifications) | Push token, events, in-app messages. |
| [`io.appwin:appwin-analytics`](https://appwin.io/docs/products/analytics) | Behavioural events, funnels and experiments. |
| [`io.appwin:appwin-attribution`](https://appwin.io/docs/products/attribution) | Acquisition signals: Play Install Referrer, advertising id and consent. |

Attribution can relay conversions to ad networks through an optional adapter:
add `io.appwin:appwin-tiktok-events` to your app, nothing else to call. See the
[Attribution guide](https://appwin.io/docs/products/attribution).

## Quickstart

Configure once at launch, then present or initialise the products you use:

```kotlin
AppwinCore.configure(context, projectAppId = "your-app-id")
AppwinSupport.presentMessenger(activity)
```

The App ID comes from your Appwin dashboard; without a valid one the SDK stays
inert and makes no network call. Identity lives in Core
(`AppwinCore.identify`, a `suspend` function); the products pick up the current
user by themselves. See the [Quickstart](https://appwin.io/docs/sdk/installation).

## Support

Bugs and questions: the issues of this repository. Anything tied to your
account, your billing or your data goes through the support widget in your
Appwin dashboard.

## Licence

Proprietary, see [LICENSE](./LICENSE). This source is public for auditability
and for debugging on the studio's side, not for reuse.

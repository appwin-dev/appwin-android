# Changelog - Appwin SDK for Android

Versions follow [semantic versioning](https://semver.org).

Each Appwin artefact versions independently: a fix here does not move the iOS,
Android or React Native SDK. All four numbers live in one place, `version.json`
in the monorepo, and the release script derives every manifest and every
cross-artefact pin from it.

The four artefacts (`appwin-core`, `appwin-support`, `appwin-community`,
`appwin-notifications`) are released together and share **this** version.

## 0.3.0

**Breaking.** `registerPushToken` moved from Support to the foundation: it is
now `AppwinCore.registerPushToken(...)`. The token is shared by Support, Community and Notifications, so it
belongs to the socle rather than to one product; it still posts to the Support
route, so registering it needs no Notifications entitlement. A product whose
`initialize()` runs without a registered token logs a warning - recommended for
Support and Community, required for Notifications - rather than refusing to
start.

`initialize()` answered `UNKNOWN` on a first launch of an app that was
online. `configure` returns before the bearer exists - deliberately, so an
offline app starts as fast as any other - and `/sdk/v1/availability` is
bearer-only. Called straight after `configure`, which is what the integration
sequence tells you to do, the request went out without a token, took a 401,
found no cached verdict, and reported no verdict at all. Products stayed
closed until the next launch.

`availability()` now awaits the session first. It is idempotent and shared
between concurrent callers, so the three products initialising at startup still
cost one round trip.

The `UNKNOWN` message no longer says "offline". A 404 from an API older than
the SDK lands in the same place, and telling a developer their online app is
offline sends them looking in the wrong direction; it now names both causes.

## 0.2.0

**Breaking.** Each product now has a suspending `initialize()` that asks the
server whether it may open, and it must be called before presenting that
product. `AppwinCore.configure(context, projectAppId)` is unchanged and still
the first call.

`initialize()` returns an `AppwinInitResult` rather than throwing: not being
entitled is a normal outcome of a normal launch. Gate your own UI on it, since
the SDK does not own your navigation. `presentMessenger`, `MessengerView`,
`presentCommunity` and `CommunityView` refuse rather than opening empty.

The verdict is cached in shared preferences and used as an offline fallback.

## 0.1.0

First release.

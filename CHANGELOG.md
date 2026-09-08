# Changelog - Appwin SDK for Android

Versions follow [semantic versioning](https://semver.org).

Each Appwin artefact versions independently: a fix here does not move the iOS,
Android or React Native SDK. All four numbers live in one place, `version.json`
in the monorepo, and the release script derives every manifest and every
cross-artefact pin from it.

The four artefacts (`appwin-core`, `appwin-support`, `appwin-community`,
`appwin-notifications`) are released together and share **this** version.

## 0.5.0

**New artefact: `io.appwin:appwin-analytics`.** `track`, `screen`, `flush` and
`setConsent`, behind the same `initialize()` gate as the other products.
`configure` alone collects nothing: an app that never initialises Analytics
pays for none of it. Events are validated, batched and persisted to a rotating
JSONL store, so a flush that fails offline is retried at the next launch rather
than dropped, and the store drops its oldest entries rather than growing
without bound. Sessions carry a UUIDv7 identifier, which sorts by time without
a second timestamp field.

Consent is **opt-out** by default, the model PostHog and Firebase use: the
pipeline runs until the host app calls `setConsent(AnalyticsConsent.DENIED)`. A
studio bound to opt-in calls it before `initialize()`.

**Play Install Referrer** (ADR-0038), the counterpart of SKAdNetwork on iOS.
The store's answer is captured once per install and rides the regular pipeline
as the reserved `install_referrer` event, so consent gating, batching and
retries apply unchanged and the backend needs no new route. A definitive answer
- including "no Play Store on this device" - burns the single attempt; a
transient failure leaves it for the next launch. The dependency is optional at
runtime: a broken Play Store binding logs and moves on rather than taking the
SDK down.

**In-app banners.** `appwin-core` hosts a shared banner surface, and Support
raises one when an agent replies while the customer sits on another screen.
That case was silent until now: the server skips the push when the customer
holds a live realtime connection, assuming something in-app takes over, and
nothing did. Tapping the banner opens the thread through the new
`AppwinSupport.presentConversation(context, conversationId)`.

A silent push now acts as a doorbell, so an in-app message reaches a session
already in progress instead of waiting for the next launch.

**Fewer sockets.** Notifications no longer holds a realtime connection: it
fetches on open and refetches after a delay. Support opens its socket lazily,
only once a conversation exists. Both were paid for on every launch by every
app, for an event most sessions never see.

The messenger thread updates in real time and groups consecutive messages from
the same author into one bubble stack. The Community feed and its post cards
follow the Figma designs: spacing, elevation and palette.

## 0.4.0

Le messenger s'ouvre en **bottom sheet** par-dessus l'app hôte, et non plus
dans une activité plein écran opaque. Ouvrir une conversation reste dans le
panneau : l'écran n'est plus remplacé, ce qui se lisait comme quitter l'app.

L'UI est reprise sur les maquettes (accueil et fil) : bannière, appel à
l'action en dégradé, bulles avec leur coin de queue et leur ombre. Les six
bannières prédéfinies sont rendues nativement, depuis les mêmes images que le
SDK iOS.

Les paramètres du studio arrivent enfin jusqu'à l'écran. `autoGradient`,
`bannerSource`, `presetBannerId` et `bannerFocusY` étaient lus sur le réseau
puis jetés à la conversion : changer la bannière ou couper le dégradé dans le
dashboard ne se voyait pas sur Android.

**Correctif.** Le retour système fermait l'activité depuis le fil au lieu de
revenir à l'accueil : il n'y avait aucun `BackHandler` dans le module.

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

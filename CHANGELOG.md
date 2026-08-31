# Changelog - Appwin SDK for Android

Versions follow [semantic versioning](https://semver.org).

Each Appwin artefact versions independently: a fix here does not move the iOS,
Android or React Native SDK. All four numbers live in one place, `version.json`
in the monorepo, and the release script derives every manifest and every
cross-artefact pin from it.

The four artefacts (`appwin-core`, `appwin-support`, `appwin-community`,
`appwin-notifications`) are released together and share **this** version.

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

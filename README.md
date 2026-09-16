# Appwin SDK for Android

Support messenger, community feed, push notifications and in-app messages for
Android apps, rendered natively in Compose.

Requires API 24 and JDK 17.

## Install

```kotlin
dependencies {
  implementation("io.appwin:appwin-support:0.1.0")
  implementation("io.appwin:appwin-community:0.1.0")
  implementation("io.appwin:appwin-notifications:0.1.0")
}
```

`io.appwin:appwin-core` arrives as a transitive dependency; you never declare
it yourself. The four artefacts share one version and are released together.

## Use

Configure once, at launch, whatever the number of products:

```kotlin
AppwinCore.configure(context, appId = "your-app-id")
AppwinSupport.presentMessenger(activity)
```

The App ID comes from your Appwin dashboard. Without a valid one the SDK stays
inert: it makes no network call.

## Build from source

```bash
./gradlew testDebugUnitTest
./gradlew assembleRelease
./gradlew publishToMavenLocal
```

Needs an Android SDK declared in `local.properties` (not versioned) and a JDK
17 to 21. Each module is a direct subfolder named after its Maven artefact.

## Support

Bugs and questions: the issues of this repository. Anything tied to your
account, your billing or your data goes through the support widget in your
Appwin dashboard.

## Licence

Proprietary, see [LICENSE](./LICENSE). This source is public for auditability
and for debugging on the studio's side, not for reuse.

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.MavenPublishBaseExtension

// Build root: it only declares the plugins, and each module applies them.
// `apply false` avoids applying an Android plugin at the root, which is not an
// Android module.
plugins {
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.maven.publish) apply false
}

/*
 * Maven Central publication, configured once for all four modules.
 *
 * The coordinates derive from the module name (`:appwin-core` becomes
 * `io.appwin:appwin-core`), so adding a module is enough to make it publishable
 * - no publication block to copy, and therefore no risk of one module shipping
 * with another's version.
 *
 * The Vanniktech plugin rather than raw `maven-publish`: Central refuses a
 * bundle that is missing a sources jar, a javadoc jar, a GPG signature or any
 * of the required POM fields, and it does so at upload time with a message
 * naming one field. The plugin assembles and signs the whole bundle, and knows
 * the Central Portal API that replaced OSSRH.
 */
/**
 * Maven description of each module, one per artefact rather than a template.
 *
 * This is the text Central shows under an artefact, and it is FROZEN once the
 * version is released. A shared template had `appwin-core` advertising a
 * messenger, a community feed and push notifications - the three things it does
 * NOT contain, being identity, session and the HTTP client the others consume.
 *
 * A module missing from this map fails the build. Central would refuse the
 * bundle anyway, but at upload time and with a message naming one field.
 */
val MODULE_DESCRIPTIONS =
  mapOf(
    "appwin-core" to
      "Appwin SDK for Android - core. Device identity, session and the HTTP " +
      "client shared by every Appwin product.",
    "appwin-support" to
      "Appwin SDK for Android - support. In-app messenger, conversations and " +
      "FAQ, rendered natively in Compose.",
    "appwin-community" to
      "Appwin SDK for Android - community. In-app feed, comments and member " +
      "profiles, rendered natively in Compose.",
    "appwin-notifications" to
      "Appwin SDK for Android - notifications. Push token registration and " +
      "in-app messages.",
  )

subprojects {
  // `plugins.withId` rather than a direct `apply`: the block only runs when the
  // subproject applies the Android plugin, so it never touches a module that is
  // not an Android library.
  plugins.withId("com.android.library") {
    apply(plugin = "com.vanniktech.maven.publish")

    extensions.configure<MavenPublishBaseExtension> {
      // Sources and javadoc jars come from here, not from an
      // `android { publishing { … } }` block in each module: declaring the
      // variant twice makes AGP fail on an already-registered publication.
      configure(AndroidSingleVariantLibrary("release", sourcesJar = true, publishJavadocJar = true))

      coordinates(
        "io.appwin",
        project.name,
        rootProject.property("appwinSdkVersion") as String,
      )

      publishToMavenCentral()
      // Signature only when a key is available, so `publishToMavenLocal` keeps
      // working on a machine with no GPG setup - which is every machine that
      // only runs `pnpm sdk:bootstrap`.
      if (project.findProperty("signingInMemoryKey") != null) {
        signAllPublications()
      }

      /*
       * Central validates the POM before accepting a bundle, and refuses one
       * without `description`, `developers` or `scm`. Those three are not
       * documentation: a missing one fails the publish with a message that
       * names the field and nothing else.
       */
      pom {
        name.set(project.name)
        // Une description par artefact, jamais un gabarit : cf. MODULE_DESCRIPTIONS.
        description.set(
          MODULE_DESCRIPTIONS[project.name]
            ?: error("Description Maven manquante pour ${project.name} (cf. MODULE_DESCRIPTIONS)")
        )
        url.set("https://appwin.io")
        licenses {
          license {
            name.set("Proprietary")
            url.set("https://github.com/appwin-dev/appwin-android/blob/main/LICENSE")
            distribution.set("repo")
          }
        }
        developers {
          developer {
            id.set("appwin")
            name.set("Appwin Studio")
            email.set("lesignobles.studio@gmail.com")
            url.set("https://appwin.io")
          }
        }
        scm {
          url.set("https://github.com/appwin-dev/appwin-android")
          connection.set("scm:git:https://github.com/appwin-dev/appwin-android.git")
          developerConnection.set("scm:git:ssh://git@github.com/appwin-dev/appwin-android.git")
        }
      }
    }
  }
}

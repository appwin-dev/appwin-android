pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "appwin-sdk-android"

/*
 * Gradle build root for the four Android modules.
 *
 * Each module is a direct subfolder named after its Maven artifact, so no
 * `projectDir` override is needed: Gradle derives the path from the module
 * name. That symmetry is new - the tree used to be grouped by product because
 * SPM derives a package's identity from its path, which forced the iOS folders
 * to carry their package name. The four Swift packages are now one package with
 * four products, so that constraint is gone and `sdk/` is grouped by
 * technology, one folder per published repository (cf. ADR-0036).
 */
include(":appwin-core")
include(":appwin-support")
include(":appwin-community")
include(":appwin-notifications")

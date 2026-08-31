plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "io.appwin.core"
  compileSdk = 35

  defaultConfig {
    // API 24 is the floor for `EncryptedSharedPreferences` and covers the real
    // device base. Going lower would force an unencrypted fallback store for a
    // negligible share of devices.
    minSdk = 24
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
}

/*
 * The SDK is a public library: every exposed API must carry an explicit
 * visibility modifier and return type.
 *
 * The `explicitApi()` DSL rather than the raw compiler option: it applies the
 * rule to production sources only. Setting the option by hand propagates it to
 * the tests, where it makes no sense - nothing exposes an API from
 * un test.
 */
kotlin {
  explicitApi()
}

dependencies {
  api(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)
  implementation(libs.androidx.security.crypto)

  testImplementation(libs.junit)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
}

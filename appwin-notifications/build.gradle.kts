plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "io.appwin.notifications"
  compileSdk = 35

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
}

kotlin {
  explicitApi()
}

dependencies {
  api(project(":appwin-core"))

  val composeBom = platform(libs.compose.bom)
  implementation(composeBom)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)
  implementation(libs.compose.foundation)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.core.ktx)
  implementation(libs.coil.compose)

  compileOnly(platform(libs.firebase.bom))
  compileOnly(libs.firebase.messaging)

  testImplementation(libs.junit)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
}

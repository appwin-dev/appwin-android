plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  // Tests only: the masking test renders real composables. Main code has no
  // @Composable function, so the published classes are unchanged.
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "io.appwin.analytics"
  compileSdk = 35

  defaultConfig {
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

kotlin {
  explicitApi()
}

dependencies {
  api(project(":appwin-core"))

  // Session replay masks Compose content through its semantics tree and
  // offers `Modifier.appwinMask()`. compileOnly: an app without Compose ships
  // none of it, and the recorder only touches these classes once it has seen
  // them on the classpath.
  compileOnly(platform(libs.compose.bom))
  compileOnly(libs.compose.ui)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(platform(libs.compose.bom))
  testImplementation(libs.compose.foundation)
  testImplementation(libs.compose.material3)
  testImplementation(libs.androidx.activity.compose)
  testImplementation(libs.compose.ui.test.manifest)
}

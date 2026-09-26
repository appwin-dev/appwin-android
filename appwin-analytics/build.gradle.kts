plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "io.appwin.analytics"
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

  testOptions {
    unitTests {
      isReturnDefaultValues = true
    }
  }
}

kotlin {
  explicitApi()
}

dependencies {
  api(project(":appwin-core"))

  testImplementation(libs.junit)
}

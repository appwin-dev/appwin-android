plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "io.appwin.tiktok"
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
      isReturnDefaultValues = true
    }
  }
}

kotlin {
  explicitApi()
}

dependencies {
  api(project(":appwin-core"))
  // The whole point of this module (ADR-0038 exception, 2026-09-05):
  // the TikTok App Events SDK stays OUT of appwin-core, only studios
  // advertising on TikTok pull it into their build.
  implementation(libs.tiktok.business)

  testImplementation(libs.junit)
}

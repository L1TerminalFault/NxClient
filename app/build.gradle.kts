plugins {
  // alias(libs.plugins.android.application)
  // id(com.android.application)
  id("com.google.devtools.ksp") // version "1.9.10-1.0.13"
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
  // alias(libs.plugins.kotlin.android)
  // alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "rx.xdk.nx"
  compileSdk = 36

  defaultConfig {
    applicationId = "rx.xdk.nx"
    minSdk = 26
    targetSdk = 36
    versionCode = 5
    versionName = "3.2.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  buildFeatures {
    compose = true
  }
  packaging {
    resources {
        excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
  }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
  val roomVersion = "2.8.4"
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-extended:1.7.7")
  // implementation("androidx.compose.material:material-icons-extended:1.3.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.work:work-runtime-ktx:2.9.1")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation("androidx.room:room-ktx:$roomVersion")
  implementation("androidx.room:room-runtime:$roomVersion")
  ksp("androidx.room:room-compiler:$roomVersion")

  // implementation("io.coil-kt:coil-compose:2.4.0")
  // camera
  implementation("io.github.g00fy2.quickie:quickie-bundled:1.10.0")
  // barcode scanning
  implementation("com.google.mlkit:barcode-scanning:17.3.0")

  // Only if you're using the Clerk API without the Clerk UI components
  implementation("com.clerk:clerk-android-api:1.0.1")

  implementation("com.clerk:clerk-android-ui:1.0.1")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

  // Coil for Compose (Latest version as of March 2026)
  implementation("io.coil-kt.coil3:coil-compose:3.0.0")
  
  // Optional: If you need GIF or SVG support
  implementation("io.coil-kt.coil3:coil-gif:3.0.0")
  implementation("io.coil-kt.coil3:coil-svg:3.0.0")

  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}

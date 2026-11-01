plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("io.github.takahirom.roborazzi")
}

android {
  namespace = "com.cstone.nightfall"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.cstone.nightfall"
    minSdk = 26
    // Stay on 36: API 37 mutes unattended background starts. See README.
    targetSdk = 36
    versionCode = 1
    versionName = "0.1"
  }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  val bom = platform("androidx.compose:compose-bom:2024.09.00")
  implementation(bom)
  implementation("androidx.activity:activity-compose:1.10.1")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

  debugImplementation(bom)
  debugImplementation("androidx.compose.ui:ui-test-manifest")

  testImplementation(bom)
  testImplementation("androidx.compose.ui:ui-test-junit4")
  testImplementation("androidx.test.ext:junit:1.3.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.16.1")
  testImplementation("io.github.takahirom.roborazzi:roborazzi:1.59.0")
  testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.59.0")
  testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.59.0")
}

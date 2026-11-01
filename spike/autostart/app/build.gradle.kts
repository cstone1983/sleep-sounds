plugins {
  id("com.android.application")
}

// Build with -PspikeTarget=37 to install a second copy that targets API 37.
val spikeTarget = (project.findProperty("spikeTarget") as String? ?: "36").toInt()

android {
  namespace = "com.cstone.sleepsounds.spike"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.cstone.sleepsounds.spike"
    if (spikeTarget != 36) applicationIdSuffix = ".t$spikeTarget"
    minSdk = 26
    targetSdk = spikeTarget
    versionCode = 1
    versionName = "spike-t$spikeTarget"
    manifestPlaceholders["appLabel"] = if (spikeTarget == 36) "Nightfall" else "Nightfall t$spikeTarget"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

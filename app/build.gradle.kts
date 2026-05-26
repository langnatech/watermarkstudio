import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

fun loadKeystoreProperties(): Properties {
  val properties = Properties()
  val keystorePropertiesFile = rootProject.file("keystore.properties")
  if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { properties.load(it) }
  }
  return properties
}

fun Properties.signingValue(propertyKey: String, envKey: String): String? =
  getProperty(propertyKey)?.takeIf { it.isNotBlank() } ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

fun validateReleaseSigning(signingConfig: com.android.build.api.dsl.ApkSigningConfig) {
  val errors = mutableListOf<String>()
  val storeFile = signingConfig.storeFile
  if (storeFile == null || !storeFile.exists()) {
    errors += "Keystore 文件不存在: ${storeFile?.absolutePath ?: "未配置 storeFile"}"
  }
  if (signingConfig.storePassword.isNullOrBlank()) {
    errors += "storePassword 未配置，请在 keystore.properties 或环境变量 STORE_PASSWORD 中设置"
  }
  if (signingConfig.keyAlias.isNullOrBlank()) {
    errors += "keyAlias 未配置，请在 keystore.properties 或环境变量 KEY_ALIAS 中设置"
  }
  if (signingConfig.keyPassword.isNullOrBlank()) {
    errors += "keyPassword 未配置，请在 keystore.properties 或环境变量 KEY_PASSWORD 中设置"
  }
  if (errors.isNotEmpty()) {
    throw GradleException(
      """
      Release 签名配置不完整:
      ${errors.joinToString("\n")}

      请复制 keystore.properties.example 为 keystore.properties 并填入密码，
      或设置环境变量: STORE_PASSWORD、KEY_PASSWORD（可选 KEY_ALIAS、KEYSTORE_PATH）。
      """.trimIndent(),
    )
  }
}

android {
  namespace = "com.watermarkstudio"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.watermarkstudio"
    minSdk = 24
    targetSdk = 36
    versionCode = 18
    versionName = "1.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
    }

    externalNativeBuild {
      cmake {
        cppFlags += "-std=c++17"
        arguments += listOf("-DANDROID_STL=c++_shared")
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  signingConfigs {
    create("release") {
      val keystoreProperties = loadKeystoreProperties()
      val keystorePath =
        keystoreProperties.signingValue("storeFile", "KEYSTORE_PATH")
          ?: "${rootDir}/my-upload-key.jks"
      storeFile = rootProject.file(keystorePath)
      storePassword = keystoreProperties.signingValue("storePassword", "STORE_PASSWORD")
      keyAlias = keystoreProperties.signingValue("keyAlias", "KEY_ALIAS") ?: "upload"
      keyPassword = keystoreProperties.signingValue("keyPassword", "KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Uses default auto-generated debug keystore
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging {
    jniLibs {
      pickFirsts += "**/libc++_shared.so"
    }
  }
}

gradle.taskGraph.whenReady {
  val needsReleaseSigning =
    allTasks.any { task ->
      task.project.path == ":app" &&
        task.name.contains("Release", ignoreCase = true) &&
        (
          task.name.contains("Bundle", ignoreCase = true) ||
            task.name.contains("Assemble", ignoreCase = true) ||
            task.name.contains("Sign", ignoreCase = true) ||
            task.name.contains("Package", ignoreCase = true)
          )
    }
  if (needsReleaseSigning) {
    validateReleaseSigning(android.signingConfigs.getByName("release"))
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.emoji2.bundled)
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.androidx.media3.transformer)
  implementation(libs.androidx.media3.effect)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.play.services.ads)
  implementation(libs.user.messaging.platform)
  implementation(libs.android.billing.ktx)
  implementation(libs.androidx.fragment)
  implementation(libs.opencv)
  implementation(libs.ffmpeg.kit.lib)
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}

import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.karen.assistant"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.karen.assistant"
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake {
            targets += "karen_voice"
            arguments += listOf("-DANDROID_STL=c++_shared", "-DCMAKE_BUILD_TYPE=Release", "-DGGML_NATIVE=OFF", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            cppFlags += "-O3"
        } }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    androidResources { noCompress += "gguf" }
}

val validateBundledModels by tasks.registering {
    val modelDir = file("src/main/assets/models")
    inputs.files(fileTree(modelDir) { include("*.gguf") })
    inputs.files(fileTree("src/main/assets/voices") { include("*.wav") })
    doLast {
        check(file("$modelDir/qwen-tokenizer-12hz-Q4_K_M.gguf").length() == 254974752L &&
              file("$modelDir/qwen-talker-0.6b-base-Q4_K_M.gguf").length() == 628905056L) {
            "Bundled voice models missing. Run tools/prepare-models.ps1 before building."
        }
        mapOf(
            "spider.wav" to "a8419f8202d8340f03aec8d31f55aca179dd2b494bb772262e25c4f6697f128a",
            "iron.wav" to "cb21465d19827fe231a046fee0f24567a3aea8f86d6085ff4862cc52cf8866db"
        ).forEach { (name, expected) ->
            val bytes = file("src/main/assets/voices/$name").readBytes()
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(actual == expected) { "Bundled voice reference missing or changed: $name" }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(validateBundledModels) }

kotlin { jvmToolchain(17) }

dependencies {
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
}

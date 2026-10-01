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
        versionCode = 5
        versionName = "0.5.0"
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
}

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

plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.2.21"
    id("com.chaquo.python") version "17.0.0"
}
android {
    namespace = "org.pistonport.nativeandroid"
    compileSdk = 36
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        applicationId = "org.pistonport.nativeandroid.hub"
        minSdk = 33
        // The original PS4 files are accessed by path using the legacy read permission.
        targetSdk = 28
        versionCode = 7
        versionName = "0.6.1"
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets["main"].java.srcDir(layout.buildDirectory.dir("sdl-java"))
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("native-libs"))
    packaging { jniLibs { useLegacyPackaging = true } }
}

kotlin { jvmToolchain(17) }

chaquopy {
    defaultConfig {
        version = "3.13"
    }
}

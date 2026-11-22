plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.2.21"
}
android {
    namespace = "org.pistonport.nativeandroid"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.pistonport.nativeandroid"
        minSdk = 33
        // The original PS4 files are accessed by path using the legacy read permission.
        targetSdk = 28
        versionCode = 1
        versionName = "0.1-native"
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets["main"].java.srcDir(layout.buildDirectory.dir("sdl-java"))
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("native-libs"))
    packaging { jniLibs { useLegacyPackaging = false } }
}

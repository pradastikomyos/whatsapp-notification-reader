apply(plugin = "com.android.application")
apply(plugin = "org.jetbrains.kotlin.android")

extensions.configure<com.android.build.gradle.AppExtension>("android") {
    namespace = "com.ridenotify.spike.audiobackground"
    compileSdkVersion(35)

    defaultConfig {
        applicationId = "com.ridenotify.spike.audiobackground"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "spike"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension>("kotlin") {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Intentionally zero external dependencies: this disposable spike only
// needs the Android SDK itself, which keeps offline/local-cache builds
// reliable and matches the "dependency-minimal" requirement for P0-T00.

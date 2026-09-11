// Disposable spike root build file (P0-T00 / P0-T05).
// This project is exempt from the production implementation freeze and must
// never be referenced by the future production `app` module.
//
// Uses the legacy buildscript/classpath form (instead of the `plugins {}`
// DSL) because this machine's Gradle plugin-portal marker artifacts are not
// cached for offline resolution, while the underlying AGP/Kotlin jars are.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

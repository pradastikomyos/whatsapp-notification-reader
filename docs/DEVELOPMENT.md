# Development

## Requirements

- JDK 17 or newer. CI uses Temurin 17; local development currently uses JDK 21.
- Android SDK Platform 36 and Build Tools 36.0.0.
- Android SDK path in the untracked `local.properties` file.

Install the required platform on this Windows machine:

```powershell
& "$env:LOCALAPPDATA\Android\sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-36" "build-tools;36.0.0"
```

## Verification Commands

Run from the project root.

```powershell
# Compile and package a debug APK.
.\gradlew.bat assembleDebug

# Run local JVM tests.
.\gradlew.bat testDebugUnitTest

# Run Android lint.
.\gradlew.bat lintDebug

# Reproduce the complete CI verification locally.
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Kotlin and XML formatting follows `.editorconfig`. Until a formatter is added
by an approved dependency change, use the IDE's `Reformat Code` action before
submitting changes; CI lint remains the command-line style and correctness gate.

## Device Smoke Test

```powershell
adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
adb shell am start -W -n com.ridenotify.app.wa_reader/.MainActivity
adb shell dumpsys package com.ridenotify.app.wa_reader
```

The package dump must show `targetSdk=36` and the service component
`com.ridenotify.app.wa_reader/.MyNotificationListener` protected by
`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`.

Do not grant notification access through ADB for production onboarding. The
user grants that special access through Android settings.

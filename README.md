# android_kotlin_browser

Floating browser

## Build APK

Prerequisites:

- Android Studio or the Android SDK command-line tools
- JDK 11
- Android SDK Platform 35 installed

From the project root, build a debug APK:

```bash
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

To install the debug APK on a connected device or emulator:

```bash
./gradlew installDebug
```

To build a release APK:

```bash
./gradlew assembleRelease
```

The unsigned release APK is generated at:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

For distribution, configure release signing in `app/build.gradle.kts` or sign the generated release APK with your own keystore.

App screenshots
![image](https://github.com/user-attachments/assets/73de7198-3027-4246-a41f-0eec319b81eb)
![image](https://github.com/user-attachments/assets/03c744bb-2b18-4be5-af6d-9811c5cae15a)

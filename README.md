# Eclipse

Eclipse is an Android app for keeping Android's Light/Dark appearance schedule in control when Battery Saver is enabled. It can also configure Android's built-in custom dark-mode schedule. The app uses [Shizuku](https://shizuku.rikka.app/) to access the protected system settings required for these features.

## Features

- **Battery Saver protection** — prevents Battery Saver from overriding the device's Light/Dark appearance by setting `enable_night_mode=false` in Android's `battery_saver_constants` global setting.
- **Native Android schedule** — follows sunset to sunrise using Android's automatic mode or configures custom dark-mode and light-mode start times, including schedules that cross midnight.
- **Live status** — shows Shizuku access, Battery Saver state, current appearance, protection status, and Android's schedule.
- **Apply / verify** — checks enabled features and reapplies their settings when needed.
- **Safe restoration** — saves the previous values and verifies system changes. On restore, settings changed by another app or Android are left untouched. If Battery Saver configuration has changed externally, you can choose to restore only Eclipse's `enable_night_mode` entry while preserving other entries.
- **Diagnostics** — displays Shizuku connection and relevant system-setting details to help troubleshoot access or configuration problems.

## Requirements

- An Android device running **Android 8.0 (API 26) or later**.
- The **Shizuku** app installed and running on the device.
- Eclipse authorized in Shizuku before using features that modify system settings.
- For development: Android Studio or a compatible Android SDK setup, and a JDK supported by the configured Gradle/Android Gradle Plugin versions. The project uses the Gradle Wrapper, so a separate Gradle installation is not required.

Eclipse's minimum supported Android version is API 26. Android 11 (API 30) or later is needed for Android to expose custom schedule times and the original schedule state used by safe schedule restoration.

## Getting started

1. Install and start Shizuku using one of the startup methods supported by Shizuku on your device. Shizuku may need to be started again after a reboot, depending on the startup method.
2. Open Eclipse and go to **Settings**.
3. Under **Shizuku access**, tap **Grant access** and approve the request in Shizuku.
4. Return to **Status** or **Schedule** and enable the feature you want.

Shizuku must remain connected and Eclipse must retain Shizuku permission for Eclipse to read, apply, or restore protected settings. Eclipse does not start Shizuku for you.

## Using Eclipse

### Battery Saver protection

On **Status**, turn on **Battery Saver protection**. Eclipse saves the original Battery Saver configuration and changes only its `enable_night_mode` entry, preserving other configuration entries. Android's own appearance schedule can then control Light/Dark mode while Battery Saver is active.

When Shizuku starts, Eclipse checks and reapplies this protection once if **Battery Saver protection** is enabled and Eclipse has Shizuku permission. If **Use Android's schedule** is also enabled, Eclipse reasserts the saved sunset-to-sunrise or custom schedule. Android may defer the visible appearance change until the screen turns off. Eclipse also checks the Battery Saver setting while the app is open and can reapply the protection if the managed entry is no longer disabled. It refreshes when the system appearance changes while Eclipse is open. The app does not replace Android's schedule or continuously run a separate schedule service.

### Android's native schedule

On **Schedule**, choose **Sunset to sunrise** to let Android follow its built-in twilight schedule, or choose **Custom times** and set when dark mode starts and light mode returns. Turn on **Use Android's schedule** to apply the selection. Eclipse relies on Android to determine sunset and sunrise; it does not calculate them itself. Changes are saved for the next activation when the schedule is off, and applied immediately when it is on.

The two start times must be different. Android may defer a scheduled appearance change while the screen is in use, applying it after the screen turns off or a screensaver starts.

### Apply and restore

- **Apply / verify now**, in **Settings**, checks and reapplies enabled Eclipse features.
- **Restore safely** restores saved settings only when they still match the values Eclipse applied. If a setting has changed externally, Eclipse leaves it untouched.
- If Battery Saver configuration conflicts with Eclipse's saved value, use **Restore only Eclipse's key** to restore the original `enable_night_mode` entry without replacing other current entries.

Restoring a schedule requires the original schedule state to have been readable and the current schedule to still match Eclipse's applied values. If Android does not expose enough state or the schedule changed externally, Eclipse leaves the current schedule unchanged.

## Build

From the project root, build a debug APK:

```bash
./gradlew :app:assembleDebug
```

On Windows, use the included batch wrapper:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Signed release APKs in GitHub Actions

The release workflow signs its APK using a private release keystore stored as GitHub Actions secrets. Create a keystore and keep the `.jks` file and its passwords private:

```powershell
keytool -genkeypair -v -keystore eclipse-release.jks -alias eclipse -keyalg RSA -keysize 2048 -validity 10000
```

In the repository's **Settings → Secrets and variables → Actions**, add these repository secrets:

- `ANDROID_KEYSTORE_BASE64` — base64-encoded contents of `eclipse-release.jks`. In PowerShell, copy the encoded value with:
  ```powershell
  Set-Clipboard -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes(".\eclipse-release.jks")))
  ```
- `ANDROID_KEYSTORE_PASSWORD` — the keystore password.
- `ANDROID_KEY_ALIAS` — `eclipse` (or the alias chosen when creating the keystore).
- `ANDROID_KEY_PASSWORD` — the key password.

Do not commit the keystore or put its passwords in the repository. Once these secrets are set, pushes to the repository's default branch and manual workflow runs publish the signed APK as a GitHub Release asset. Keep a secure backup of the keystore: updates to an installed app must be signed with the same key.

To install the debug build on a connected device or emulator:

```bash
./gradlew :app:installDebug
```

Use `.\gradlew.bat :app:installDebug` on Windows.

## Tests

Run the JVM unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

On Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

The unit tests cover Battery Saver configuration parsing and safe restoration, schedule time parsing and validation, and shell-result handling. Instrumentation tests can be run on a connected Android device or emulator with `./gradlew :app:connectedDebugAndroidTest` (Windows: `.\gradlew.bat :app:connectedDebugAndroidTest`).

## Project structure

```text
.
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/eu/tlipman/eclipse/
│       │   │   ├── shizuku/   # Shizuku shell access
│       │   │   ├── system/    # System settings, schedule, and saved state
│       │   │   └── ui/        # Compose theme
│       │   └── res/           # Android resources
│       ├── test/              # JVM unit tests
│       └── androidTest/       # Android instrumentation tests
├── gradle/                    # Version catalog and Gradle Wrapper files
├── build.gradle.kts
└── settings.gradle.kts
```

## Technical details

- **Language and UI:** Kotlin, Jetpack Compose, Material 3
- **Android application ID:** `eu.tlipman.eclipse`
- **Minimum SDK:** 26
- **Target SDK:** 37
- **System access:** Shizuku API and Android shell commands
- **Local state:** app-private preferences for feature toggles, schedule type and times, and restoration snapshots

Dependency versions are maintained in `gradle/libs.versions.toml`.

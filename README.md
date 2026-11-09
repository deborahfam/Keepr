# Keepr

Save, organize, and revisit marketplace listings, property rentals, and shared social posts in one clean place.

## Android app

A native Java Android starter using platform UI components, with no third-party runtime dependencies. Supports Android 8.0 (API 26) and later.

- Save a web link with a title, category, and notes.
- Search saved titles, links, and notes; filter by category.
- Receive links from other apps through Android's Share menu.
- Reopen original listings in a browser and delete saved items.
- Keep your collection on the device across app launches.

No account, backend, link scraping, or cloud sync is configured. Uninstalling the app removes its data. Links are stored in app-private SharedPreferences; automatic Android backup is disabled.

## Run

1. Install Android Studio, JDK 17, and Android SDK Platform 36.
2. Open this repository in Android Studio and let Gradle sync.
3. Select the `app` configuration and run on an emulator or Android device.

For command-line builds, set `ANDROID_HOME` to your Android SDK directory (or set `sdk.dir` in an untracked `local.properties` file):

```sh
./gradlew assembleDebug
./gradlew lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Structure

- `app/src/main/java/com/keepr/app/MainActivity.java` — collection screen, filters, save dialog, and share handling.
- `app/src/main/java/com/keepr/app/SavedLink.java` — model, JSON encoding, and web URL validation.
- `app/src/main/java/com/keepr/app/LinkStore.java` — device-local persistence.
- `app/src/main/AndroidManifest.xml` — launcher and Android share target.
- `.github/workflows/android.yml` — debug build and Android lint on pushes and pull requests.

Build tools: Android Gradle Plugin 8.11.0, Gradle 8.14.3, Java 17, compile/target SDK 36. Release signing and Play Store publishing are not configured.

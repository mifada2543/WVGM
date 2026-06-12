# Build the Android APK

<div align="center">
  <img width="1200" height="475" alt="Banner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

## Prerequisites
- Android Studio (latest)
- JDK 21 installed and `JAVA_HOME` set
- Internet connection for Gradle and SDK downloads
- A valid Gemini API key (optional for app functionality)

## Setup steps
1. **Clone the repository** (or ensure you have the project files locally).
2. **Create an environment file**:
   ```bash
   echo "GEMINI_API_KEY=YOUR_GEMINI_API_KEY" > .env
   ```
   Keep `.env` listed in `.gitignore` to avoid committing the key.
3. **Install the Android SDK command‑line tools** (executed automatically on first build). If you prefer manual setup:
   ```bash
   mkdir -p android-sdk && cd android-sdk
   wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
   unzip -q commandlinetools-linux-11076708_latest.zip -d cmdline-tools
   mkdir -p cmdline-tools/latest
   mv cmdline-tools/* cmdline-tools/latest/
   cd ..
   ```
4. **Generate the Gradle wrapper** (already present, but ensure it matches the required Gradle version):
   ```bash
   ./gradlew wrapper --gradle-version 9.3.1
   ```
5. **Accept Android SDK licenses** (first build will prompt, or run):
   ```bash
   ./gradlew --no-daemon licenseReport
   ```

## Build the debug APK
Run the following command:
```bash
./gradlew assembleDebug
```
The build will download any required SDK platforms/build‑tools and produce the APK at:
```
app/build/outputs/apk/debug/app-debug.apk
```
You can install it on a connected device or emulator with:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## (Optional) Build a release APK
1. Create a signing keystore (`my-upload-key.jks`) and set environment variables:
   ```bash
   export STORE_PASSWORD=your_store_password
   export KEY_PASSWORD=your_key_password
   ```
2. Uncomment the signing config in `app/build.gradle.kts` or add a new one.
3. Build the release bundle:
   ```bash
   ./gradlew assembleRelease
   ```
The signed APK will be located at:
```
app/build/outputs/apk/release/app-release.apk
```

## Notes
- The `.gitignore` already excludes generated files (`.gradle/`, `android-sdk/`, `.env`, `.env.example`).
- Keep your `GEMINI_API_KEY` secret; share only `.env.example` for collaborators.
- If you encounter missing JDK errors, ensure `JAVA_HOME` points to a JDK (not just a JRE).
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/ae472813-a5be-4f5d-b8b8-6e0f1d7b5008

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
## Version Control

- The `.gitignore` file excludes generated build artifacts and local SDK files:
```
.gradle/
android-sdk/
.env
.env.example
```

- Keep your `GEMINI_API_KEY` in `.env` (ignored) and share the example file `.env.example`.

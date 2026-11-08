# AshDock v0.1 – compilation status (8 November 2026)

**Outcome: Debug APK built successfully.** `gradle :app:assembleDebug` completed with no source changes
(Gradle 8.14.3, AGP 8.9.2, Kotlin 2.1.10, Android SDK platform 35, build-tools 35.0.0, JDK 21).

- Output: `app/build/outputs/apk/debug/app-debug.apk` (about 845 KB), package `dev.ashdock.launcher`,
  version 0.1.0 (1), minSdk 26, targetSdk 35.
- Signed with the Android debug key (`apksigner verify` passes). Fine for sideloading, not for the Play Store.
- Compiler warnings only: deprecated `onBackPressed`, `statusBarColor`, `navigationBarColor`, `FLAG_FULLSCREEN`
  in `MainActivity.kt`.

**Not tested:** `tests/run.sh` (needs `kotlinc`, not installed in the build environment), launching on a
device, PRoot/Arch install, and Termux integration.

**Rebuilding**

- GitHub: the repository-root workflow `.github/workflows/ashdock.yml` builds on any push touching `AshDock/`
  (or via **Actions → Build AshDock APK → Run workflow**) and uploads the **AshDock-debug-APK** artifact.
- Locally: Android SDK 35 + JDK 17 or newer, then `./build-apk.sh` (or `gradle :app:assembleDebug`).

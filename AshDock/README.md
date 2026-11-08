# AshDock · native Android home launcher

**Status:** source prototype, not a tested/built APK. Kotlin + Android SDK, no Flutter, rootless Linux optional.

A light Omarchy-inspired Android launcher: dark grid wallpaper, copper/orange default palette, large clock, tiny dock, installed app search, app icon grid, pin/hide, Linux shortcuts, command console and extensive settings. AshDock is an original interface design, not an official Omarchy port or a Hyprland compositor.

## Build APK

Requirements: Android Studio with Android SDK 35, JDK 17, and internet access for Google's Android Gradle plugin (8.9.2), Kotlin plugin (2.1.10), and Gradle 8.11.1.

1. Open this folder (`AshDock/`) as an Android Studio project.
2. Let Android Studio sync the Gradle project.
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)** or run `gradle :app:assembleDebug` with Gradle 8.11.1 installed.
4. Find the debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
5. Sideload to an ARM64 phone (APK itself is architecture-neutral); tap AshDock, then use **Settings → Set as default home**.

The included `.github/workflows/android.yml` builds a debug APK and uploads it as a GitHub Actions artifact. The workflow has not been run in this environment. There is **no Gradle wrapper binary** in this source ZIP; use Android Studio's Gradle integration or installed Gradle.

## Features implemented in source

| Feature | Implementation |
|---|---|
| HOME/DEFAULT launcher role | Yes, native Android manifest and home-role chooser |
| Search installed Android apps | Yes, PackageManager launcher-intent query |
| Pin/unpin and hide/unhide | Yes, SharedPreferences |
| Icon grid, compact mode, 3–6 columns | Yes |
| Omarchy-inspired status panel, wallpaper, dock, clock | Yes, original native drawings |
| Themes | Ember, Glacier, Forest, Violet, Sand, custom hex |
| 24-hour time, text size, left-hand dock, status bar | Yes |
| Linux in-launcher command console | One-shot commands with asynchronous Termux output |
| Arch Linux ARM installer | Termux/PRoot-Distro bridge + GPG signature check; requires user-granted permission |
| Install packages | `install htop git ...` uses Arch `pacman` via PRoot |
| Add Linux commands as app shortcuts | Yes |
| Discover `.desktop` shortcuts | Yes, scan and import, requires configured GUI display |
| Full interactive terminal | Delegates to Termux foreground session, not embedded |
| Termux:X11 integration | Start command and documentation, external X11 app required |
| JSON settings backup/import | Yes, user-mediated Android document picker |

## Known limitations

- **No APK compiled in this environment:** the Android SDK/Gradle toolchain is not installed here and internet access from the build container is unavailable.
- PRoot, Termux and an optional GUI X server are separate dependencies. Not bundled in this APK.
- In-launcher console is not a PTY terminal. Interactive commands use Termux.
- Not yet run on a Pixel 9 or any physical device. No claim of hardware, installer or GUI success.
- GUI Linux applications show in Termux:X11, not native Android app windows; Omarchy/Hyprland cannot be run directly as an Android home screen.
- Installation may fail due to downloads, Android background execution policy, signed keyserver availability, PRoot support, Arch ARM mirror/keyring state, or storage.
- The launcher uses Android's standard permission scope, and does not require root, Accessibility Services or broad file permissions.

See [docs/LINUX_SETUP.md](docs/LINUX_SETUP.md) for detailed setup and troubleshooting.

## Sources and reference documentation

- [Arch Linux ARM official downloads](https://archlinuxarm.org/about/downloads)
- [Termux PRoot-Distro](https://github.com/termux/proot-distro)
- [Termux RUN_COMMAND API](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [Termux:X11](https://github.com/termux/termux-x11)
- [Android W^X execution restriction](https://developer.android.com/about/versions/10/behavior-changes-10)
- [Omarchy themes](https://omarchy.org/manual/themes/)

## Licence

AshDock source in this package is offered under the MIT License. Third-party projects (Termux, Arch Linux ARM and Omarchy) retain their own licenses and trademarks. This project does not redistribute their binaries or assets.

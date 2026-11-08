#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v gradle >/dev/null 2>&1; then
  echo 'Gradle 8.11.1 not found. Open this project in Android Studio or use the GitHub Actions workflow.' >&2
  exit 1
fi
gradle --no-daemon :app:assembleDebug
echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"

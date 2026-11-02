// Generated from .github/workflows/build.yml. Pushed with every app so GitHub builds it.
const buildWorkflow = r'''
name: Build APK

# Builds whatever Flutter project is on the pushed branch and publishes the APK
# as a GitHub Release, so the phone can download it with a plain link.
on:
  push:
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - uses: subosito/flutter-action@v2
        with:
          channel: stable
          cache: true

      - name: Read app name
        id: meta
        run: |
          NAME=$(grep -m1 '^name:' pubspec.yaml | awk '{print $2}' | tr -d '"'"'"'\r')
          echo "name=$NAME" >> "$GITHUB_OUTPUT"

      - name: Generate Android platform files
        run: |
          flutter create --platforms=android --project-name "${{ steps.meta.outputs.name }}" --org com.aibuilder .
          # Release builds need internet permission; allow http to a local AI server.
          MANIFEST=android/app/src/main/AndroidManifest.xml
          grep -q 'android.permission.INTERNET' $MANIFEST || \
            sed -i 's#<application#<uses-permission android:name="android.permission.INTERNET"/>\n    <application#' $MANIFEST
          grep -q 'usesCleartextTraffic' $MANIFEST || \
            sed -i '0,/<application/s#<application#<application android:usesCleartextTraffic="true"#' $MANIFEST

      - run: flutter pub get

      - name: Build APK
        run: flutter build apk --release

      - name: Publish release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ steps.meta.outputs.name }}-build-${{ github.run_number }}
          name: ${{ steps.meta.outputs.name }} build ${{ github.run_number }}
          target_commitish: ${{ github.sha }}
          files: build/app/outputs/flutter-apk/app-release.apk
''';

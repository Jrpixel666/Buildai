#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
DIR="$(mktemp -d)"
trap 'rm -rf "$DIR"' EXIT
kotlinc app/src/main/java/dev/ashdock/launcher/LinuxCommands.kt tests/LinuxCommandsSmoke.kt \
  -include-runtime -d "$DIR/linux-test.jar"
java -jar "$DIR/linux-test.jar"
SCRIPT="$(python3 -c 'import tempfile; print(tempfile.gettempdir())')/ashdock-installer-smoke.sh"
bash -n "$SCRIPT"
rm -f "$SCRIPT"
echo 'PASS: generated bash installer syntax'

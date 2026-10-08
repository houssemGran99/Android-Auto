#!/bin/bash
# Installs the Android SDK and warms the Gradle cache so `./gradlew test lintDebug assembleDebug`
# work in Claude Code cloud sessions. Requires dl.google.com in the environment's allowed domains.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-9862592_latest.zip"
SDK_PACKAGES=("platforms;android-35" "build-tools;35.0.0" "platform-tools")
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

# 1. Android command-line tools (idempotent).
if [ ! -x "$sdkmanager" ]; then
  echo "Installing Android command-line tools into $ANDROID_HOME" >&2
  tmp="$(mktemp -d)"
  curl -fsSL --retry 4 -o "$tmp/clt.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
  unzip -q "$tmp/clt.zip" -d "$tmp"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# 2. SDK packages used by the build (sdkmanager skips ones already installed).
yes | "$sdkmanager" --licenses >/dev/null 2>&1 || true
"$sdkmanager" --sdk_root="$ANDROID_HOME" "${SDK_PACKAGES[@]}" >/dev/null

# 3. Point Gradle and the session at the SDK.
echo "sdk.dir=$ANDROID_HOME" > "$PROJECT_DIR/local.properties"
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=\"$ANDROID_HOME\"" >> "$CLAUDE_ENV_FILE"
  echo "export ANDROID_SDK_ROOT=\"$ANDROID_HOME\"" >> "$CLAUDE_ENV_FILE"
fi

# 4. Warm the Gradle/dependency cache so later builds start fast. Best effort: Maven Central
#    sometimes rate-limits (HTTP 429), so retry and never fail the session start because of it.
cd "$PROJECT_DIR"
export ANDROID_HOME
for attempt in 1 2 3; do
  if ./gradlew --no-daemon -q assembleDebug compileDebugUnitTestKotlin >&2; then
    break
  fi
  echo "Gradle warm-up attempt $attempt failed; retrying" >&2
  sleep 15
done || true

exit 0

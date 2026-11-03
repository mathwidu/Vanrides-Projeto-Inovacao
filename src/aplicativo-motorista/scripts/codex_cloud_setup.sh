#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

FLUTTER_VERSION="${FLUTTER_VERSION:-3.38.5}"
FLUTTER_ROOT="${FLUTTER_ROOT:-$HOME/.cache/flutter-$FLUTTER_VERSION}"
INSTALL_ANDROID_SDK="${INSTALL_ANDROID_SDK:-1}"
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-36}"
ANDROID_BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-36.0.0}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
ANDROID_COMMANDLINE_TOOLS_URL="${ANDROID_COMMANDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip}"
WARM_ANDROID_BUILD="${WARM_ANDROID_BUILD:-0}"

log() {
  printf '\n[codex-cloud-setup] %s\n' "$1"
}

run_sudo() {
  if command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    "$@"
  fi
}

append_once() {
  local marker="$1"
  local line="$2"
  local bashrc="$HOME/.bashrc"

  touch "$bashrc"
  if ! grep -Fq "$marker" "$bashrc"; then
    {
      printf '\n# %s\n' "$marker"
      printf '%s\n' "$line"
    } >> "$bashrc"
  fi
}

install_apt_packages() {
  if ! command -v apt-get >/dev/null 2>&1; then
    return 0
  fi

  log "Installing Linux packages used by Flutter tooling"
  run_sudo apt-get update
  run_sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y \
    ca-certificates \
    clang \
    cmake \
    curl \
    git \
    libglu1-mesa \
    libgtk-3-dev \
    ninja-build \
    openjdk-17-jdk \
    pkg-config \
    unzip \
    xz-utils \
    zip
}

install_flutter() {
  if [[ ! -x "$FLUTTER_ROOT/bin/flutter" ]]; then
    log "Installing Flutter $FLUTTER_VERSION"
    rm -rf "$FLUTTER_ROOT"
    git clone --depth 1 --branch "$FLUTTER_VERSION" https://github.com/flutter/flutter.git "$FLUTTER_ROOT"
  fi

  export PATH="$FLUTTER_ROOT/bin:$FLUTTER_ROOT/bin/cache/dart-sdk/bin:$PATH"
  append_once "VANRIDES_CODEX_CLOUD_FLUTTER" "export PATH=\"$FLUTTER_ROOT/bin:$FLUTTER_ROOT/bin/cache/dart-sdk/bin:\$PATH\""

  flutter config --no-analytics
  flutter --version
  flutter precache --linux
}

install_android_sdk() {
  if [[ "$INSTALL_ANDROID_SDK" != "1" ]]; then
    log "Skipping Android SDK install (INSTALL_ANDROID_SDK=$INSTALL_ANDROID_SDK)"
    return 0
  fi

  log "Installing Android SDK command line tools"
  mkdir -p "$ANDROID_HOME/cmdline-tools"

  if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    tmp_dir="$(mktemp -d)"
    curl -fsSL "$ANDROID_COMMANDLINE_TOOLS_URL" -o "$tmp_dir/cmdline-tools.zip"
    unzip -q "$tmp_dir/cmdline-tools.zip" -d "$tmp_dir"
    rm -rf "$ANDROID_HOME/cmdline-tools/latest"
    mv "$tmp_dir/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -rf "$tmp_dir"
  fi

  export ANDROID_HOME
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

  append_once "VANRIDES_CODEX_CLOUD_ANDROID_HOME" "export ANDROID_HOME=\"$ANDROID_HOME\""
  append_once "VANRIDES_CODEX_CLOUD_ANDROID_ROOT" "export ANDROID_SDK_ROOT=\"$ANDROID_HOME\""
  append_once "VANRIDES_CODEX_CLOUD_ANDROID_PATH" "export PATH=\"$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:\$PATH\""

  sdkmanager --install \
    "platform-tools" \
    "platforms;android-$ANDROID_API_LEVEL" \
    "build-tools;$ANDROID_BUILD_TOOLS_VERSION"

  yes | sdkmanager --licenses >/dev/null || true
  flutter config --android-sdk "$ANDROID_HOME"
  flutter precache --android
}

warm_project() {
  log "Fetching Flutter dependencies"
  cd "$APP_DIR"
  flutter pub get

  if [[ "$WARM_ANDROID_BUILD" == "1" ]]; then
    log "Warming Gradle and Android build caches"
    flutter build apk --debug --flavor legacy \
      --dart-define=VANRIDES_API_BASE_URL="${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}" \
      --dart-define=VANRIDES_GOOGLE_PLACES_API_KEY="${VANRIDES_GOOGLE_PLACES_API_KEY:-}"
  fi
}

install_apt_packages
install_flutter
install_android_sdk
warm_project

log "Done. Recommended agent check: ./scripts/ci_quality_gate.sh"

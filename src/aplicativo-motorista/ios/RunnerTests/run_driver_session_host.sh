#!/bin/bash
set -euo pipefail

task_root="$(cd "$(dirname "$0")/../.." && pwd)"
task_developer="$(xcode-select -p)"
task_sdk="$(xcrun --sdk macosx --show-sdk-path)"
task_platform="$task_developer/Platforms/MacOSX.platform/Developer"
task_output="$(mktemp -d /tmp/vanrides-session-host.XXXXXX)"
trap 'rm -rf "$task_output"' EXIT

cd "$task_root"
xcrun --sdk macosx swiftc -swift-version 5 -sdk "$task_sdk" \
  -F "$task_platform/Library/Frameworks" \
  -I "$task_platform/usr/lib" -L "$task_platform/usr/lib" \
  -Xlinker -rpath -Xlinker "$task_platform/Library/Frameworks" \
  -Xlinker -rpath -Xlinker "$task_platform/Library/PrivateFrameworks" \
  -Xlinker -rpath -Xlinker "$task_developer/../SharedFrameworks" \
  -Xlinker -rpath -Xlinker "$task_platform/usr/lib" \
  -D DRIVER_SESSION_STANDALONE_TESTS \
  ios/Runner/DriverSessionCore.swift ios/Runner/DriverSessionNative.swift \
  ios/RunnerTests/DriverSessionHostTests.swift -o "$task_output/tests"
"$task_output/tests"

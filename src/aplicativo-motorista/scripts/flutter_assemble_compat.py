#!/usr/bin/env python3
"""Translate Flutter 3.38.5's Gradle assemble flag without inspecting its value."""
import json
import os
from pathlib import Path
import sys


def translate_arguments(arguments):
    # DartDefines is already one opaque, comma-separated base64 argument. Do not
    # split, decode, trim, re-encode, log or otherwise interpret its contents.
    prefix = '--DartDefines='
    return ['-dDartDefines=' + argument[len(prefix):] if argument.startswith(prefix) else argument
            for argument in arguments]


def real_flutter(environment):
    value = environment.get('VANRIDES_FLUTTER_REAL_EXECUTABLE', '')
    candidate = Path(value).expanduser()
    if not value or not candidate.is_absolute():
        raise ValueError('EXPLICIT_FLUTTER_EXECUTABLE_REQUIRED')
    candidate = candidate.resolve(strict=True)
    if candidate == Path(__file__).resolve() or not candidate.is_file() or not os.access(candidate, os.X_OK):
        raise ValueError('INVALID_FLUTTER_EXECUTABLE')
    return candidate


def main():
    try:
        executable = str(real_flutter(os.environ))
    except Exception:
        print('BLOCKED: EXPLICIT_FLUTTER_EXECUTABLE_REQUIRED', file=sys.stderr)
        return 2
    # The legacy assemble -d option splits commas in this SDK. Reuse the SDK's
    # command with only that parser option changed; no SDK file is modified.
    try:
        arguments = translate_arguments(sys.argv[1:])
        if 'assemble' in arguments:
            sdk = Path(executable).parent.parent
            dart = sdk / 'bin/cache/dart-sdk/bin/dart'
            packages = sdk / 'packages/flutter_tools/.dart_tool/package_config.json'
            metadata = json.loads((sdk / 'bin/cache/flutter.version.json').read_bytes())
            if metadata.get('frameworkVersion') != '3.38.5' or not dart.is_file() or not packages.is_file():
                raise OSError()
            environment = os.environ.copy()
            environment['FLUTTER_ROOT'] = str(sdk)
            entrypoint = Path(__file__).with_name('flutter_assemble_entrypoint.dart')
            os.execve(str(dart), [str(dart), '--packages=' + str(packages), str(entrypoint), *arguments], environment)
        else:
            os.execv(executable, [executable, *arguments])
    except (OSError, ValueError):
        print('BLOCKED: FLUTTER_EXEC_FAILED', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())

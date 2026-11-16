#!/usr/bin/env python3
"""Translate Flutter 3.38.5's Gradle assemble flag without inspecting its value."""
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
    # execv preserves argument boundaries and exit/signal behavior. No shell and
    # no fallback to another Flutter selected through PATH or a global config.
    try:
        os.execv(executable, [executable, *translate_arguments(sys.argv[1:])])
    except OSError:
        print('BLOCKED: FLUTTER_EXEC_FAILED', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())

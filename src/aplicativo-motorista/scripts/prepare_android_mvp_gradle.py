#!/usr/bin/env python3
"""Prepare a new private Gradle home; never build, install or read app configuration."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import sys

from flutter_assemble_compat import real_flutter

HERE = Path(__file__).resolve().parent


def outside_git(value):
    candidate = Path(value).expanduser()
    if candidate.is_symlink():
        raise ValueError('NEW_PRIVATE_GRADLE_HOME_REQUIRED')
    candidate = candidate.resolve()
    if any((parent / '.git').exists() for parent in (candidate, *candidate.parents)):
        raise ValueError('GRADLE_HOME_OUTSIDE_GIT_REQUIRED')
    return candidate


def trusted_directory(path):
    resolved = path.resolve(strict=True)
    details = resolved.stat()
    if not stat.S_ISDIR(details.st_mode) or details.st_uid != os.getuid() or stat.S_IMODE(details.st_mode) & 0o022:
        raise ValueError('PRIVATE_OWNED_CACHE_DIRECTORY_REQUIRED')
    return resolved


def prepare(directory, flutter, cache_source=None, heap_mb=768):
    target = outside_git(directory)
    if target.exists():
        raise ValueError('NEW_PRIVATE_GRADLE_HOME_REQUIRED')
    if not 256 <= heap_mb <= 2048:
        raise ValueError('BOUNDED_GRADLE_HEAP_REQUIRED')
    executable = real_flutter({'VANRIDES_FLUTTER_REAL_EXECUTABLE': str(flutter)})
    shared = {}
    if cache_source is not None:
        source = trusted_directory(Path(cache_source).expanduser())
        if source == target or source in target.parents or target in source.parents:
            raise ValueError('DISTINCT_CACHE_SOURCE_REQUIRED')
        for name in ('caches', 'wrapper'):
            path = source / name
            if path.exists():
                shared[name] = trusted_directory(path)
        # A modified wrapper distribution can itself contain global init hooks.
        # Reuse data only when the distribution carries no executable init file.
        if 'wrapper' in shared:
            for init_directory in shared['wrapper'].rglob('init.d'):
                if any(init_directory.glob('*.gradle')) or any(init_directory.glob('*.gradle.kts')):
                    raise ValueError('WRAPPER_DISTRIBUTION_INIT_REFUSED')
    os.umask(0o077)
    target.mkdir(mode=0o700, parents=True)
    target.chmod(0o700)
    for name, source in shared.items():
        (target / name).symlink_to(source, target_is_directory=True)
    init = HERE / 'flutter_assemble_compat.init.gradle'
    (target / 'init.d').mkdir(mode=0o700)
    (target / 'init.d/vanrides-driver-mvp.gradle').write_bytes(init.read_bytes())
    (target / 'gradle.properties').write_text(
        f'org.gradle.jvmargs=-Xmx{heap_mb}m -XX:MaxMetaspaceSize=384m\n'
        'org.gradle.workers.max=1\norg.gradle.parallel=false\norg.gradle.daemon=false\n'
        'kotlin.compiler.execution.strategy=in-process\n')
    receipt = {
        'schemaVersion': 1,
        'flutterExecutable': str(executable),
        'adapter': str(HERE / 'flutter_assemble_compat.py'),
        'adapterSha256': hashlib.sha256((HERE / 'flutter_assemble_compat.py').read_bytes()).hexdigest(),
        'initSha256': hashlib.sha256(init.read_bytes()).hexdigest(),
        'sharedDirectories': {name: str(path) for name, path in shared.items()},
        'sourceInitCopied': False,
        'sourcePropertiesCopied': False,
        'gradleHeapMb': heap_mb,
        'workers': 1,
    }
    (target / 'compat-receipt.json').write_text(json.dumps(receipt, sort_keys=True, indent=2) + '\n')
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--flutter', required=True, help='Absolute path to the real installed Flutter executable')
    parser.add_argument('--cache-source', help='Existing trusted Gradle home; reuse only caches and wrapper')
    parser.add_argument('--heap-mb', type=int, default=768)
    args = parser.parse_args()
    try:
        prepare(args.directory, args.flutter, args.cache_source, args.heap_mb)
    except ValueError as error:
        print('BLOCKED: ' + str(error), file=sys.stderr)
        return 2
    except Exception:
        print('BLOCKED: GRADLE_HOME_PREPARATION_FAILED', file=sys.stderr)
        return 2
    print('Prepared isolated Gradle home. Only caches/wrapper reused; no build executed.')
    return 0


if __name__ == '__main__':
    sys.exit(main())

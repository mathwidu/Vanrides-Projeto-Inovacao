"""Lightweight synthetic checks; no Flutter, Gradle, build, signing or device call."""
import base64
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import flutter_assemble_compat as compat
import prepare_android_mvp_gradle as prepare


class AssembleCompatibilityTest(unittest.TestCase):
    def test_complete_encoded_define_list_is_preserved_as_one_argument(self):
        encoded = ','.join(base64.b64encode(value.encode()).decode() for value in (
            'VANRIDES_PAIRING_LINK_HOST=links.example',
            'VANRIDES_WHATSAPP_BOT_NUMBER=SYNTHETIC_BOT',
            'SYNTHETIC_EXTRA=some=value,with,a,comma',
        ))
        arguments = ['--quiet', 'assemble', '--no-version-check', '--DartDefines=' + encoded, '-dFlavor=driver']
        result = compat.translate_arguments(arguments)
        self.assertEqual(result, [*arguments[:3], '-dDartDefines=' + encoded, arguments[4]])
        self.assertEqual(len(result), len(arguments))
        self.assertEqual(arguments[3], '--DartDefines=' + encoded)

    def test_translation_is_literal_including_padding_and_shell_characters(self):
        value = 'first==,second=,third==;$(never_execute)'
        self.assertEqual(compat.translate_arguments(['--DartDefines=' + value]), ['-dDartDefines=' + value])
        self.assertEqual(compat.translate_arguments(['-dDartDefines=existing==', '--dartdefines=other', ' assemble ']),
                         ['-dDartDefines=existing==', '--dartdefines=other', ' assemble '])

    def test_exec_uses_only_explicit_real_flutter_without_shell_or_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            executable = Path(temporary).resolve() / 'flutter'
            executable.write_text('#!/bin/sh\nexit 0\n'); executable.chmod(0o700)
            captured = io.StringIO()
            with patch.dict(os.environ, {'VANRIDES_FLUTTER_REAL_EXECUTABLE': str(executable)}), \
                 patch.object(compat.sys, 'argv', ['adapter', '--version']), \
                 patch.object(compat.os, 'execv') as execution, \
                 contextlib.redirect_stdout(captured), contextlib.redirect_stderr(captured):
                compat.main()
            execution.assert_called_once_with(str(executable), [str(executable), '--version'])
            self.assertEqual(captured.getvalue(), '')

    def test_assemble_uses_the_explicit_sdks_command_with_the_same_opaque_arguments(self):
        with tempfile.TemporaryDirectory() as temporary:
            sdk = Path(temporary).resolve()
            executable = sdk / 'bin/flutter'
            executable.parent.mkdir(); executable.write_text('#!/bin/sh\nexit 0\n'); executable.chmod(0o700)
            dart = sdk / 'bin/cache/dart-sdk/bin/dart'
            dart.parent.mkdir(parents=True); dart.write_text('synthetic')
            (sdk / 'bin/cache/flutter.version.json').write_text('{"frameworkVersion":"3.38.5"}')
            packages = sdk / 'packages/flutter_tools/.dart_tool/package_config.json'
            packages.parent.mkdir(parents=True); packages.write_text('{}')
            encoded = 'SYNTHETIC_ONE==,SYNTHETIC_TWO=='
            with patch.dict(os.environ, {'VANRIDES_FLUTTER_REAL_EXECUTABLE': str(executable)}), \
                 patch.object(compat.sys, 'argv', ['adapter', 'assemble', '--DartDefines=' + encoded]), \
                 patch.object(compat.os, 'execve') as execution:
                compat.main()
            arguments = execution.call_args.args
            self.assertEqual(arguments[0], str(dart))
            self.assertEqual(arguments[1][-2:], ['assemble', '-dDartDefines=' + encoded])
            self.assertEqual(arguments[1][1], '--packages=' + str(packages))
            self.assertEqual(arguments[2]['FLUTTER_ROOT'], str(sdk))

    def test_missing_implicit_or_recursive_flutter_is_refused_without_argument_dump(self):
        for value in ('', 'flutter', str(Path(compat.__file__).resolve())):
            captured = io.StringIO()
            with patch.dict(os.environ, {'VANRIDES_FLUTTER_REAL_EXECUTABLE': value}), \
                 patch.object(compat.sys, 'argv', ['adapter', '--DartDefines=SYNTHETIC_PRIVATE']), \
                 patch.object(compat.os, 'execv') as execution, contextlib.redirect_stderr(captured):
                self.assertEqual(compat.main(), 2)
            execution.assert_not_called()
            self.assertEqual(captured.getvalue(), 'BLOCKED: EXPLICIT_FLUTTER_EXECUTABLE_REQUIRED\n')


class IsolatedGradleHomeTest(unittest.TestCase):
    def fixture(self, root):
        flutter = root / 'flutter'
        flutter.write_text('#!/bin/sh\nexit 0\n'); flutter.chmod(0o700)
        source = root / 'source'
        (source / 'caches').mkdir(parents=True)
        (source / 'wrapper').mkdir()
        (source / 'init.d').mkdir()
        (source / 'init.d/old.gradle').write_text('SYNTHETIC_OLD_INIT')
        (source / 'gradle.properties').write_text('SYNTHETIC_OLD_PROPERTIES')
        (source / 'init.gradle').write_text('SYNTHETIC_OLD_GLOBAL_INIT')
        return flutter, source

    def test_reuses_only_cache_data_and_prepares_bounded_opt_in_home(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            flutter, source = self.fixture(root)
            target = root / 'isolated'
            receipt = prepare.prepare(target, flutter, source)
            self.assertEqual((target / 'caches').resolve(), source / 'caches')
            self.assertEqual((target / 'wrapper').resolve(), source / 'wrapper')
            self.assertTrue((target / 'caches').is_symlink())
            self.assertEqual({path.name for path in (target / 'init.d').iterdir()}, {'vanrides-driver-mvp.gradle'})
            self.assertFalse((target / 'init.gradle').exists())
            self.assertEqual((source / 'init.d/old.gradle').read_text(), 'SYNTHETIC_OLD_INIT')
            self.assertNotIn('SYNTHETIC_OLD', (target / 'gradle.properties').read_text())
            self.assertIn('org.gradle.workers.max=1', (target / 'gradle.properties').read_text())
            self.assertIn('-Xmx768m', (target / 'gradle.properties').read_text())
            self.assertFalse(receipt['sourceInitCopied'])
            self.assertFalse(receipt['sourcePropertiesCopied'])
            self.assertEqual(json.loads((target / 'compat-receipt.json').read_text()), receipt)
            self.assertEqual(target.stat().st_mode & 0o777, 0o700)
            self.assertEqual((target / 'compat-receipt.json').stat().st_mode & 0o777, 0o600)
            with self.assertRaisesRegex(ValueError, 'NEW_PRIVATE_GRADLE_HOME_REQUIRED'):
                prepare.prepare(target, flutter, source)

    def test_wrapper_distribution_init_and_writable_caches_are_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            flutter, source = self.fixture(root)
            global_init = source / 'wrapper/dists/gradle-synthetic/init.d'
            global_init.mkdir(parents=True)
            (global_init / 'unexpected.gradle').write_text('SYNTHETIC_INIT')
            with self.assertRaisesRegex(ValueError, 'WRAPPER_DISTRIBUTION_INIT_REFUSED'):
                prepare.prepare(root / 'blocked', flutter, source)
            self.assertFalse((root / 'blocked').exists())
            (global_init / 'unexpected.gradle').unlink()
            (source / 'caches').chmod(0o777)
            with self.assertRaisesRegex(ValueError, 'PRIVATE_OWNED_CACHE_DIRECTORY_REQUIRED'):
                prepare.prepare(root / 'blocked', flutter, source)

    def test_git_destinations_and_unbounded_heap_are_refused_before_writing(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            flutter, source = self.fixture(root)
            with self.assertRaisesRegex(ValueError, 'BOUNDED_GRADLE_HEAP_REQUIRED'):
                prepare.prepare(root / 'blocked', flutter, source, heap_mb=4096)
            (root / '.git').write_text('gitdir: synthetic')
            with self.assertRaisesRegex(ValueError, 'GRADLE_HOME_OUTSIDE_GIT_REQUIRED'):
                prepare.prepare(root / 'blocked', flutter, source)
            self.assertFalse((root / 'blocked').exists())


if __name__ == '__main__':
    unittest.main()

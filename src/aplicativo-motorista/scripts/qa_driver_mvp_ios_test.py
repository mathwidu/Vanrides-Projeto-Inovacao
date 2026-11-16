"""Offline privacy/target checks. No build, signing, simulator or device operation."""
import argparse
import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import plistlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('mvp_ui', Path(__file__).with_name('qa_driver_mvp_ios.py'))
mvp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mvp)


class MVPUIHelperTest(unittest.TestCase):
    def document(self):
        return {'TestConfigurations': [{'TestTargets': [{
            'BlueprintName': mvp.TARGET,
            'TestBundlePath': '/old/test', 'TestHostPath': '/old/host',
            'UITargetAppPath': '/old/driver.app', 'DependentProductPaths': ['/old/driver.app'],
            'EnvironmentVariables': {'UNTRUSTED': 'discard'},
            'UITargetAppCommandLineArguments': ['--bypass'],
        }]}]}

    def test_preparation_targets_installed_apps_without_reinstallation_or_injection(self):
        original = self.document()
        result = mvp.patch_run(original, 'dev.example.mvp.xctrunner')
        target = result['TestConfigurations'][0]['TestTargets'][0]
        self.assertTrue(target['UseDestinationArtifacts'])
        self.assertEqual(target['UITargetAppBundleIdentifier'], mvp.DRIVER)
        self.assertEqual(target['DependentProductPaths'], [])
        for key in ('UITargetAppPath', 'TestBundlePath', 'TestHostPath'):
            self.assertNotIn(key, target)
        self.assertEqual(target['UITargetAppCommandLineArguments'], [])
        self.assertEqual(target['EnvironmentVariables'], {})
        self.assertEqual(target['SystemAttachmentLifetime'], 'keepNever')

    def test_spike_or_multiple_test_targets_are_refused(self):
        document = self.document()
        document['TestConfigurations'][0]['TestTargets'][0]['BlueprintName'] = 'DriverGPSProofUITests'
        with self.assertRaises(mvp.PreparationError):
            mvp.patch_run(document, 'dev.example.mvp.xctrunner')
        document = self.document()
        document['TestConfigurations'][0]['TestTargets'].append(copy.deepcopy(document['TestConfigurations'][0]['TestTargets'][0]))
        with self.assertRaises(mvp.PreparationError):
            mvp.patch_run(document, 'dev.example.mvp.xctrunner')

    def test_prepare_writes_only_nonsecret_one_shot_command_without_device_calls(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            app = root / 'Runner.app'
            (app / ('PlugIns/' + mvp.TARGET + '.xctest')).mkdir(parents=True)
            (app / 'Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier': 'dev.example.mvp.xctrunner'}))
            source = root / 'source.xctestrun'
            source.write_bytes(plistlib.dumps(self.document()))
            output = root / 'run'
            with patch.object(mvp.subprocess, 'run') as execution:
                mvp.prepare(argparse.Namespace(output_dir=output, runner_app=app, source_xctestrun=source, command='copy-code'))
                execution.assert_not_called()
            fixture = json.loads((output / 'command.json').read_bytes())
            self.assertEqual(set(fixture), {'schemaVersion', 'command', 'sourceDigest'})
            self.assertEqual(fixture['command'], 'copy-code')
            self.assertEqual((output / 'command.json').stat().st_mode & 0o777, 0o600)
            with self.assertRaises(mvp.PreparationError):
                mvp.prepare(argparse.Namespace(output_dir=output, runner_app=app, source_xctestrun=source, command='inspect'))

    def test_private_input_refuses_public_files_symlinks_and_git_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / 'input.json'
            path.write_text('{}')
            path.chmod(0o644)
            with self.assertRaises(mvp.PreparationError):
                mvp.read_private(path)
            path.chmod(0o600)
            link = root / 'link.json'; link.symlink_to(path)
            with self.assertRaises(mvp.PreparationError):
                mvp.read_private(link)
            (root / '.git').write_text('gitdir: synthetic')
            with self.assertRaises(mvp.PreparationError):
                mvp.private_path(path)

    def test_private_links_accept_only_canonical_returns_and_explicit_start_shapes(self):
        token = 'A' * 43
        link = 'vanridesdriver://run/start#v1.12345678-1234-1234-1234-123456789012.7.' + token
        self.assertEqual(mvp.validate_link(link, ''), link)
        https = link.replace('vanridesdriver://run/start', 'https://links.example/motorista/saida')
        self.assertEqual(mvp.validate_link(https, 'links.example'), https)
        self.assertEqual(mvp.validate_link('vanridesdriver://pairing/return', ''), 'vanridesdriver://pairing/return')
        for invalid in (https, link + '.extra', link.replace('.7.', '.07.'), link.replace('.7.', '.9007199254740992.'),
                        link.replace('v1.', 'v2.'), link.replace('12345678', '%312345678'), link[:-1] + 'B',
                        'https://evil.example/motorista/vinculo/retorno'):
            with self.assertRaises(mvp.PreparationError):
                mvp.validate_link(invalid, '')

    def test_explicit_open_uses_private_link_and_never_prints_tool_errors(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'input.json'
            path.write_text(json.dumps({'device': 'synthetic-device', 'url': 'vanridesdriver://pairing/return'}))
            path.chmod(0o600)
            args = argparse.Namespace(input=path, host='')
            with patch.object(mvp.subprocess, 'run', return_value=subprocess.CompletedProcess([], 1, b'private', b'private')) as execution:
                with self.assertRaisesRegex(mvp.PreparationError, '^DEVICE_LINK_OPEN_FAILED$'):
                    mvp.open_link(args)
                self.assertIn('--payload-url', execution.call_args.args[0])
                self.assertTrue(execution.call_args.kwargs['capture_output'])
            captured = io.StringIO()
            with patch.object(mvp.sys, 'argv', ['helper', 'open-link', '--input', str(path)]), \
                 patch.object(mvp.subprocess, 'run', side_effect=RuntimeError('private credential')), \
                 contextlib.redirect_stderr(captured):
                self.assertEqual(mvp.main(), 2)
            self.assertEqual(captured.getvalue(), 'BLOCKED: LOCAL_OPERATION_FAILED\n')


if __name__ == '__main__':
    unittest.main()

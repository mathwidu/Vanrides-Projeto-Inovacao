"""Offline installation and no-repository-secret regression tests."""
import json
import os
from pathlib import Path
import shutil
import stat
import tempfile
import unittest
from unittest.mock import patch, Mock

import install_private as installer
import launch_vps110_mac as launch
import protocol as p


class PrivateInstallationTest(unittest.TestCase):
    def test_install_copies_only_pinned_sources_into_new_private_directory(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(Path,'home',return_value=Path(folder).resolve()):
            destination,receipt=installer.install()
            self.assertEqual(Path(folder).resolve()/'.local/share/vanrides/wa110/mac-runtimes',destination.parent)
            self.assertEqual(0o700,stat.S_IMODE(destination.stat().st_mode))
            self.assertEqual(set(installer.FILES)|{'INSTALLED_SOURCES.json'},{node.name for node in destination.iterdir()})
            for name in installer.FILES:
                self.assertEqual((installer.SOURCE/name).read_bytes(),(destination/name).read_bytes())
                self.assertEqual(0o600,stat.S_IMODE((destination/name).stat().st_mode))
            self.assertFalse(receipt['runtimeStarted']);self.assertFalse(receipt['specsSecretsOrReceiptsImported'])

    def test_existing_destination_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(Path,'home',return_value=Path(folder).resolve()):
            destination,_=installer.install();marker=destination/'keep';marker.write_text('synthetic')
            with self.assertRaisesRegex(ValueError,'NEW_DESTINATION'):installer.install(destination)
            self.assertEqual('synthetic',marker.read_text())

    def test_outside_private_base_and_symlink_parent_are_denied(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(Path,'home',return_value=Path(folder).resolve()):
            outside=Path(folder)/'outside'
            with self.assertRaisesRegex(ValueError,'DIRECT_CHILD'):installer.install(outside)
            self.assertFalse(outside.exists())
            other=Path(folder)/'other';other.mkdir();(Path(folder)/'.local').symlink_to(other)
            with self.assertRaisesRegex(ValueError,'PARENT_SYMLINK'):installer.install()
            self.assertEqual([],list(other.iterdir()))

    def test_changed_source_fails_before_any_destination_is_created(self):
        with tempfile.TemporaryDirectory() as folder:
            base=Path(folder).resolve();source=base/'source';source.mkdir();home=base/'home';home.mkdir()
            for name in (*installer.FILES,'SOURCE_PINS.json'):shutil.copyfile(installer.SOURCE/name,source/name)
            (source/'protocol.py').write_bytes((source/'protocol.py').read_bytes()+b'\n# synthetic mutation\n')
            with patch.object(installer,'SOURCE',source),patch.object(Path,'home',return_value=home):
                with self.assertRaisesRegex(ValueError,'SOURCE_HASH_CHANGED'):installer.install()
            self.assertFalse((home/'.local').exists())

    def test_runtime_origin_is_preserved_except_for_the_verified_artifact_pins(self):
        pins=json.loads((installer.SOURCE/'SOURCE_PINS.json').read_text())
        self.assertEqual('f7b567891e3d0ac872438b4e2c80a6f60644cab02540f89ab08b711bffb9adc9',pins['originPreparedManifestSha256'])
        previous=b'd179e0e94c65efc65ba82e327e537cefbab2e77df9f5a229bfb3be5cf7f14f6f'
        reviewed=b'613ae571ac0b8cf78c212344edb0e890eee2203c329f9106c056c44af928b7c5'
        previous_apk=b'902962e5d67eb688f0001d2a76d89b04c5fc86b5f0698f7512ca7cbc2dfb3b05'
        reviewed_apk=b'e022b8f2eafe1caa0f3eed13ea7e20d48e27f8a85076383d0175f98bf6b94cce'
        self.assertEqual(reviewed.decode(),p.READER)
        self.assertEqual(reviewed_apk.decode(),p.APK)
        for name in ('protocol.py','transport.py','phone_guard_core.py'):
            source=(installer.SOURCE/name).read_bytes()
            if name!='transport.py':
                for current,original in ((reviewed,previous),(reviewed_apk,previous_apk)):
                    self.assertEqual(1,source.count(current));self.assertNotIn(original,source)
                    source=source.replace(current,original)
            self.assertEqual(pins['originFiles'][name],installer.sha(source))


class RepositoryWriteBarrierTest(unittest.TestCase):
    def test_prepare_run_and_recover_in_repository_never_generate_or_write_a_nonce(self):
        with patch.object(launch.secrets,'token_urlsafe') as nonce,patch.object(launch.g,'write_journal') as write:
            actions=(lambda:launch.prepare('fixture',installer.SOURCE/'spec-physical.json','a'*64),
                     lambda:launch.run(None,'a'*64),lambda:launch.recover(None,'a'*64,installer.SOURCE/'session.json'))
            for action in actions:
                with self.assertRaisesRegex(p.Rejected,'PRIVATE_DIRECTORY_OUTSIDE_GIT'):action()
            nonce.assert_not_called();write.assert_not_called()
        self.assertFalse((installer.SOURCE/'spec-physical.json').exists());self.assertFalse((installer.SOURCE/'session.json').exists())

    def test_private_mode_does_not_override_a_git_ancestor(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'.git').write_text('gitdir: synthetic fixture')
            with patch.object(launch,'ROOT',root),self.assertRaisesRegex(p.Rejected,'OUTSIDE_GIT'):launch.require_private_runtime()

    def test_private_location_checks_owner_mode_and_prevents_cross_directory_recovery(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);root.chmod(0o700)
            # Simulate an equivalent directory outside the checkout without changing HOME.
            with patch.object(launch,'ROOT',root),patch.object(Path,'exists',return_value=False):
                launch.require_private_runtime()
                with patch.object(Path,'lstat',return_value=Mock(st_uid=os.getuid()+1,st_mode=0o40700)):
                    with self.assertRaises(p.Rejected):launch.require_private_runtime()
                root.chmod(0o755)
                with self.assertRaises(p.Rejected):launch.require_private_runtime()
            root.chmod(0o700)
            with patch.object(launch,'ROOT',root),patch.object(launch,'require_private_runtime'),patch.object(launch.g,'write_journal') as write:
                with self.assertRaisesRegex(p.Rejected,'JOURNAL_MUST_STAY'):launch.recover(None,'a'*64,root.parent/'foreign-session.json')
                write.assert_not_called()


if __name__=='__main__':unittest.main()

import tempfile
import unittest
from pathlib import Path
from unittest.mock import call, patch
from zipfile import ZipFile

import atualizar as cd


class AtualizarTest(unittest.TestCase):
    request = cd.Request('1' * 40, 'sha256:' + '2' * 64, '3' * 64)
    old = cd.Service(cd.REPOSITORY + ':previous', '4' * 64, 'completed', '101')
    new = cd.Service(request.image, request.jar, 'completed', '102')

    def test_accepts_only_the_exact_forced_command(self):
        command = f'deploy {self.request.source} {self.request.digest} {self.request.jar}'
        self.assertEqual(cd.request_from(command), self.request)
        for invalid in ('', command + ' ', command + '\n', command + ' ; date', command.replace('deploy ', 'rollback ')):
            with self.subTest(invalid=invalid), self.assertRaises(cd.DeployError):
                cd.request_from(invalid)

    def test_switches_digest_and_jar_in_one_update(self):
        with patch.object(cd, 'docker') as docker, patch.object(cd, 'wait_ready') as ready:
            cd.rollout(self.request, self.old, 'old-id', 'new-id')
        docker.assert_called_once_with(
            'service', 'update', '--detach', '--with-registry-auth', '--image', self.request.image,
            '--env-add', cd.JAR_ENV + self.request.jar, cd.SERVICE, timeout=45,
        )
        ready.assert_called_once_with(self.request.image, self.request.jar, 'new-id')

    def test_failed_smoke_restores_previous_spec_and_keeps_run_failed(self):
        old_spec = self.old.image + '|' + self.old.jar
        with patch.object(cd, 'docker') as docker, patch.object(cd, 'service', return_value=self.new), \
             patch.object(cd, 'previous', return_value=old_spec), \
             patch.object(cd, 'wait_ready', side_effect=[cd.DeployError('Smoke falhou.'), None]) as ready:
            with self.assertRaisesRegex(cd.DeployError, 'anteriores restaurados'):
                cd.rollout(self.request, self.old, 'old-id', 'new-id')
        self.assertEqual(docker.call_args_list[-1], call('service', 'update', '--detach', '--rollback', cd.SERVICE, timeout=45))
        self.assertEqual(ready.call_args_list[-1], call(self.old.image, self.old.jar, 'old-id'))

    def test_blocks_rollback_when_previous_spec_or_current_revision_changed(self):
        foreign = cd.Service(cd.REPOSITORY + ':foreign', '5' * 64, 'completed', '103')
        for current in (foreign, self.new):
            with self.subTest(current=current), patch.object(cd, 'docker') as docker, \
                 patch.object(cd, 'service', return_value=current), patch.object(cd, 'previous', return_value='wrong'), \
                 patch.object(cd, 'wait_ready', side_effect=cd.DeployError('Smoke falhou.')):
                with self.assertRaisesRegex(cd.DeployError, 'Retorno bloqueado'):
                    cd.rollout(self.request, self.old, 'old-id', 'new-id')
                docker.assert_called_once()

    def test_changed_migration_prevents_update(self):
        old_image = ('old-id', '5' * 40, '6' * 40, self.old.jar)
        new_image = ('new-id', self.request.source, '7' * 40, self.request.jar)
        with patch.object(cd, 'docker', side_effect=['srv1397065|active|true', 'single-node', 'pulled']), \
             patch.object(cd, 'service', return_value=self.old), \
             patch.object(cd, 'image_info', side_effect=[old_image, new_image]), \
             patch.object(cd, 'running_image', return_value='old-id'), patch.object(cd, 'https_ready', return_value=True), \
             patch.object(cd, 'verify_source', return_value=new_image[2]), \
             patch.object(cd, 'inspect_jar', side_effect=[{'V38': 'x'}, {'V38': 'x', 'V39': 'y'}]), \
             patch.object(cd, 'rollout') as rollout:
            with self.assertRaisesRegex(cd.DeployError, 'plano de banco'):
                cd.deploy(self.request)
        rollout.assert_not_called()

    def test_ancestry_failure_stops_before_pull(self):
        old_image = ('old-id', '5' * 40, '6' * 40, self.old.jar)
        with patch.object(cd, 'docker', side_effect=['srv1397065|active|true', 'single-node']) as docker, \
             patch.object(cd, 'service', return_value=self.old), patch.object(cd, 'image_info', return_value=old_image), \
             patch.object(cd, 'running_image', return_value='old-id'), patch.object(cd, 'https_ready', return_value=True), \
             patch.object(cd, 'verify_source', side_effect=cd.DeployError('Revisão anterior à instalada.')):
            with self.assertRaisesRegex(cd.DeployError, 'anterior'):
                cd.deploy(self.request)
        self.assertEqual(docker.call_count, 2)

    def test_source_requires_candidate_on_main_and_installed_source_as_ancestor(self):
        installed = '5' * 40
        source_tree = '6' * 40
        with patch.object(cd, 'git', side_effect=['', '', '', source_tree]) as git:
            self.assertEqual(cd.verify_source(self.request.source, installed), source_tree)
        self.assertEqual(git.call_args_list[1:], [
            call('merge-base', '--is-ancestor', self.request.source, 'refs/heads/main'),
            call('merge-base', '--is-ancestor', installed, self.request.source),
            call('show', '-s', '--format=%T', self.request.source),
        ])

    def test_inventory_compares_migration_bytes_and_configuration(self):
        with tempfile.TemporaryDirectory() as folder:
            def jar(name, sql, enabled='true', app='original'):
                file = Path(folder) / (name + '.jar')
                with ZipFile(file, 'w') as archive:
                    archive.writestr('BOOT-INF/classes/db/migration/V38__example.sql', sql)
                    archive.writestr('BOOT-INF/classes/application.properties', f'example.app={app}\n')
                    archive.writestr('BOOT-INF/classes/application-homolog.properties', f'spring.flyway.enabled={enabled}\n')
                return cd.migration_inventory(file)

            original = jar('base', 'SELECT 1;')
            self.assertEqual(original, jar('app', 'SELECT 1;', app='updated'))
            self.assertNotEqual(original, jar('changed-sql', 'SELECT 2;'))
            self.assertNotEqual(original, jar('changed-config', 'SELECT 1;', enabled='false'))

    def test_bootstrap_accepts_only_the_preapproved_new_v39(self):
        old = {'V38': 'old', 'flyway': 'unchanged'}
        checksum = 'a' * 64
        candidate = {**old, cd.TEAM_MIGRATION: checksum}
        self.assertTrue(cd.allow_migrations(old, candidate, checksum))
        self.assertFalse(cd.allow_migrations(old, old, checksum))
        self.assertFalse(cd.allow_migrations(candidate, candidate, None))
        for invalid in ({**candidate, 'V40': checksum}, {**candidate, 'V38': 'changed'},
                        {**candidate, cd.TEAM_MIGRATION: 'b' * 64}):
            with self.subTest(invalid=invalid), self.assertRaisesRegex(cd.DeployError, 'somente a adição'):
                cd.allow_migrations(old, invalid, checksum)
        with self.assertRaises(cd.DeployError):
            cd.allow_migrations(old, candidate, None)

    def test_rejects_additional_command_line_arguments(self):
        arguments = ['atualizar.py', '--bootstrap-v39', self.request.source, self.request.digest, self.request.jar]
        with patch.object(cd.sys, 'argv', arguments), patch.dict(cd.os.environ, {'SSH_ORIGINAL_COMMAND': 'deploy'}), \
             patch.object(cd, 'deploy') as deploy, patch('sys.stderr'):
            self.assertEqual(cd.main(), 1)
        deploy.assert_not_called()

    def test_bootstrap_approval_must_be_private(self):
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / f'bootstrap-v39-{self.request.source}.sha256'
            self.assertIsNone(cd.read_bootstrap_checksum(file))
            file.write_text('a' * 64 + '\n')
            file.chmod(0o644)
            with self.assertRaises(cd.DeployError):
                cd.read_bootstrap_checksum(file)
            file.chmod(0o600)
            self.assertEqual(cd.read_bootstrap_checksum(file), 'a' * 64)
            symlink = Path(folder) / 'untrusted-link'
            symlink.symlink_to(file)
            with self.assertRaises(OSError):
                cd.read_bootstrap_checksum(symlink)

    def test_forced_command_consumes_only_matching_approval_used_successfully(self):
        command = f'deploy {self.request.source} {self.request.digest} {self.request.jar}'
        scenarios = (
            (self.request.source, True, 'a' * 64, False, 0),
            (self.request.source, False, 'a' * 64, True, 0),
            (self.request.source, cd.DeployError('Smoke falhou.'), 'a' * 64, True, 1),
            ('5' * 40, False, None, True, 0),
        )
        for source, result, expected_pin, approval_remains, exit_code in scenarios:
            with self.subTest(source=source, result=result), tempfile.TemporaryDirectory() as folder, \
                 patch.object(cd, 'STATE', Path(folder)), patch.object(cd.os, 'umask'), \
                 patch.object(cd.sys, 'argv', ['atualizar.py']), \
                 patch.dict(cd.os.environ, {'SSH_ORIGINAL_COMMAND': command}), \
                 patch.object(cd, 'deploy', side_effect=[result]) as deploy, patch('sys.stderr'), patch('sys.stdout'):
                approval = cd.STATE / f'bootstrap-v39-{source}.sha256'
                approval.write_text('a' * 64 + '\n')
                approval.chmod(0o600)
                self.assertEqual(cd.main(), exit_code)
                deploy.assert_called_once_with(self.request, expected_pin)
                self.assertEqual(approval.exists(), approval_remains)

    def test_retry_can_consume_approval_for_healthy_identical_image_and_v39(self):
        checksum = 'a' * 64
        current = ('new-id', self.request.source, '7' * 40, self.request.jar)
        with patch.object(cd, 'docker', side_effect=['srv1397065|active|true', 'single-node']), \
             patch.object(cd, 'service', return_value=self.new), \
             patch.object(cd, 'image_info', return_value=current), \
             patch.object(cd, 'running_image', return_value='new-id'), patch.object(cd, 'https_ready', return_value=True), \
             patch.object(cd, 'verify_source', return_value=current[2]), \
             patch.object(cd, 'inspect_jar', return_value={cd.TEAM_MIGRATION: checksum}), \
             patch.object(cd, 'wait_ready') as ready, patch.object(cd, 'rollout') as rollout, patch('sys.stdout'):
            self.assertTrue(cd.deploy(self.request, checksum))
        ready.assert_called_once_with(self.request.image, self.request.jar, 'new-id')
        rollout.assert_not_called()

    def test_new_digest_for_installed_source_still_runs_the_full_image_checks_and_rollout(self):
        source_tree = '7' * 40
        old_image = ('old-id', self.request.source, source_tree, self.old.jar)
        new_image = ('new-id', self.request.source, source_tree, self.request.jar)
        migrations = {'V38': 'unchanged'}
        with patch.object(cd, 'docker', side_effect=['srv1397065|active|true', 'single-node', 'pulled']), \
             patch.object(cd, 'service', return_value=self.old), \
             patch.object(cd, 'image_info', side_effect=[old_image, new_image]), \
             patch.object(cd, 'running_image', return_value='old-id'), patch.object(cd, 'https_ready', return_value=True), \
             patch.object(cd, 'verify_source', return_value=source_tree), \
             patch.object(cd, 'inspect_jar', return_value=migrations) as inspect, \
             patch.object(cd, 'rollout') as rollout, patch('sys.stdout'):
            self.assertFalse(cd.deploy(self.request))
        self.assertEqual(inspect.call_args_list, [
            call(self.old.image, self.old.jar, cd.STATE), call(self.request.image, self.request.jar, cd.STATE),
        ])
        rollout.assert_called_once_with(self.request, self.old, 'old-id', 'new-id')

    def test_healthy_old_task_cannot_approve_a_different_digest(self):
        with patch.object(cd, 'service', return_value=self.new), patch.object(cd, 'running_image', return_value='old-id'), \
             patch.object(cd.time, 'monotonic', side_effect=[0, 0, 181]), patch.object(cd.time, 'sleep'), \
             patch.object(cd, 'https_ready', return_value=True) as https:
            with self.assertRaisesRegex(cd.DeployError, 'não convergiu'):
                cd.wait_ready(self.request.image, self.request.jar, 'new-id')
        https.assert_not_called()


if __name__ == '__main__':
    unittest.main()

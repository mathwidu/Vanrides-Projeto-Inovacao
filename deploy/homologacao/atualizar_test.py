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

    def test_approval_accepts_only_the_approved_new_migration(self):
        v39, v40 = cd.MIGRATIONS + 'V39__a.sql', cd.MIGRATIONS + 'V40__b.sql'
        config = 'BOOT-INF/classes/application.properties'
        old = {v39: 'old', 'BOOT-INF/lib/flyway-core.jar': 'unchanged', config: ('spring.flyway.enabled=true',)}
        checksum = 'a' * 64
        approval = (v40, checksum)
        candidate = {**old, v40: checksum}
        self.assertTrue(cd.allow_migrations(old, candidate, approval))
        self.assertFalse(cd.allow_migrations(old, old, approval))
        self.assertFalse(cd.allow_migrations(candidate, candidate, None))
        renamed = {key: value for key, value in old.items() if key != v39}
        for invalid in ({**candidate, cd.MIGRATIONS + 'V41__c.sql': checksum}, {**candidate, v39: 'changed'},
                        {**old, v40: 'b' * 64}, {**old, cd.MIGRATIONS + 'V40__other.sql': checksum},
                        {**old, 'BOOT-INF/lib/flyway-new.jar': checksum},
                        {**candidate, config: ('spring.flyway.enabled=false',)},
                        {**renamed, v40: checksum}):
            with self.subTest(invalid=invalid), self.assertRaisesRegex(cd.DeployError, 'somente a adição'):
                cd.allow_migrations(old, invalid, approval)
        with self.assertRaisesRegex(cd.DeployError, 'somente a adição'):
            cd.allow_migrations(candidate, {**candidate, v39: 'changed'}, approval)
        with self.assertRaisesRegex(cd.DeployError, 'plano de banco'):
            cd.allow_migrations(old, candidate, None)

    def test_rejects_additional_command_line_arguments(self):
        arguments = ['atualizar.py', '--approve-migration', self.request.source, self.request.digest, self.request.jar]
        with patch.object(cd.sys, 'argv', arguments), patch.dict(cd.os.environ, {'SSH_ORIGINAL_COMMAND': 'deploy'}), \
             patch.object(cd, 'deploy') as deploy, patch('sys.stderr'):
            self.assertEqual(cd.main(), 1)
        deploy.assert_not_called()

    def test_migration_approval_must_be_private_and_name_one_versioned_migration(self):
        valid = 'a' * 64 + '  V40__whatsapp_team_test_driver.sql\n'
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / f'migration-{self.request.source}.sha256'
            self.assertIsNone(cd.read_approval(file))
            file.write_text(valid)
            file.chmod(0o644)
            with self.assertRaises(cd.DeployError):
                cd.read_approval(file)
            file.chmod(0o600)
            self.assertEqual(cd.read_approval(file),
                             (cd.MIGRATIONS + 'V40__whatsapp_team_test_driver.sql', 'a' * 64))
            for content in ('a' * 64 + '\n', 'a' * 64 + '  R__repeatable.sql\n', 'a' * 64 + '  ../V40__x.sql\n',
                            'a' * 64 + ' V40__x.sql\n', 'A' * 64 + '  V40__x.sql\n'):
                file.write_text(content)
                with self.subTest(content=content), self.assertRaisesRegex(cd.DeployError, 'inválida'):
                    cd.read_approval(file)
            symlink = Path(folder) / 'untrusted-link'
            symlink.symlink_to(file)
            with self.assertRaises(OSError):
                cd.read_approval(symlink)

    def test_forced_command_consumes_only_matching_approval_used_successfully(self):
        command = f'deploy {self.request.source} {self.request.digest} {self.request.jar}'
        scenarios = (
            (self.request.source, True, True, False, 0),
            (self.request.source, False, True, True, 0),
            (self.request.source, cd.DeployError('Smoke falhou.'), True, True, 1),
            ('5' * 40, False, None, True, 0),
        )
        approved = (cd.MIGRATIONS + 'V40__b.sql', 'a' * 64)
        for source, result, expected_pin, approval_remains, exit_code in scenarios:
            with self.subTest(source=source, result=result), tempfile.TemporaryDirectory() as folder, \
                 patch.object(cd, 'STATE', Path(folder)), patch.object(cd.os, 'umask'), \
                 patch.object(cd.sys, 'argv', ['atualizar.py']), \
                 patch.dict(cd.os.environ, {'SSH_ORIGINAL_COMMAND': command}), \
                 patch.object(cd, 'deploy', side_effect=[result]) as deploy, patch('sys.stderr'), patch('sys.stdout'):
                approval = cd.STATE / f'migration-{source}.sha256'
                approval.write_text('a' * 64 + '  V40__b.sql\n')
                approval.chmod(0o600)
                self.assertEqual(cd.main(), exit_code)
                deploy.assert_called_once_with(self.request, approved if expected_pin else None)
                self.assertEqual(approval.exists(), approval_remains)

    def test_retry_consumes_approval_only_when_the_healthy_image_has_the_approved_migration(self):
        approval = (cd.MIGRATIONS + 'V40__b.sql', 'a' * 64)
        current = ('new-id', self.request.source, '7' * 40, self.request.jar)
        for installed, consumed in (({approval[0]: approval[1]}, True), ({approval[0]: 'b' * 64}, False),
                                    ({cd.MIGRATIONS + 'V40__other.sql': approval[1]}, False)):
            with self.subTest(installed=installed), \
                 patch.object(cd, 'docker', side_effect=['srv1397065|active|true', 'single-node']), \
                 patch.object(cd, 'service', return_value=self.new), \
                 patch.object(cd, 'image_info', return_value=current), \
                 patch.object(cd, 'running_image', return_value='new-id'), patch.object(cd, 'https_ready', return_value=True), \
                 patch.object(cd, 'verify_source', return_value=current[2]), \
                 patch.object(cd, 'inspect_jar', return_value=installed), \
                 patch.object(cd, 'wait_ready') as ready, patch.object(cd, 'rollout') as rollout, patch('sys.stdout'):
                self.assertEqual(cd.deploy(self.request, approval), consumed)
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

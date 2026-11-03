#!/usr/bin/env python3
"""Regression tests for partial workspaces; product gates stay complete by default."""

import copy
import io
import json
import subprocess
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock

import program_control as control
from program_control_test import valid_fixture, write_control_fixture


class WorkspaceProfileTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.program, self.repositories = valid_fixture(self.root)
        self.repositories['repositories'].append({
            'id': 'maps', 'path': 'maps', 'cardGate': 'gate', 'ciGate': 'test',
        })
        (self.root / 'workspaces').mkdir()
        self.profile = {
            'schemaVersion': 1, 'name': 'core', 'included': ['coordination'],
            'deferred': {'maps': 'Owner deferred restoration.'},
        }
        self.write_profile()

    def write_profile(self):
        (self.root / 'workspaces/core.json').write_text(json.dumps(self.profile))

    def codes(self, *, scoped):
        deferred = control.load_workspace('core' if scoped else None, self.repositories, self.root)
        return {issue.code for issue in control.validate_static(
            self.program, self.repositories, self.root, deferred_repositories=deferred,
        )}

    def test_full_requires_missing_maps_core_does_not(self):
        self.assertIn('REPOSITORY_PATH', self.codes(scoped=False))
        self.assertNotIn('REPOSITORY_PATH', self.codes(scoped=True))

    def test_core_still_requires_included_repository(self):
        self.profile['included'].append('app')
        self.repositories['repositories'].append({
            'id': 'app', 'path': 'app', 'cardGate': 'gate', 'ciGate': 'test',
        })
        self.write_profile()
        self.assertIn('REPOSITORY_PATH', self.codes(scoped=True))

    def test_executable_maps_cards_are_rejected_even_if_directory_exists(self):
        (self.root / 'maps').mkdir()
        for status in ['ready', 'in_progress', 'validating']:
            with self.subTest(status=status):
                self.program['cards'][0]['status'] = status
                self.program['cards'][0]['repositories'] = ['coordination', 'maps']
                self.assertIn('WORKSPACE_CARD_REPOSITORY', self.codes(scoped=True))

    def test_core_keeps_all_static_contract_checks(self):
        self.repositories['repositories'][1].pop('cardGate')
        self.program['cards'][0]['blockers'] = ['A real unresolved blocker']
        issues = self.codes(scoped=True)
        self.assertIn('REPOSITORY_GATE', issues)
        self.assertTrue(any('BLOCKER' in code for code in issues))

    def test_profile_rejects_incomplete_unknown_duplicate_overlap_and_empty_reason(self):
        invalid = [
            {'included': []},
            {'included': ['coordination', 'unknown']},
            {'included': ['coordination', 'coordination']},
            {'included': ['coordination', 'maps']},
            {'deferred': {}},
            {'deferred': {'maps': ''}},
            {'deferred': {'maps': None}},
            {'schemaVersion': 2},
            {'name': 'another'},
            {'included': 'coordination'},
            {'included': [[]]},
        ]
        valid = copy.deepcopy(self.profile)
        for change in invalid:
            with self.subTest(change=change):
                self.profile = {**valid, **change}
                self.write_profile()
                with self.assertRaises(ValueError):
                    control.load_workspace('core', self.repositories, self.root)

    def test_profile_cannot_traverse_directories_or_silently_fall_back(self):
        for name in ['../core', '/tmp/core', 'missing']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                control.load_workspace(name, self.repositories, self.root)

    def test_next_is_usable_for_core_but_refuses_maps_work(self):
        write_control_fixture(self.root, self.program, self.repositories)
        with redirect_stdout(io.StringIO()):
            self.assertEqual(0, control.command_next(self.root, workspace='core'))
        self.program['cards'][0]['repositories'].append('maps')
        write_control_fixture(self.root, self.program, self.repositories)
        output = io.StringIO()
        with redirect_stdout(output):
            self.assertEqual(1, control.command_next(self.root, workspace='core'))
        self.assertIn('WORKSPACE_CARD_REPOSITORY', output.getvalue())
        self.assertNotIn('CARD-001 [in_progress]', output.getvalue())

    def test_runtime_does_not_accept_a_folder_inheriting_parent_git(self):
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        (self.root / 'maps').mkdir()
        issues = control.validate_runtime(self.program, self.repositories, self.root)
        self.assertTrue(any(issue.code == 'NOT_GIT_REPOSITORY' and 'maps:' in issue.message for issue in issues))

    def test_deferred_git_is_not_probed_and_is_reported_as_not_evaluated(self):
        repos = {'repositories': [self.repositories['repositories'][1]]}
        with mock.patch.object(control, 'git_output') as git:
            issues = control.validate_runtime(
                self.program, repos, self.root, deferred_repositories=frozenset({'maps'}),
            )
        git.assert_not_called()
        self.assertEqual(['WORKSPACE_DEFERRED'], [issue.code for issue in issues])

    def test_dashboard_profile_cannot_be_mistaken_for_full(self):
        folder = self.root / '.dev-harness'
        folder.mkdir()
        (folder / 'dashboard.md').write_text('\n'.join([
            control.DASHBOARD_MARKER,
            'Active card: `CARD-001`', 'Pilot: `None`', 'Production: `None`',
            'Workspace: `core`',
        ]))
        self.assertEqual([], control.validate_dashboard(self.program, self.root, 'core'))
        self.assertEqual('DASHBOARD_STALE', control.validate_dashboard(self.program, self.root)[0].code)

    def test_projection_initializes_once_and_preserves_existing_harness_state(self):
        write_control_fixture(self.root, self.program, self.repositories)
        (self.root / '.dev-harness').mkdir()
        control.initialize_local_status(self.root)
        path = self.root / '.dev-harness/status.json'
        self.assertEqual('CARD-001', json.loads(path.read_text())['active_card'])
        path.write_text('{"active_card":"another-card"}\n')
        before = path.read_bytes()
        control.initialize_local_status(self.root)
        self.assertEqual(before, path.read_bytes())


    def shared_repositories(self):
        self.repositories['repositories'][0].update(
            repositoryRoot='.', moduleRoot='.', path='.',
        )
        backend = {
            'id': 'backend', 'path': 'src/backend', 'moduleRoot': 'src/backend',
            'repositoryRoot': '.', 'cardGate': 'gate', 'ciGate': 'test',
        }
        self.repositories['repositories'].append(backend)
        (self.root / 'src/backend').mkdir(parents=True)
        self.profile['included'].append('backend')
        self.write_profile()
        return backend

    def test_shared_git_requires_explicit_root_and_existing_module(self):
        backend = self.shared_repositories()
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        issues = control.validate_runtime(
            self.program, self.repositories, self.root,
            deferred_repositories=frozenset({'maps'}),
        )
        self.assertNotIn('NOT_GIT_REPOSITORY', {issue.code for issue in issues})
        backend.pop('repositoryRoot')
        issues = control.validate_runtime(
            self.program, self.repositories, self.root,
            deferred_repositories=frozenset({'maps'}),
        )
        self.assertIn('NOT_GIT_REPOSITORY', {issue.code for issue in issues})
        (self.root / 'src/backend').rmdir()
        self.assertIn('REPOSITORY_PATH', self.codes(scoped=True))

    def test_shared_git_partitions_dirty_scope_and_keeps_unowned_root_paths(self):
        backend = self.shared_repositories()
        repositories = self.repositories['repositories']
        output = '?? program/cards/card.md\0?? src/backend/src/a.java\0?? outside.txt\0'
        self.assertEqual(
            ['program/cards/card.md', 'outside.txt'],
            control.module_dirty_paths(output, repositories[0], repositories, self.root),
        )
        self.assertEqual(
            ['src/a.java'],
            control.module_dirty_paths(output, backend, repositories, self.root),
        )
        card = self.program['cards'][0]
        card['repositories'].append('backend')
        card['allowedDirtyPaths']['backend'] = ['src/']
        def git_result(path, *args):
            if '--is-inside-work-tree' in args:
                return 0, 'true'
            if '--show-toplevel' in args:
                return 0, str(self.root)
            if 'status' in args:
                return 0, output
            return 0, 'test-version'
        with mock.patch.object(control, 'git_output', side_effect=git_result):
            issues = control.validate_runtime(
                self.program, self.repositories, self.root,
                deferred_repositories=frozenset({'maps'}),
            )
        dirty = [issue.message for issue in issues if issue.code == 'UNSCOPED_DIRTY_PATH']
        self.assertEqual(['coordination: alteração fora do card ativo: outside.txt'], dirty)
        card['repositories'].remove('backend')
        with mock.patch.object(control, 'git_output', side_effect=git_result):
            issues = control.validate_runtime(
                self.program, self.repositories, self.root,
                deferred_repositories=frozenset({'maps'}),
            )
        self.assertTrue(any(issue.code == 'UNSCOPED_DIRTY_PATH' and 'backend:' in issue.message for issue in issues))

    def test_shared_layout_rejects_escape_mismatch_and_null_before_git(self):
        backend = self.shared_repositories()
        for change in [
            {'repositoryRoot': '../escape'}, {'moduleRoot': '/tmp/outside'},
            {'moduleRoot': None}, {'path': 'different'},
            {'repositoryRoot': 'another-git-root'},
        ]:
            original = copy.deepcopy(backend)
            with self.subTest(change=change):
                backend.update(change)
                self.assertIn('REPOSITORY_LAYOUT', self.codes(scoped=True))
                with mock.patch.object(control, 'git_output') as git:
                    issues = control.validate_runtime(self.program, {'repositories': [backend]}, self.root)
                git.assert_not_called()
                self.assertEqual(['REPOSITORY_LAYOUT'], [issue.code for issue in issues])
            backend.clear()
            backend.update(original)

    def test_nul_status_keeps_unicode_spaces_and_both_sides_of_rename(self):
        output = '?? docs/ação do grupo.md\0R  src/backend/new.java\0outside.java\0'
        self.assertEqual(
            ['docs/ação do grupo.md', 'src/backend/new.java', 'outside.java'],
            control.parse_dirty_paths(output),
        )
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        path = self.root / 'ação do grupo.txt'
        path.write_text('fixture')
        _, status = control.git_output(self.root, 'status', '--porcelain=v1', '-z', '--untracked-files=all')
        self.assertIn('ação do grupo.txt', control.parse_dirty_paths(status))

    def test_portable_harness_bootstraps_without_private_root_config(self):
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        self.program['cards'][0]['allowedDirtyPaths']['coordination'].extend(
            ['workspaces/', '.dev-harness/status.json'],
        )
        write_control_fixture(self.root, self.program, self.repositories)
        harness = {
            'repos': [{'name': repo['id'], 'path': repo['path'], 'commands': [repo['cardGate'], repo['ciGate']]}
                      for repo in self.repositories['repositories']],
            'agent_files': ['AGENTS.md', 'program/README.md', 'maps/AGENTS.md'],
        }
        (self.root / 'workspaces/harness.json').write_text(json.dumps(harness))
        control.initialize_local_status(self.root)
        self.assertFalse((self.root / '.dev-harness/harness.json').exists())
        issues, _, _ = control.collect_issues(self.root, check_dashboard=False, workspace='core')
        self.assertFalse(any(issue.level == 'ERROR' for issue in issues), issues)


if __name__ == '__main__':
    unittest.main()

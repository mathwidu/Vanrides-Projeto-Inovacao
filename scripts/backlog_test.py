import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('backlog', Path(__file__).with_name('backlog.py'))
backlog = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backlog)


class BacklogIdentityTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.previous = backlog.ROOT
        backlog.ROOT = self.root
        (self.root/'docs/backlog').mkdir(parents=True)
        self.data = {
            'tarefas.json': {'tasks': [['T01', 1, 'F09', 'Backend', 'Cadastro', 'P', '', 25]]},
            'acompanhamento.json': {'tasks': [{'taskId': 'T01'}]},
            'criterios.json': {'tasks': [{'taskId': 'T01', 'acceptanceCriteria': ['Dado válido é registrado.']}]},
        }

    def tearDown(self):
        backlog.ROOT = self.previous
        self.tmp.cleanup()

    def write(self):
        for name, data in self.data.items():
            (self.root/'docs/backlog'/name).write_text(json.dumps(data, ensure_ascii=False), encoding='utf-8')

    def test_same_task_can_be_loaded_once_from_each_source(self):
        self.write()
        _, tasks, progress, criteria = backlog.load()
        self.assertEqual(set(tasks), set(progress))
        self.assertEqual(set(tasks), set(criteria))

    def test_rejects_duplicate_ids_in_each_source_before_collapsing(self):
        for name in self.data:
            with self.subTest(source=name):
                self.data[name]['tasks'].append(copy.deepcopy(self.data[name]['tasks'][0]))
                self.write()
                with self.assertRaisesRegex(AssertionError, 'duplicados'):
                    backlog.load()
                self.data[name]['tasks'].pop()

    def test_rejects_missing_task_in_criteria(self):
        self.data['criterios.json']['tasks'] = []
        self.write()
        with self.assertRaises(AssertionError):
            backlog.load()

    def test_rejects_two_tasks_claiming_the_same_issue(self):
        task = copy.deepcopy(self.data['tarefas.json']['tasks'][0])
        task[0] = 'T02'
        self.data['tarefas.json']['tasks'].append(task)
        self.data['acompanhamento.json']['tasks'].append({'taskId': 'T02'})
        self.data['criterios.json']['tasks'].append({'taskId': 'T02', 'acceptanceCriteria': ['Outro dado.']})
        self.write()
        with self.assertRaisesRegex(AssertionError, 'duas tarefas'):
            backlog.load()


if __name__ == '__main__':
    unittest.main()

"""Failure evidence excludes messages and data, including errors raised during cleanup."""
import contextlib
import io
import json
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import control


class FailureDiagnosticTest(unittest.TestCase):
    def test_main_preserves_failure_and_bounded_exception_chain_without_messages(self):
        marker = 'SYNTHETIC_PRIVATE_PAYLOAD_NOT_FOR_OUTPUT'
        def fail(_value):
            try:
                raise json.JSONDecodeError(marker, marker, 0)
            except ValueError:
                raise BrokenPipeError(marker)
        output = io.StringIO()
        value = SimpleNamespace(startup_stage='CHECK_API')
        with patch.object(control, 'runtime', return_value=value), patch.object(control, 'serve', fail), \
                patch('sys.argv', ['control.py', 'serve', '--attempt', 'fixture']), contextlib.redirect_stdout(output):
            self.assertEqual(2, control.main())
        result = json.loads(output.getvalue())
        self.assertEqual(('BLOCKED', 'LOCAL_VALIDATION_ERROR'), (result['status'], result['code']))
        self.assertEqual('CHECK_API', result['diagnostic']['stage'])
        self.assertEqual(['BrokenPipeError', 'JSONDecodeError'],
                         [item['kind'] for item in result['diagnostic']['exceptions']])
        self.assertNotIn(marker, output.getvalue())
        self.assertEqual({'file', 'function', 'line'}, set(result['diagnostic']['exceptions'][0]['frames'][0]))
        self.assertEqual('main', result['diagnostic']['exceptions'][0]['frames'][0]['function'])

    def test_unknown_error_stage_and_foreign_file_are_not_copied(self):
        marker = 'SYNTHETIC_PRIVATE_PATH_OR_STAGE'
        error_type = type(marker, (ValueError,), {})
        try:
            raise error_type(marker)
        except ValueError as error:
            result = control.failure_diagnostic(error, SimpleNamespace(startup_stage=marker))
        self.assertEqual({'stage': 'UNAVAILABLE', 'exceptions': [
            {'kind': 'OTHER_CAUGHT_ERROR', 'frames': []}]}, result)
        self.assertNotIn(marker, json.dumps(result))

    def test_recursive_trace_is_bounded_without_local_values(self):
        namespace = {}
        code = compile('def recurse(n):\n if n: return recurse(n-1)\n raise ValueError("PRIVATE")\n',
                       str(control.HERE / 'control.py'), 'exec')
        exec(code, namespace)
        try:
            namespace['recurse'](12)
        except ValueError as error:
            result = control.failure_diagnostic(error, None)
        self.assertEqual(1, len(result['exceptions']))
        self.assertEqual(4, len(result['exceptions'][0]['frames']))
        self.assertTrue(all(row['file'] == 'control.py' and row['function'] == 'recurse'
                            and 1 <= row['line'] <= 3 for row in result['exceptions'][0]['frames']))
        self.assertNotIn('PRIVATE', json.dumps(result))

    def test_existing_enumerated_failure_code_is_preserved(self):
        output = io.StringIO()
        with patch.object(control, 'runtime', side_effect=control.Blocked('PIN_MISMATCH')), \
                patch('sys.argv', ['control.py', 'serve', '--attempt', 'fixture']), contextlib.redirect_stdout(output):
            self.assertEqual(2, control.main())
        self.assertEqual('PIN_MISMATCH', json.loads(output.getvalue())['code'])


if __name__ == '__main__':
    unittest.main()

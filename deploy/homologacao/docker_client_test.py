import os
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import prepare as P
from docker_client import local_docker


class PinnedEndpointTests(unittest.TestCase):
    def test_context_change_after_preflight_cannot_redirect_any_command(self):
        calls = []
        current = 'unix:///local-verified.sock'
        def execute(args, **kwargs):
            calls.append(args)
            if args[:3] == ['docker', 'context', 'inspect']:
                return SimpleNamespace(returncode=0, stdout=P.encode({'Host': current}))
            self.assertEqual(args[:3], ['docker', '--host', 'unix:///local-verified.sock'])
            return SimpleNamespace(returncode=0, stdout=b'')
        with patch.dict(os.environ, {'DOCKER_HOST': '', 'DOCKER_CONTEXT': ''}):
            run = local_docker(execute)
            current = 'ssh://a-remote-daemon'
            for operation in (['image','inspect'], ['create'], ['ps','-a'], ['inspect'], ['exec'], ['rm','-f','-v']):
                run(operation)
        self.assertEqual(sum(args[:3] == ['docker','context','inspect'] for args in calls), 1)

    def test_remote_env_or_context_and_ambiguous_unix_urls_are_denied(self):
        for host in ('ssh://remote', 'tcp://127.0.0.1:2375', 'unix://remote/socket', 'unix:///socket?redirect=remote'):
            def execute(args, **kwargs):
                return SimpleNamespace(returncode=0, stdout=P.encode({'Host': host}))
            with patch.dict(os.environ, {'DOCKER_HOST': '', 'DOCKER_CONTEXT': ''}), self.subTest(host=host), self.assertRaises(P.Blocked):
                local_docker(execute)
        with patch.dict(os.environ, {'DOCKER_HOST': 'ssh://remote'}), self.assertRaises(P.Blocked):
            local_docker(lambda *a, **k: self.fail('must reject before a command'))


if __name__ == '__main__':
    unittest.main()

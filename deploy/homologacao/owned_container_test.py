"""Lost responses, SIGTERM and ownership collisions, without Docker."""
import os
import signal
import unittest

import prepare as P
from owned_container import OwnedContainer, OWNER, OPERATION, Terminated

IMAGE = 'postgres@sha256:' + '1' * 64
CID = 'a' * 64


class OwnerTests(unittest.TestCase):
    def fake(self, failure=None, forged=False, collision=False):
        state, calls = {}, []
        def run(args):
            calls.append(args)
            if args[0] == 'ps':
                if any(part.startswith('name=') for part in args):
                    return CID.encode() if collision else b''
                return (CID + '\n').encode() if state else b''
            if args[0] == 'create':
                state.update(id=CID, name='/' + args[args.index('--name') + 1], image=args[-1],
                    labels=dict(args[i+1].split('=',1) for i,part in enumerate(args) if part == '--label'))
                if forged:
                    state['labels'][OWNER] = 'different-owner'
                if failure == 'response':
                    raise P.Blocked('UNIT_RESPONSE_LOST_AFTER_CREATE')
                if failure == 'sigterm':
                    os.kill(os.getpid(), signal.SIGTERM)
                if failure == 'interrupt':
                    raise KeyboardInterrupt()
                return CID.encode()
            if args[0] == 'inspect':
                return P.encode(state)
            if args[0] == 'rm':
                self.assertEqual(args[-1], CID)
                state.clear()
                return b''
            raise AssertionError(args)
        return run, state, calls

    def test_success_cleanup_checks_nonce_name_image_and_removes_only_its_container(self):
        run, state, calls = self.fake()
        with OwnedContainer(run, 'restore', IMAGE) as owned:
            self.assertEqual(owned.create(['--network', 'none']), CID)
        self.assertFalse(state)
        self.assertIn(['rm', '-f', '-v', CID], calls)
        self.assertTrue(any(arg.startswith('label=' + OWNER) for row in calls for arg in row))

    def test_lost_response_sigterm_and_keyboard_interrupt_cleanup_without_id_response(self):
        previous = signal.getsignal(signal.SIGTERM)
        for failure, exception in (('response', P.Blocked), ('sigterm', Terminated), ('interrupt', KeyboardInterrupt)):
            run, state, calls = self.fake(failure)
            with self.subTest(failure=failure), self.assertRaises(exception):
                with OwnedContainer(run, 'image-check', IMAGE) as owned:
                    owned.create(['--network', 'none'])
            self.assertFalse(state)
            self.assertIn(['rm', '-f', '-v', CID], calls)
            self.assertEqual(signal.getsignal(signal.SIGTERM), previous)

    def test_name_collision_never_creates_or_removes(self):
        run, state, calls = self.fake(collision=True)
        with self.assertRaises(P.Blocked):
            with OwnedContainer(run, 'restore', IMAGE) as owned:
                owned.create([])
        self.assertFalse(any(row[0] in ('create', 'rm') for row in calls))

    def test_forged_owner_on_lost_response_is_never_removed(self):
        run, state, calls = self.fake('response', forged=True)
        with self.assertRaises(P.Blocked):
            with OwnedContainer(run, 'restore', IMAGE) as owned:
                owned.create([])
        self.assertTrue(state)
        self.assertFalse(any(row[0] == 'rm' for row in calls))


if __name__ == '__main__':
    unittest.main()

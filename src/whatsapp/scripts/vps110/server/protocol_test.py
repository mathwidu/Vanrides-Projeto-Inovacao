import copy
import datetime as dt
import json
from pathlib import Path
import unittest
import protocol


class ContractTest(unittest.TestCase):
    def setUp(self):
        self.contract = json.loads((Path(__file__).parent / 'contract.json').read_text())
        self.binding = protocol.context('dry-run-a1', 'A' * 43, self.contract)
        self.auth = {'bridgeToken': 'B' * 43, 'expiresAt': '2099-01-01T00:00:00Z'}
        self.headers = {'x-wa110-attempt': 'dry-run-a1', 'x-wa110-source-sha': self.binding['sourceSha'],
                        'x-wa110-nonce': self.binding['nonce'], 'x-wa110-bridge-token': self.auth['bridgeToken']}

    def test_exact_context_and_phase_order(self):
        protocol.check_headers(self.headers, self.binding, self.auth)
        for phase, route in enumerate(['/pair', '/intent', '/started', '/stopped']):
            protocol.phase_body(route, {'code': 'BCDFGHJK'} if phase == 0 else {}, phase)

    def test_foreign_attempt_source_nonce_token_and_expiry_rejected(self):
        for key in self.headers:
            headers = {**self.headers, key: 'foreign'}
            with self.assertRaises(protocol.Blocked):
                protocol.check_headers(headers, self.binding, self.auth)
        with self.assertRaises(protocol.Blocked):
            protocol.check_headers(self.headers, self.binding, self.auth, dt.datetime(2100, 1, 1, tzinfo=dt.timezone.utc))

    def test_no_uri_gps_sql_or_reordered_phase_from_mac(self):
        for route, value, phase in [('/intent', {}, 0), ('/pair', {'code': '12345678'}, 0),
                                    ('/started', {'latitude': 1}, 2), ('/stopped', {'sql': 'DELETE'}, 3),
                                    ('/intent', {'uri': 'https://foreign'}, 1)]:
            with self.assertRaises(protocol.Blocked):
                protocol.phase_body(route, value, phase)


if __name__ == '__main__':
    unittest.main()

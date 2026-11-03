import unittest
import uuid
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

from testar_api import CheckFailed, ENVIRONMENT, HOST, check_destination, request, run_scenario


class ScenarioTest(unittest.TestCase):
    def setUp(self):
        self.event = str(uuid.uuid4())
        self.token = "A" * 43
        self.receipt = {"commandId": str(uuid.uuid4()), "entityVersion": 1,
                        "responseIds": [str(uuid.uuid4())], "outcome": "APPLIED"}
        self.item = {"inboxEventId": self.event, "inboxLease": "B" * 43,
                     "leaseExpiresAt": datetime.fromtimestamp(160, timezone.utc).isoformat()}
        self.calls = []

    def response(self, method, path, body=None, headers=None, timeout=7):
        self.calls.append((method, path, body, dict(headers or {})))
        if path == "/actuator/health":
            return 200, {"status": "UP"}, ENVIRONMENT
        if path.endswith("/claim"):
            if not headers:
                return 401, {}, ENVIRONMENT
            return 200, {"items": [self.item]}, ENVIRONMENT
        self.assertEqual(headers["X-Inbox-Lease"], self.item["inboxLease"])
        self.assertEqual(headers["Authorization"], "Bearer " + self.token)
        if "/context?" in path:
            return 200, {"entityVersion": 0, "inboxEventId": self.event,
                         "allowedCommands": ["START_ONBOARDING"]}, ENVIRONMENT
        if path.endswith("/commands"):
            return 200, self.receipt.copy(), ENVIRONMENT
        self.assertEqual(body, {"outcome": "COMPLETED"})
        return 200, {"state": "PROCESSED", "inboxEventId": self.event}, ENVIRONMENT

    def test_replay_uses_same_key_body_and_lease_before_ack(self):
        check_destination(self.response)
        run_scenario(self.response, self.token, self.event, now=lambda: 100)
        commands = [c for c in self.calls if c[1].endswith("/commands")]
        self.assertEqual(len(commands), 2)
        self.assertEqual(commands[0], commands[1])
        self.assertEqual(commands[0][2]["commandType"], "START_ONBOARDING")
        self.assertTrue(self.calls[-1][1].endswith("/result"))
        self.assertNotIn("Authorization", self.calls[0][3])
        self.assertNotIn("Authorization", self.calls[1][3])

    def test_health_without_environment_marker_stops_before_claim(self):
        send = MagicMock(return_value=(200, {"status": "UP"}, None))
        with self.assertRaises(CheckFailed):
            check_destination(send)
        self.assertEqual(send.call_count, 1)

    def test_other_fixture_stops_before_context_or_command(self):
        with self.assertRaisesRegex(CheckFailed, "outro evento"):
            run_scenario(self.response, self.token, str(uuid.uuid4()), now=lambda: 100)
        self.assertEqual(len(self.calls), 1)

    def test_expired_lease_does_not_send_command(self):
        with self.assertRaisesRegex(CheckFailed, "expirou"):
            run_scenario(self.response, self.token, self.event, now=lambda: 160)
        self.assertFalse(any(c[1].endswith("/commands") for c in self.calls))

    def test_conflicting_replay_never_acknowledges(self):
        def conflict(*args):
            if args[1].endswith("/commands") and any(c[1] == args[1] for c in self.calls):
                self.receipt["commandId"] = str(uuid.uuid4())
            return self.response(*args)
        with self.assertRaisesRegex(CheckFailed, "recibo original"):
            run_scenario(conflict, self.token, self.event, now=lambda: 100)
        self.assertFalse(any(c[1].endswith("/result") for c in self.calls))

    @patch("testar_api.http.client.HTTPSConnection")
    def test_redirect_never_forwards_bearer(self, connection):
        response = connection.return_value.getresponse.return_value
        response.status = 307
        with self.assertRaisesRegex(CheckFailed, "redirecionou"):
            request("GET", "/only-test", headers={"Authorization": "Bearer " + self.token})
        self.assertEqual(connection.call_count, 1)
        self.assertEqual(connection.call_args.args[0], HOST)
        self.assertTrue(connection.call_args.kwargs["context"].check_hostname)
        connection.return_value.request.assert_called_once()
        connection.return_value.close.assert_called_once()


if __name__ == "__main__":
    unittest.main()

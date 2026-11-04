import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from ensaio_local import CheckFailed, database, source_check, utc


class UtcTest(unittest.TestCase):
    def assert_future_utc(self, seconds, expected):
        now = datetime(2030, 1, 1, 23, 50, 0, 123456, tzinfo=timezone.utc)
        with patch("ensaio_local.datetime") as clock:
            clock.now.return_value = now
            value = utc(seconds)
            clock.now.assert_called_once_with(timezone.utc)
        # WaOperatorRequest requires canonical Z for both request fields.
        self.assertTrue(value.endswith("Z"), "O prazo da CLI deve terminar em Z")
        self.assertEqual(value, expected)
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        self.assertEqual(parsed.utcoffset(), timedelta(0))
        self.assertGreater(parsed, now)
        self.assertEqual(parsed - now, timedelta(seconds=seconds))

    def test_credential_expires_at_is_utc_z_with_its_future_offset(self):
        self.assert_future_utc(1800, "2030-01-02T00:20:00.123456Z")

    def test_fixture_lease_expires_at_is_utc_z_with_its_future_offset(self):
        self.assert_future_utc(45, "2030-01-01T23:50:45.123456Z")


class OwnershipTest(unittest.TestCase):
    def test_remote_docker_override_fails_before_any_command(self):
        with tempfile.TemporaryDirectory() as temp:
            with patch.dict(os.environ, {"DOCKER_HOST": "ssh://example.invalid"}), patch("ensaio_local.output") as run:
                with self.assertRaisesRegex(CheckFailed, "Override Docker"):
                    with database(Path(temp)):
                        self.fail("Banco externo utilizado")
                run.assert_not_called()

    def test_reassigned_container_is_never_removed(self):
        cid = "f" * 64
        with tempfile.TemporaryDirectory() as temp:
            work = Path(temp)
            def fake(*args):
                if args[1:3] == ("context", "show"):
                    return "test"
                if args[1:3] == ("context", "inspect"):
                    return "unix:///unit-test.sock"
                if "run" in args:
                    (work / "pg.cid").write_text(cid)
                    return cid
                if "ps" in args:
                    return cid
                if "inspect" in args:
                    return "outro-owner"
                self.fail("Comando inesperado: " + str(args))
            with patch.dict(os.environ, {}, clear=True), patch("ensaio_local.output", side_effect=fake) as run:
                with self.assertRaisesRegex(CheckFailed, "identificação"):
                    with database(work):
                        self.fail("Banco de outro proprietário utilizado")
                self.assertFalse(any("rm" in c.args for c in run.call_args_list))

    def test_substituted_jar_fails_before_startup(self):
        with tempfile.TemporaryDirectory() as temp, patch("ensaio_local.output", side_effect=["a" * 40, ""]):
            jar = Path(temp) / "backend.jar"
            jar.write_bytes(b"binario substituido")
            with self.assertRaisesRegex(CheckFailed, "JAR divergente"):
                source_check(jar, "a" * 40, "0" * 64)


if __name__ == "__main__":
    unittest.main()

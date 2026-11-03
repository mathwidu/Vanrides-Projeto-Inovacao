#!/usr/bin/env python3
from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPTS_DIR = ROOT / "scripts"
FIXTURE_SCRIPTS = (
    SCRIPTS_DIR / "seed_operational_service_day_fixture.sh",
    SCRIPTS_DIR / "seed_geographic_route_fixture.sh",
    SCRIPTS_DIR / "seed_igrejinha_route_lab.sh",
)
EXPECTED_EMAIL_TEMPLATE_COUNTS = {
    "seed_operational_service_day_fixture.sh": 6,
    "seed_geographic_route_fixture.sh": 3,
    "seed_igrejinha_route_lab.sh": 4,
}
SMOKE_SCRIPT = SCRIPTS_DIR / "qa_admin_portal_smoke.sh"
LOCATION_READ_SMOKE_SCRIPT = SCRIPTS_DIR / "qa_location_read_smoke.sh"
EMAIL_NAMESPACE_SMOKE_SCRIPT = SCRIPTS_DIR / "qa_email_namespace_smoke.sh"
EMAIL_CONFIRMATION_INTENT_SMOKE_SCRIPT = (
    SCRIPTS_DIR / "qa_email_confirmation_intent_smoke.sh"
)
PASSENGER_DELETE_SMOKE_SCRIPT = SCRIPTS_DIR / "qa_passenger_tenant_delete_smoke.sh"
EXPECTED_LOCAL_CONSUMERS = {
    *(script.name for script in FIXTURE_SCRIPTS),
    SMOKE_SCRIPT.name,
    LOCATION_READ_SMOKE_SCRIPT.name,
    EMAIL_NAMESPACE_SMOKE_SCRIPT.name,
    EMAIL_CONFIRMATION_INTENT_SMOKE_SCRIPT.name,
    PASSENGER_DELETE_SMOKE_SCRIPT.name,
}


def read_script(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def bootstrap_payload(source: str, path: Path) -> str:
    match = re.search(
        r'request POST "/company-admins/bootstrap" none "\{(?P<body>.*?)\n\s*\}"',
        source,
        flags=re.S,
    )
    if match is None:
        raise AssertionError(f"bootstrap payload not found in {path.name}")
    return match.group("body")


class BootstrapFixtureContractTest(unittest.TestCase):
    def test_inventory_covers_every_local_shell_consumer(self) -> None:
        actual = {
            path.name
            for path in SCRIPTS_DIR.glob("*.sh")
            if "/company-admins/bootstrap" in read_script(path)
        }

        self.assertEqual(EXPECTED_LOCAL_CONSUMERS, actual)

    def test_fixtures_send_unique_company_name_and_never_company_slug(self) -> None:
        for script in FIXTURE_SCRIPTS:
            with self.subTest(script=script.name):
                source = read_script(script)
                payload = bootstrap_payload(source, script)

                self.assertIn('RUN_ID="${TS}-$$"', source)
                self.assertIsNotNone(
                    re.search(r'^COMPANY_NAME=.*\$\{RUN_ID\}.*$', source, flags=re.M)
                )
                self.assertIn(r'\"companyName\":\"${COMPANY_NAME}\"', payload)
                self.assertNotIn("companySlug", payload)

    def test_fixtures_register_only_the_slug_returned_by_backend(self) -> None:
        response_assignment = (
            'COMPANY_SLUG="$(printf \'%s\' "$bootstrap_response" '
            '| json_get "companySlug")"'
        )

        for script in FIXTURE_SCRIPTS:
            with self.subTest(script=script.name):
                source = read_script(script)

                self.assertNotIn("slugify", source)
                self.assertEqual(1, source.count("COMPANY_SLUG="))
                self.assertIn(response_assignment, source)
                self.assertGreater(
                    source.index(response_assignment),
                    source.index("bootstrap_response="),
                )

    def test_fixture_identity_emails_are_unique_per_run(self) -> None:
        for script in FIXTURE_SCRIPTS:
            with self.subTest(script=script.name):
                source = read_script(script)
                email_templates = [
                    line.strip()
                    for line in source.splitlines()
                    if "@vanrides.dev" in line
                ]

                self.assertEqual(
                    EXPECTED_EMAIL_TEMPLATE_COUNTS[script.name],
                    len(email_templates),
                )
                for template in email_templates:
                    self.assertIn("${RUN_ID}", template)
                    self.assertNotIn("${SHORT_TS}", template)
                    self.assertNotIn("${TS}", template)

    def test_admin_smoke_rejects_legacy_slug_selector(self) -> None:
        source = read_script(SMOKE_SCRIPT)
        probe = re.search(
            r'assert_status "(?P<status>\d+)" '
            r'"POST /company-admins/bootstrap com companySlug legado.*?\n\n',
            source,
            flags=re.S,
        )
        self.assertIsNotNone(probe)
        assert probe is not None

        self.assertEqual("400", probe.group("status"))
        self.assertIn(
            'LEGACY_BOOTSTRAP_SLUG="legacy-bootstrap-must-fail-${SMOKE_RUN_ID}"',
            source,
        )
        for required_field in ("nome", "email", "telefone", "senha", "companySlug"):
            self.assertIn(rf'\"{required_field}\"', probe.group(0))
        self.assertIn(
            r'\"companySlug\":\"${LEGACY_BOOTSTRAP_SLUG}\"',
            probe.group(0),
        )
        self.assertNotIn("companyName", probe.group(0))

    def test_location_read_smoke_covers_positive_and_legacy_bootstrap_contracts(self) -> None:
        source = read_script(LOCATION_READ_SMOKE_SCRIPT)

        self.assertIn('DUPLICATE_PAYLOAD="{\\"companyName\\"', source)
        self.assertIn('COMPANY2_PAYLOAD="{\\"companyName\\"', source)
        self.assertIn('SLUG_ONLY_PAYLOAD="{\\"companySlug\\"', source)
        self.assertIn("assert_equal \"$STATUS\" 400 bootstrap_slug_only_status", source)
        self.assertIn("assert_equal \"$STATUS\" 409 bootstrap_duplicate_company_status", source)


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
from pathlib import Path
import tempfile
import unittest

import quality_metrics


class BackendQualityRatchetTest(unittest.TestCase):
    def test_fails_when_protected_metric_increases(self):
        baseline = {
            "metrics": {
                "system_print_count": 0,
                "mockbean_count": 10,
            },
            "files": {},
        }
        current = {
            "metrics": {
                "system_print_count": 1,
                "mockbean_count": 10,
            },
            "files": {},
        }

        failures = quality_metrics.evaluate_ratchet(
            baseline, current, quality_metrics.BACKEND_RULES
        )

        self.assertTrue(any("system_print_count" in failure for failure in failures))

    def test_fails_when_new_java_file_exceeds_limit(self):
        baseline = {"metrics": {}, "files": {}}
        current = {
            "metrics": {},
            "files": {"src/main/java/com/vanrides/vanrides/service/HugeService.java": 351},
        }

        failures = quality_metrics.evaluate_ratchet(
            baseline, current, quality_metrics.BACKEND_RULES
        )

        self.assertTrue(any("HugeService.java" in failure for failure in failures))

    def test_existing_large_file_can_remain_at_baseline_size(self):
        path = "src/main/java/com/vanrides/vanrides/service/PassengerOnboardingService.java"
        baseline = {"metrics": {}, "files": {path: 507}}
        current = {"metrics": {}, "files": {path: 507}}

        failures = quality_metrics.evaluate_ratchet(
            baseline, current, quality_metrics.BACKEND_RULES
        )

        self.assertEqual([], failures)

    def test_scan_detects_stale_completed_quality_cards(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            backlog = root / "backlog" / "SpringBoot-Cards.md"
            backlog.parent.mkdir(parents=True)
            backlog.write_text(
                """
## Backlog 0016 — QUALITY-0016: exemplo stale

### Status
IN_PROGRESS

### DoD
- Card marcado como DONE (commit: abc1234)

## Backlog 0017 — QUALITY-0017: ainda aberto

### Status
IN_PROGRESS

### DoD
- Card ainda pendente
""",
                encoding="utf-8",
            )

            result = quality_metrics.scan(root)

        self.assertEqual(1, result["metrics"]["stale_completed_card_count"])
        self.assertEqual(
            ["QUALITY-0016"],
            result["details"]["stale_completed_cards"],
        )

    def test_scan_reports_productive_java_files_over_limit(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            huge_service = (
                root
                / "src/main/java/com/vanrides/vanrides/service/HugeService.java"
            )
            huge_service.parent.mkdir(parents=True)
            huge_service.write_text("\n".join(["class HugeService {}"] * 351))
            huge_test = root / "src/test/java/com/vanrides/vanrides/HugeTest.java"
            huge_test.parent.mkdir(parents=True)
            huge_test.write_text("\n".join(["class HugeTest {}"] * 600))

            result = quality_metrics.scan(root)

        self.assertEqual(1, result["metrics"]["productive_java_files_over_limit_count"])
        self.assertEqual(
            [
                {
                    "lines": 351,
                    "path": "src/main/java/com/vanrides/vanrides/service/HugeService.java",
                }
            ],
            result["details"]["productive_java_files_over_limit"],
        )

    def test_scan_reports_testcontainers_skips_from_surefire_reports(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            report = root / "target" / "surefire-reports" / "TEST-it.xml"
            report.parent.mkdir(parents=True)
            report.write_text(
                '<testsuite name="RepositoryIntegrationTest" tests="3" skipped="2" />',
                encoding="utf-8",
            )

            result = quality_metrics.scan(root)

        self.assertEqual(2, result["metrics"]["testcontainers_skipped_count"])
        self.assertEqual(
            [{"path": "target/surefire-reports/TEST-it.xml", "skipped": 2}],
            result["details"]["testcontainers_skipped_reports"],
        )

    def test_scan_reports_mockbean_files(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            webmvc_test = (
                root
                / "src/test/java/com/vanrides/vanrides/controller/DashboardWebMvcTest.java"
            )
            webmvc_test.parent.mkdir(parents=True)
            webmvc_test.write_text(
                """
import org.springframework.boot.test.mock.mockito.MockBean;

class DashboardWebMvcTest {
    @MockBean
    Object service;

    @MockBean
    Object repository;
}
""",
                encoding="utf-8",
            )

            result = quality_metrics.scan(root)

        self.assertEqual(2, result["metrics"]["mockbean_count"])
        self.assertEqual(
            [
                {
                    "count": 2,
                    "path": "src/test/java/com/vanrides/vanrides/controller/DashboardWebMvcTest.java",
                }
            ],
            result["details"]["mockbean_files"],
        )

    def test_warns_while_mockbean_debt_remains(self):
        current = {
            "metrics": {"mockbean_count": 2},
            "details": {"mockbean_files": [{"path": "A.java", "count": 2}]},
        }

        warnings = quality_metrics.evaluate_warnings(current)

        self.assertTrue(any("mockbean_count remains 2" in warning for warning in warnings))


if __name__ == "__main__":
    unittest.main()

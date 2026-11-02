#!/usr/bin/env python3
import tempfile
import unittest
from pathlib import Path

import quality_metrics


class AppQualityRatchetTest(unittest.TestCase):
    def test_fails_when_protected_metric_increases(self):
        baseline = {
            "metrics": {
                "set_state_count": 1,
                "debug_print_count": 0,
                "analyzer_ignore_count": 0,
                "shared_preferences_violation_count": 0,
                "root_screen_count": 0,
            },
            "files": {},
        }
        current = {
            "metrics": {
                "set_state_count": 2,
                "debug_print_count": 0,
                "analyzer_ignore_count": 0,
                "shared_preferences_violation_count": 0,
                "root_screen_count": 0,
            },
            "files": {},
        }

        failures = quality_metrics.evaluate_ratchet(
            baseline, current, quality_metrics.APP_RULES
        )

        self.assertTrue(any("set_state_count" in failure for failure in failures))

    def test_fails_when_new_dart_file_exceeds_limit(self):
        baseline = {"metrics": {}, "files": {}}
        current = {"metrics": {}, "files": {"lib/features/new/huge.dart": 601}}

        failures = quality_metrics.evaluate_ratchet(
            baseline, current, quality_metrics.APP_RULES
        )

        self.assertTrue(any("huge.dart" in failure for failure in failures))

    def test_existing_large_file_can_remain_at_baseline_size(self):
        baseline = {"metrics": {}, "files": {"lib/home_motorista_screen.dart": 3655}}
        current = {"metrics": {}, "files": {"lib/home_motorista_screen.dart": 3655}}

        failures = quality_metrics.evaluate_ratchet(
            baseline, current, quality_metrics.APP_RULES
        )

        self.assertEqual([], failures)

    def test_scan_reports_stale_completed_quality_cards(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            backlog = root / "backlog" / "Flutter-Cards.md"
            backlog.parent.mkdir(parents=True)
            backlog.write_text(
                """
## Backlog 0999 — QUALITY-0099: stale card

### Status
IN_PROGRESS

### DoD
- Card marcado como `DONE (commit: abc1234)`.
""",
                encoding="utf-8",
            )

            result = quality_metrics.scan(root)

        self.assertEqual(1, result["metrics"]["stale_completed_card_count"])
        self.assertEqual(
            ["QUALITY-0099"],
            result["details"]["stale_completed_cards"],
        )

    def test_scan_reports_files_over_limit_and_category_maxima(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            files = {
                "lib/large_production.dart": 601,
                "test/large_widget_test.dart": 602,
                "integration_test/large_e2e.dart": 603,
            }
            for rel_path, line_count in files.items():
                path = root / rel_path
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("x\n" * line_count, encoding="utf-8")

            result = quality_metrics.scan(root)

        self.assertEqual(3, result["metrics"]["dart_files_over_limit_count"])
        self.assertEqual(
            {
                "production": 601,
                "widget_test": 602,
                "integration_test": 603,
            },
            result["details"]["max_file_lines_by_category"],
        )
        self.assertEqual(
            [
                {
                    "category": "integration_test",
                    "lines": 603,
                    "path": "integration_test/large_e2e.dart",
                },
                {
                    "category": "production",
                    "lines": 601,
                    "path": "lib/large_production.dart",
                },
                {
                    "category": "widget_test",
                    "lines": 602,
                    "path": "test/large_widget_test.dart",
                },
            ],
            result["details"]["files_over_limit"],
        )


if __name__ == "__main__":
    unittest.main()

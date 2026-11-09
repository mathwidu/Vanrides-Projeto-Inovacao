#!/usr/bin/env python3
from __future__ import annotations

import copy
import fnmatch
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

import qa_test_groups as groups


class TestGroupsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.postgres, self.plan, _ = groups.plan()
        self.sha, self.tree = "1" * 40, "2" * 40

    def finalized_manifest(self, cases):
        # Final canonical schema: preparedSourceStateSha256 is deliberately absent.
        return {"schemaVersion": 2, "status": "passed", "suite": "integration", "eligibleForRelease": True,
                "archivedAtUtc": "2026-11-05T00:00:00+00:00", "runId": "integration-synthetic",
                "source": {"gitSha": self.sha, "gitTree": self.tree, "runStartGitSha": self.sha,
                           "runStartGitTree": self.tree, "clean": True, "dirtyPathCount": 0, "cleanRequired": True},
                "reports": [{"className": name, "tests": count} for name, count in cases.items()],
                "totals": {"tests": sum(cases.values()), **groups.ZERO}}

    def receipts(self):
        receipts = []
        for group, cases in [*self.plan.items(), ("unit", {"com.example.NewIntegrationTest": 3})]:
            evidence = self.directory / f"{group}-manifest.json"
            evidence.write_text(json.dumps(self.finalized_manifest(cases)))
            receipts.append(groups.compact_receipt(evidence, group, groups.DEFAULT_MANIFEST, "123", "1"))
        return receipts

    def aggregate(self, receipts):
        return groups.aggregate(receipts, groups.DEFAULT_MANIFEST, self.sha, self.tree, "123", "1", True)

    def write_report(self, name, count=3, tag=None):
        root = ET.Element("testsuite", name=name, tests=str(count), skipped="0", failures="0", errors="0")
        for index in range(count):
            case = ET.SubElement(root, "testcase", name=f"template()[{index}]", classname=name)
            if tag and index == 0:
                ET.SubElement(case, tag)
        path = self.directory / f"TEST-{name}.xml"
        path.write_bytes(ET.tostring(root))
        return path

    def test_plan_has_exact_postgres_union_and_deterministic_balanced_groups(self):
        self.assertEqual(78, len(self.postgres))
        self.assertEqual(600, sum(self.postgres.values()))
        self.assertEqual(groups.plan()[1], self.plan)
        union = {}
        for cases in self.plan.values():
            self.assertFalse(set(cases) & set(union))
            union.update(cases)
        self.assertEqual(self.postgres, union)
        rows = groups.read_json(groups.DEFAULT_MANIFEST)["classes"]
        durations = {row["className"]: row["seconds"] for row in rows}
        loads = [sum(durations[name] for name in cases) for cases in self.plan.values()]
        self.assertLess(max(loads) - min(loads), 3)

    def test_manifest_rejects_duplicate_classes_and_invalid_counts_or_weights(self):
        data = groups.read_json(groups.DEFAULT_MANIFEST)
        variants = [copy.deepcopy(data) for _ in range(4)]
        variants[0]["classes"].append(variants[0]["classes"][0])
        variants[1]["classes"][0]["tests"] = 0
        variants[2]["classes"][0]["seconds"] = float("nan")
        variants[3]["groups"] = 7
        for value in variants:
            with self.subTest(value=value["groups"]):
                path = self.directory / "bad.json"; path.write_text(json.dumps(value))
                with self.assertRaises(ValueError):
                    groups.plan(path)

    def test_java_exclusions_are_only_postgres_and_default_inner_class_rule(self):
        result = subprocess.run([sys.executable, str(Path(groups.__file__)), "excludes"], capture_output=True, text=True, check=True)
        patterns = result.stdout.splitlines()
        self.assertEqual(79, len(patterns))
        self.assertEqual("**/*$*", patterns[0])
        for name in self.postgres:
            self.assertIn(name.replace(".", "/") + ".java", patterns)
        for name in ("NewTest", "NewIntegrationTest", "TemplateTests", "TestAdded", "AddedTestCase"):
            self.assertFalse(any(fnmatch.fnmatchcase(f"com/example/{name}.java", pattern) for pattern in patterns))
        self.assertFalse(any("IntegrationTest*" in pattern for pattern in patterns))

    def test_actual_java_inventory_includes_new_classes_and_parameterized_or_factory_cases(self):
        self.write_report("com.example.NewIntegrationTest")
        self.write_report("com.example.DynamicTest", count=7)
        self.assertEqual({"com.example.NewIntegrationTest": 3, "com.example.DynamicTest": 7}, groups.report_inventory(self.directory))
        groups.validate_selection(groups.report_inventory(self.directory), "unit", groups.DEFAULT_MANIFEST)

    def test_report_inventory_rejects_skipped_failed_errors_and_forged_child_totals(self):
        for tag in ("skipped", "failure", "error"):
            path = self.write_report("com.example.BadTest", tag=tag)
            with self.assertRaisesRegex(ValueError, "testcase inventory"):
                groups.report_inventory(self.directory)
            path.unlink()
        path = self.write_report("com.example.BadTest")
        root = ET.fromstring(path.read_bytes()); root.set("skipped", "1"); path.write_bytes(ET.tostring(root))
        with self.assertRaisesRegex(ValueError, "not green"):
            groups.report_inventory(self.directory)

    def test_group_inventory_rejects_missing_unexpected_and_wrong_case_counts(self):
        expected = self.plan["pg-1"]
        variants = [dict(expected), dict(expected), dict(expected)]
        variants[0].pop(next(iter(expected)))
        variants[1]["com.example.OtherTest"] = 1
        variants[2][next(iter(expected))] += 1
        for value in variants:
            with self.assertRaises(ValueError):
                groups.validate_selection(value, "pg-1", groups.DEFAULT_MANIFEST)

    def test_finalized_receipts_and_exact_nine_group_union(self):
        result = self.aggregate(self.receipts())
        self.assertEqual((9, 79, 603, 600, 3), (result["groups"], result["classes"], result["tests"], result["postgresTests"], result["javaOnlyTests"]))
        self.assertNotIn("properties", json.dumps(self.receipts()))

    def test_compact_receipt_refuses_prepared_or_duplicate_canonical_reports(self):
        value = self.finalized_manifest(self.plan["pg-1"])
        path = self.directory / "canonical.json"
        value["status"] = "prepared"; path.write_text(json.dumps(value))
        with self.assertRaises(ValueError):
            groups.compact_receipt(path, "pg-1", groups.DEFAULT_MANIFEST, "123", "1")
        value["status"] = "passed"; value["reports"].append(value["reports"][0]); path.write_text(json.dumps(value))
        with self.assertRaises(ValueError):
            groups.compact_receipt(path, "pg-1", groups.DEFAULT_MANIFEST, "123", "1")

    def test_aggregate_refuses_missing_or_duplicate_groups_and_cross_execution_receipts(self):
        original = self.receipts()
        for receipts in (original[:-1], original + [original[0]], original[:-1] + [original[0]]):
            with self.assertRaises(ValueError):
                self.aggregate(receipts)
        for field, bad in (("sourceSha", "f" * 40), ("sourceTree", "f" * 40), ("manifestSha256", "f" * 64),
                           ("sourceStateSha256", "f" * 64), ("ciRunId", "124"), ("ciAttempt", "2"), ("clean", False)):
            values = copy.deepcopy(original); values[0][field] = bad
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.aggregate(values)

    def test_aggregate_refuses_case_loss_overlap_wrong_totals_skips_and_raw_fields(self):
        for mutate in (lambda receipt: receipt["cases"].pop(next(iter(receipt["cases"]))),
                       lambda receipt: receipt["totals"].update(skipped=1),
                       lambda receipt: receipt.update(rawLog="forbidden")):
            receipts = self.receipts(); mutate(receipts[0])
            with self.assertRaises(ValueError):
                self.aggregate(receipts)
        receipts = self.receipts(); receipts[-1]["cases"] = dict(receipts[0]["cases"])
        receipts[-1]["totals"]["tests"] = sum(receipts[-1]["cases"].values())
        with self.assertRaises(ValueError):
            self.aggregate(receipts)

    def test_json_duplicate_keys_and_symlink_input_fail_closed(self):
        path = self.directory / "bad.json"; path.write_text('{"status":"PASS","status":"FAIL"}')
        with self.assertRaises(ValueError): groups.read_json(path)
        link = self.directory / "link.json"; link.symlink_to(path)
        with self.assertRaises(ValueError): groups.read_json(link)


if __name__ == "__main__":
    unittest.main()

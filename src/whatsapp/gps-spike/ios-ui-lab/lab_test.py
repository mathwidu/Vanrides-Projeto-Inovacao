#!/usr/bin/env python3
"""Host-only checks for privacy gates; no build, signing, or device operations."""
import datetime as dt
import gzip
import hashlib
import json
from pathlib import Path
import plistlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import lab


def run_document():
    return {"__xctestrun_metadata__": {"FormatVersion": 2}, "TestConfigurations": [{
        "Name": "Default", "TestTargets": [{
            "BlueprintName": lab.TARGET, "ProductModuleName": lab.TARGET,
            "TestHostPath": "__TESTROOT__/Release-iphoneos/UITests-Runner.app",
            "TestBundlePath": "__TESTHOST__/PlugIns/UITests.xctest",
            "UITargetAppPath": "/old/Driver.app",
            "DependentProductPaths": ["/old/Driver.app"],
            "EnvironmentVariables": {"OLD_CREDENTIAL": "synthetic-not-a-real-secret"},
            "UITargetAppEnvironmentVariables": {"OLD_INTENT": "synthetic"},
            "TestingEnvironmentVariables": {"DYLD_FRAMEWORK_PATH": "__TESTROOT__/Release-iphoneos"},
        }]
    }]}


class LabTests(unittest.TestCase):
    def test_patch_uses_only_destination_artifacts_and_discards_app_launch_environment(self):
        patched = lab.patch_run(run_document(), "dev.example.uitests.xctrunner", "__TESTHOST__/PlugIns/Lab.xctest")
        target = lab.targets(patched)[0]
        self.assertTrue(target["UseDestinationArtifacts"])
        self.assertEqual(target["UITargetAppBundleIdentifier"], lab.DRIVER_ID)
        self.assertEqual(target["SystemAttachmentLifetime"], "keepNever")
        self.assertEqual(target["UserAttachmentLifetime"], "keepNever")
        self.assertEqual(target["DependentProductPaths"], [])
        self.assertEqual(target["EnvironmentVariables"], {})
        self.assertEqual(target["UITargetAppEnvironmentVariables"], {})
        self.assertNotIn("/old/Driver.app", plistlib.dumps(patched).decode())
        self.assertNotIn("OLD_CREDENTIAL", plistlib.dumps(patched).decode())
        self.assertIn("TestingEnvironmentVariables", target)

    def test_multiple_test_targets_are_rejected(self):
        document = run_document()
        document["TestConfigurations"][0]["TestTargets"].append({"BlueprintName": "Runner"})
        with self.assertRaisesRegex(lab.LabError, "SINGLE_TEST_TARGET"):
            lab.patch_run(document, "unused", "unused")

    def test_inspect_does_not_carry_credential_or_start_intent(self):
        fixture = lab.make_fixture("inspect", {}, False, "public-run-id")
        self.assertNotIn("credential", fixture)
        self.assertNotIn("intent", fixture)
        self.assertFalse(fixture["privacyApproved"])
        for command in ("inspect", "sentinel-pass", "sentinel-fail-intent", "sentinel-fail-credential", "stop"):
            with self.subTest(command=command), self.assertRaisesRegex(lab.LabError, "REFUSES_CREDENTIALS"):
                lab.make_fixture(command, {"credential": "synthetic"}, False, "public-run-id")

    def test_configure_and_start_require_receipt_and_cannot_mix_old_pairs(self):
        configure = {"baseURL": "https://gps-spike.vanrides.matheusduarte.dev.br", "credential": "s" * 40}
        with self.assertRaisesRegex(lab.LabError, "PRIVACY_AUDIT_REQUIRED"):
            lab.make_fixture("configure", configure, False, "public-run-id")
        self.assertNotIn("intent", lab.make_fixture("configure", configure, True, "public-run-id"))
        with self.assertRaisesRegex(lab.LabError, "UNRELATED_VALUES"):
            lab.make_fixture("configure", {**configure, "intent": "old"}, True, "public-run-id")
        with self.assertRaisesRegex(lab.LabError, "UNRELATED_VALUES"):
            lab.make_fixture("start", {"intent": "fresh", "credential": "old"}, True, "public-run-id")
        with self.assertRaisesRegex(lab.LabError, "PRIVACY_AUDIT_REQUIRED"):
            lab.make_fixture("start", {"intent": "fresh"}, False, "public-run-id")

    def test_background_is_bounded_and_requires_no_secret(self):
        self.assertEqual(lab.make_fixture("background", {}, False, "run")["backgroundSeconds"], 30)
        for duration in (0, 181, True, "30"):
            with self.subTest(duration=duration), self.assertRaises(lab.LabError):
                lab.make_fixture("background", {"backgroundSeconds": duration}, False, "run")

    def test_git_output_and_world_readable_inputs_are_rejected(self):
        with self.assertRaisesRegex(lab.LabError, "OUTSIDE_GIT"):
            lab.outside_git(Path(__file__).parent / "private-output")
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "fixture.json"
            path.write_text("{}")
            path.chmod(0o644)
            with self.assertRaisesRegex(lab.LabError, "0600"):
                lab.private_json(path)
            path.chmod(0o600)
            self.assertEqual(lab.private_json(path), {})

    def test_write_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "private.json"
            lab.write_private(path, b"{}")
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            with self.assertRaises(FileExistsError):
                lab.write_private(path, b"overwrite")

    def test_marker_scan_handles_chunk_boundaries_and_compressed_logs(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "action.log"
            path.write_bytes(b"." * (64 * 1024 - 10) + lab.SENTINEL + b"tail")
            self.assertTrue(lab.contains_marker(path))
            compressed = Path(temporary) / "action.xcactivitylog"
            compressed.write_bytes(gzip.compress(path.read_bytes()))
            self.assertTrue(lab.contains_marker(compressed))

    def test_receipt_is_bound_to_source_product_toolchain_and_expiry(self):
        receipt = {"schemaVersion": 1, "approved": True, "sourceDigest": lab.source_digest(),
                   "runnerDigest": "runner", "xcodeVersion": "Xcode test",
                   "approvedAt": dt.datetime.now(dt.timezone.utc).isoformat()}
        lab.verify_receipt(receipt, "runner", "Xcode test")
        for key, value in (("sourceDigest", "other"), ("runnerDigest", "other"),
                           ("xcodeVersion", "other"), ("approved", False),
                           ("approvedAt", (dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=25)).isoformat())):
            with self.subTest(key=key), self.assertRaises(lab.LabError):
                lab.verify_receipt({**receipt, key: value}, "runner", "Xcode test")

    def make_audit_run(self, root, command):
        directory = root / command
        directory.mkdir(mode=0o700)
        document = lab.patch_run(run_document(), "dev.example.uitests.xctrunner", "__TESTHOST__/PlugIns/Lab.xctest")
        payload = plistlib.dumps(document)
        lab.write_private(directory / "run.xctestrun", payload)
        lab.write_private(directory / "run-meta.json", lab.json_bytes({
            "command": command, "runID": "synthetic-run", "sourceDigest": lab.source_digest(),
            "runnerDigest": "runner", "xcodeVersion": "Xcode test",
            "xctestrunDigest": hashlib.sha256(payload).hexdigest(),
        }))
        lab.write_private(directory / "result.json", lab.json_bytes({
            "command": command, "runID": "synthetic-run", "sourceDigest": lab.source_digest(),
            "success": command == "sentinel-pass", "intentSentinelEntered": True,
            "credentialSentinelEntered": command != "sentinel-fail-intent",
            "credentialFieldProtected": command != "sentinel-fail-intent",
            "intentReplacementVerified": command == "sentinel-pass",
            "intentionalFailure": command != "sentinel-pass", "teardownCompleted": True,
            "fixtureRemoved": True, "pasteboardEmptyAtTeardown": True,
            "teardownPasteboardCleanup": {key: True for key in lab.CLEANUP_FLAGS},
            "pasteEvents": [{"cleanup": {key: True for key in lab.CLEANUP_FLAGS}}],
            "code": {"sentinel-fail-intent": "INTENTIONAL_INTENT_FAILURE",
                     "sentinel-fail-credential": "INTENTIONAL_CREDENTIAL_FAILURE"}.get(command),
        }))
        identity = "UI_LAB_RUN_synthetic-run_SOURCE_" + lab.source_digest() + "_COMMAND_" + command
        teardown = identity.replace("UI_LAB_RUN_", "UI_LAB_TEARDOWN_", 1)
        lab.write_private(directory / "xcodebuild.log", (identity + "\n" + teardown).encode())
        (directory / "result.xcresult").mkdir()
        return directory

    def fake_exports(self, command, exposed=False, *, console_absent=False, console_reply=None,
                     diagnostics_exposed=False, diagnostics_error=False, action_identity_missing=False,
                     console_export_error=False, retained_attachments=False):
        identity = "UI_LAB_RUN_synthetic-run_SOURCE_" + lab.source_digest() + "_COMMAND_" + command
        teardown = identity.replace("UI_LAB_RUN_", "UI_LAB_TEARDOWN_", 1)
        def tool(arguments, destination):
            if arguments[:2] == ["get", "content-availability"]:
                value = {"hasCoverage": False, "hasDiagnostics": True, "hasTestResults": True,
                         "logs": ["action"] if console_absent else ["action", "console"]}
            elif arguments[:3] == ["get", "test-results", "tests"]:
                value = {"testNodes": [{"nodeType": "Test Case", "nodeIdentifier": "Lab/testCommand()",
                                         "result": "Passed" if command == "sentinel-pass" else "Failed"}]}
            elif arguments[:3] == ["get", "test-results", "activities"]:
                value = {"testRuns": {"activities": [
                    {"title": identity},
                    {"title": teardown},
                    {"title": "UI_LAB_INTENT_SENTINEL_INSERTED"},
                    {"title": "UI_LAB_CREDENTIAL_SENTINEL_INSERTED"},
                    {"title": "UI_LAB_NONEMPTY_REPLACEMENT_VERIFIED"},
                    {"title": "UI_LAB_INTENTIONAL_INTENT_FAILURE"},
                    {"title": "UI_LAB_INTENTIONAL_CREDENTIAL_FAILURE"},
                ]}}
                if retained_attachments:
                    value["testRuns"]["activities"][0]["attachments"] = [
                        {"name": "Debug Description", "lifetime": "deleteOnSuccess"}
                    ]
            else:
                kind = arguments[arguments.index("--type") + 1]
                if kind == "console" and console_export_error:
                    raise lab.LabError("XCRESULT_EXPORT_FAILED")
                prefix = "missing identity" if kind == "action" and action_identity_missing else identity + teardown
                value = {"log": prefix + (lab.SENTINEL.decode() if exposed else " synthetic safe log")}
            lab.write_private(destination, lab.json_bytes(value))
            return value

        def export(arguments, **kwargs):
            if arguments[:4] == ["xcrun", "xcresulttool", "get", "log"]:
                self.assertTrue(console_absent)
                code, stdout, stderr = console_reply or (1, b"", b"Error: No console log available\n")
                return subprocess.CompletedProcess(arguments, code, stdout, stderr)
            output = Path(arguments[arguments.index("--output-path") + 1])
            output.mkdir()
            if arguments[3] == "diagnostics":
                if diagnostics_error:
                    return subprocess.CompletedProcess(arguments, 1, b"", b"diagnostic export failure")
                payload = lab.SENTINEL if diagnostics_exposed else b"synthetic safe diagnostics"
                lab.write_private(output / "Session-DriverGPSProofUITests-synthetic.log", payload)
            else:
                (output / "manifest.json").write_text("[]")
                if retained_attachments:
                    lab.write_private(output / "Debug-Description.txt", b"synthetic retained attachment")
            return subprocess.CompletedProcess(arguments, 0, b"", b"")
        return tool, export

    def test_audit_accepts_complete_synthetic_success_and_deliberate_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            for command in ("sentinel-pass", "sentinel-fail-intent", "sentinel-fail-credential"):
                directory = self.make_audit_run(Path(temporary), command)
                tool, export = self.fake_exports(command)
                with patch.object(lab, "tool_json", tool), patch.object(lab.subprocess, "run", export):
                    lab.audit_one(directory, command, "runner", "Xcode test")

    def test_audit_blocks_marker_leak_and_does_not_issue_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self.make_audit_run(Path(temporary), "sentinel-pass")
            tool, export = self.fake_exports("sentinel-pass", exposed=True)
            with patch.object(lab, "tool_json", tool), patch.object(lab.subprocess, "run", export):
                with self.assertRaisesRegex(lab.LabError, "SENTINEL_EXPOSED"):
                    lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test")

    def test_absent_console_requires_metadata_and_exact_cli_proof(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self.make_audit_run(Path(temporary), "sentinel-pass")
            tool, export = self.fake_exports("sentinel-pass", console_absent=True)
            with patch.object(lab, "tool_json", tool), patch.object(lab.subprocess, "run", export):
                audited = lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test")
            self.assertEqual(audited["logs"]["console"]["status"], "ABSENT_CONFIRMED")
            self.assertEqual(audited["diagnostics"]["status"], "EXPORTED")
            self.assertFalse((directory / "audit-exports" / "console.json").exists())
            self.assertTrue((directory / "audit-exports" / "console-absence.log").exists())

    def test_other_console_errors_or_metadata_contradictions_block(self):
        replies = ((1, b"", b"Error: Permission denied\n"),
                   (1, b"", b"Error: No console log available\nUnexpected warning\n"),
                   (2, b"", b"Error: No console log available\n"),
                   (1, b"unexpected output", b"Error: No console log available\n"),
                   (0, b"{}", b""))
        for reply in replies:
            with self.subTest(reply=reply), tempfile.TemporaryDirectory() as temporary:
                directory = self.make_audit_run(Path(temporary), "sentinel-pass")
                tool, export = self.fake_exports("sentinel-pass", console_absent=True, console_reply=reply)
                with patch.object(lab, "tool_json", tool), patch.object(lab.subprocess, "run", export):
                    with self.assertRaisesRegex(lab.LabError, "CONSOLE_ABSENCE_NOT_CONFIRMED"):
                        lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test")

    def test_advertised_console_export_error_is_not_treated_as_absent(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self.make_audit_run(Path(temporary), "sentinel-pass")
            tool, export = self.fake_exports("sentinel-pass", console_export_error=True)
            with patch.object(lab, "tool_json", tool), patch.object(lab.subprocess, "run", export):
                with self.assertRaisesRegex(lab.LabError, "XCRESULT_EXPORT_FAILED"):
                    lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test")

    def test_unknown_log_availability_blocks_instead_of_skipping_content(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            availability = {"hasCoverage": False, "hasDiagnostics": True, "hasTestResults": True,
                            "logs": ["action", "unknown-future-log"]}
            with patch.object(lab, "tool_json", return_value=availability):
                with self.assertRaisesRegex(lab.LabError, "INVALID_LOG_AVAILABILITY"):
                    lab.export_available_logs(root / "result.xcresult", root)

    def test_diagnostics_export_error_blocks(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self.make_audit_run(Path(temporary), "sentinel-pass")
            tool, export = self.fake_exports("sentinel-pass", console_absent=True, diagnostics_error=True)
            with patch.object(lab, "tool_json", tool), patch.object(lab.subprocess, "run", export):
                with self.assertRaisesRegex(lab.LabError, "DIAGNOSTICS_EXPORT_FAILED"):
                    lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test")

    def test_action_identity_remains_mandatory_when_console_is_absent(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self.make_audit_run(Path(temporary), "sentinel-pass")
            tool, export = self.fake_exports("sentinel-pass", console_absent=True, action_identity_missing=True)
            with patch.object(lab, "tool_json", tool), patch.object(lab.subprocess, "run", export):
                with self.assertRaisesRegex(lab.LabError, "IDENTITY_MISMATCH"):
                    lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test")

    def test_diagnostic_attribute_value_leak_blocks_full_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = self.make_audit_run(root, "sentinel-pass")
            tool, export = self.fake_exports("sentinel-pass", console_absent=True, diagnostics_exposed=True)
            args = type("Arguments", (), {"receipt": root / "receipt.json", "runner_app": root / "unused.app",
                                          "pass_dir": directory, "fail_intent_dir": root / "unused-intent",
                                          "fail_credential_dir": root / "unused-credential"})()
            with patch.object(lab, "tool_json", tool), patch.object(lab.subprocess, "run", export), \
                    patch.object(lab, "runner_info", return_value=("runner", "test", "runner")), \
                    patch.object(lab, "xcode_version", return_value="Xcode test"):
                with self.assertRaisesRegex(lab.LabError, "SENTINEL_EXPOSED_IN_ARTIFACTS"):
                    lab.audit(args)
            self.assertFalse(args.receipt.exists())
            blocked = list(root.glob("privacy-audit-blocked-*.json"))
            self.assertEqual(len(blocked), 1)
            self.assertFalse(lab.private_json(blocked[0])["approved"])
            scan = lab.private_json(directory / "audit-exports" / "sentinel-scan.json")
            self.assertEqual(scan["status"], "BLOCKED")
            self.assertEqual(len(scan["exposures"]), 1)

    def test_retained_attachments_still_block_while_diagnostic_scan_is_completed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self.make_audit_run(Path(temporary), "sentinel-fail-intent")
            tool, export = self.fake_exports("sentinel-fail-intent", console_absent=True,
                                             diagnostics_exposed=True, retained_attachments=True)
            with patch.object(lab, "tool_json", tool), patch.object(lab.subprocess, "run", export):
                with self.assertRaisesRegex(lab.LabError, "RETAINED_ACTIVITY_ATTACHMENTS"):
                    lab.audit_one(directory, "sentinel-fail-intent", "runner", "Xcode test")
            status = lab.private_json(directory / "audit-exports" / "audit-status.json")
            self.assertEqual(status["status"], "BLOCKED")
            self.assertEqual(status["violations"], ["RETAINED_ACTIVITY_ATTACHMENTS", "RETAINED_ATTACHMENTS",
                                                    "SENTINEL_EXPOSED_IN_ARTIFACTS"])

    def test_read_only_run_can_export_audit_to_separate_private_directory(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = self.make_audit_run(root, "sentinel-pass")
            before = sorted(path.name for path in directory.iterdir())
            exports = root / "separate-exports"
            tool, export = self.fake_exports("sentinel-pass", console_absent=True)
            with patch.object(lab, "tool_json", tool), patch.object(lab.subprocess, "run", export):
                lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test", exports_dir=exports)
            self.assertEqual(before, sorted(path.name for path in directory.iterdir()))
            self.assertTrue((exports / "audit-status.json").exists())

    def test_audit_rejects_stale_result_from_a_previous_run(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self.make_audit_run(Path(temporary), "sentinel-pass")
            value = lab.private_json(directory / "result.json")
            value["runID"] = "previous-run"
            (directory / "result.json").write_bytes(lab.json_bytes(value))
            with self.assertRaisesRegex(lab.LabError, "INCOMPLETE_OR_MISMATCHED"):
                lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test")

    def test_fatal_sentinel_requires_abort_and_confirmed_teardown(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self.make_audit_run(Path(temporary), "sentinel-fail-intent")
            result_path = directory / "result.json"
            original = lab.private_json(result_path)
            for key, value in (("success", True), ("teardownCompleted", False),
                               ("pasteboardEmptyAtTeardown", False), ("fixtureRemoved", False)):
                result_path.write_bytes(lab.json_bytes({**original, key: value}))
                with self.subTest(key=key), self.assertRaises(lab.LabError):
                    lab.audit_one(directory, "sentinel-fail-intent", "runner", "Xcode test")

    def test_false_empty_cannot_replace_foreground_readability_clear_and_restore_proof(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self.make_audit_run(Path(temporary), "sentinel-pass")
            result_path = directory / "result.json"
            original = lab.private_json(result_path)
            for scope in ("teardown", "paste"):
                for key in lab.CLEANUP_FLAGS:
                    value = json.loads(json.dumps(original))
                    cleanup = (value["teardownPasteboardCleanup"] if scope == "teardown"
                               else value["pasteEvents"][0]["cleanup"])
                    cleanup[key] = False
                    # The old zero-based flag remains true: that must not pass.
                    result_path.write_bytes(lab.json_bytes(value))
                    with self.subTest(scope=scope, key=key), self.assertRaisesRegex(lab.LabError, "CLEANUP_UNVERIFIED"):
                        lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test")
            value = json.loads(json.dumps(original))
            value.pop("teardownPasteboardCleanup")
            result_path.write_bytes(lab.json_bytes(value))
            with self.assertRaisesRegex(lab.LabError, "CLEANUP_UNVERIFIED"):
                lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test")

    def test_success_sentinel_requires_verified_replacement_of_nonempty_dummy(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self.make_audit_run(Path(temporary), "sentinel-pass")
            result_path = directory / "result.json"
            original = lab.private_json(result_path)
            for present in (True, False):
                value = json.loads(json.dumps(original))
                if present:
                    value["intentReplacementVerified"] = False
                else:
                    value.pop("intentReplacementVerified")
                result_path.write_bytes(lab.json_bytes(value))
                with self.subTest(present=present), self.assertRaisesRegex(lab.LabError, "NONEMPTY_REPLACEMENT"):
                    lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test")

    def test_audit_rejects_mixed_result_bundle_or_terminal_log(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = self.make_audit_run(Path(temporary), "sentinel-pass")
            tool, export = self.fake_exports("sentinel-pass")
            (directory / "xcodebuild.log").write_bytes(b"previous run without matching identity")
            with patch.object(lab, "tool_json", tool), patch.object(lab.subprocess, "run", export):
                with self.assertRaisesRegex(lab.LabError, "IDENTITY_MISMATCH"):
                    lab.audit_one(directory, "sentinel-pass", "runner", "Xcode test")


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

import qa_navigation_tts_evidence as evidence


ROOT = Path(__file__).resolve().parents[1]
TTS_SCRIPT = ROOT / "scripts/qa_navigation_tts_device_smoke.sh"
QA_GATE = ROOT / "scripts/qa_gate_card.sh"
RUN_ID = "run-001"


def git(repo: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), *args],
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


def create_repo(root: Path, *, nested: bool = False) -> tuple[Path, Path, Path, Path, Path]:
    app_root = root / "repo/src/aplicativo-motorista" if nested else root / "app"
    repo_root = root / "repo" if nested else app_root
    scripts = app_root / "scripts"
    integration_tests = app_root / "integration_test"
    scripts.mkdir(parents=True)
    integration_tests.mkdir(parents=True)
    producer = scripts / "qa_navigation_tts_device_smoke.sh"
    validator = scripts / "qa_navigation_tts_evidence.py"
    integration_test = integration_tests / "navigation_tts_device_smoke_test.dart"
    shutil.copy2(TTS_SCRIPT, producer)
    shutil.copy2(evidence.VALIDATOR_PATH, validator)
    shutil.copy2(evidence.TEST_PATH, integration_test)
    (app_root / ".gitignore").write_text("/output/\n", encoding="utf-8")
    git(app_root, "init", "-q", str(repo_root))
    git(app_root, "config", "user.name", "VanRides QA")
    git(app_root, "config", "user.email", "qa@vanrides.invalid")
    git(app_root, "add", ".gitignore", "scripts", "integration_test")
    git(app_root, "commit", "-qm", "test fixture")

    evidence_dir = app_root / "output/navigation-tts-device-smoke/run-001"
    evidence_dir.mkdir(parents=True)
    write_bound_log(app_root, evidence_dir, run_id=RUN_ID)
    return app_root, producer, validator, integration_test, evidence_dir


def write_bound_log(
    app_root: Path,
    evidence_dir: Path,
    *,
    run_id: str,
    source_sha: str | None = None,
    source_tree: str | None = None,
) -> None:
    source_sha = source_sha or git(app_root, "rev-parse", "HEAD")
    source_tree = source_tree or git(app_root, "rev-parse", "HEAD^{tree}")
    marker = evidence.marker_for(
        source_sha=source_sha,
        source_tree=source_tree,
        run_id=run_id,
    )
    (evidence_dir / evidence.LOG_FILE_NAME).write_text(
        f"flutter: {marker}\n",
        encoding="utf-8",
    )


def create_technical_manifest(
    app_root: Path,
    producer: Path,
    validator: Path,
    integration_test: Path,
    evidence_dir: Path,
) -> Path:
    return evidence.create_manifest(
        evidence_dir,
        app_root=app_root,
        producer_path=producer,
        validator_path=validator,
        test_path=integration_test,
        device_target_platform="ios",
        run_id=RUN_ID,
    )


def verify_technical_manifest(
    app_root: Path,
    producer: Path,
    validator: Path,
    integration_test: Path,
    evidence_dir: Path,
    *,
    require_current_source: bool = False,
) -> dict:
    return evidence.verify_manifest(
        evidence_dir,
        app_root=app_root,
        producer_path=producer,
        validator_path=validator,
        test_path=integration_test,
        require_current_source=require_current_source,
    )


class NavigationTtsEvidenceTest(unittest.TestCase):
    def test_monorepo_manifest_uses_actual_nested_git_blobs(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp), nested=True
            )
            create_technical_manifest(app_root, producer, validator, integration_test, evidence_dir)
            data = verify_technical_manifest(app_root, producer, validator, integration_test, evidence_dir)
            expected_blob = git(
                app_root, "rev-parse",
                f"{data['sourceGitSha']}:src/aplicativo-motorista/scripts/qa_navigation_tts_device_smoke.sh",
            )
            self.assertEqual(expected_blob, data["producerGitBlob"])
            self.assertEqual(hashlib.sha256(producer.read_bytes()).hexdigest(), data["producerSha256"])
            self.assertEqual("scripts/qa_navigation_tts_device_smoke.sh", data["producerFile"])
            producer.write_text(producer.read_text(encoding="utf-8") + "\n", encoding="utf-8")
            with self.assertRaises(evidence.EvidenceError):
                evidence.verify_manifest(
                    evidence_dir, app_root=app_root, producer_path=producer,
                    validator_path=validator, test_path=integration_test,
                    require_current_source=True,
                )

    def test_create_and_verify_manifest_from_clean_tracked_snapshot(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp)
            )

            manifest_path = create_technical_manifest(
                app_root, producer, validator, integration_test, evidence_dir
            )
            verified = verify_technical_manifest(
                app_root, producer, validator, integration_test, evidence_dir
            )

            data = json.loads(manifest_path.read_text(encoding="utf-8"))
            self.assertEqual(2, data["schemaVersion"])
            self.assertEqual("navigation_tts_device_smoke", data["evidenceType"])
            self.assertEqual("PASS", data["status"])
            self.assertEqual(RUN_ID, data["runId"])
            self.assertEqual(git(app_root, "rev-parse", "HEAD"), data["sourceGitSha"])
            self.assertEqual(
                git(app_root, "rev-parse", "HEAD^{tree}"), data["sourceGitTree"]
            )
            self.assertTrue(data["trackedWorktreeClean"])
            self.assertEqual("ios", data["deviceTargetPlatform"])
            self.assertTrue(data["noRealDataPolicy"])
            self.assertNotIn("operatorHeardPtBr", data)
            self.assertNotIn("requiresHumanOperator", data)
            self.assertNotIn("fieldReady", data)
            self.assertNotIn("externalPilotReady", data)
            self.assertEqual(data, verified)

    def test_manifest_records_verifiable_git_blobs_and_digests(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp)
            )
            manifest_path = create_technical_manifest(
                app_root, producer, validator, integration_test, evidence_dir
            )
            data = json.loads(manifest_path.read_text(encoding="utf-8"))
            source_sha = data["sourceGitSha"]

            for prefix, path in (
                ("producer", producer),
                ("validator", validator),
                ("test", integration_test),
            ):
                relative = path.relative_to(app_root).as_posix()
                blob = git(app_root, "rev-parse", f"{source_sha}:{relative}")
                raw = subprocess.run(
                    ["git", "-C", str(app_root), "show", f"{source_sha}:{relative}"],
                    check=True,
                    stdout=subprocess.PIPE,
                ).stdout
                self.assertEqual(relative, data[f"{prefix}File"])
                self.assertEqual(blob, data[f"{prefix}GitBlob"])
                self.assertEqual(
                    hashlib.sha256(raw).hexdigest(), data[f"{prefix}Sha256"]
                )

    def test_create_rejects_dirty_tracked_worktree(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp)
            )
            producer.write_text("dirty\n", encoding="utf-8")

            with self.assertRaisesRegex(evidence.EvidenceError, "worktree rastreado"):
                create_technical_manifest(
                    app_root, producer, validator, integration_test, evidence_dir
                )

    def test_create_rejects_log_rebound_to_other_sha_tree_or_run(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp)
            )
            current_sha = git(app_root, "rev-parse", "HEAD")
            current_tree = git(app_root, "rev-parse", "HEAD^{tree}")
            (evidence_dir / evidence.LOG_FILE_NAME).write_text(
                f"flutter: {evidence.MARKER}:{evidence.SCENARIO}\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(
                evidence.EvidenceError, "source SHA/tree/run id"
            ):
                create_technical_manifest(
                    app_root, producer, validator, integration_test, evidence_dir
                )

            cases = (
                ("0" * 40, current_tree, RUN_ID, "source SHA"),
                (current_sha, "0" * 40, RUN_ID, "tree"),
                (current_sha, current_tree, "another-run", "run id"),
            )
            for source_sha, source_tree, run_id, label in cases:
                with self.subTest(label=label):
                    write_bound_log(
                        app_root,
                        evidence_dir,
                        run_id=run_id,
                        source_sha=source_sha,
                        source_tree=source_tree,
                    )
                    with self.assertRaisesRegex(
                        evidence.EvidenceError, "source SHA/tree/run id"
                    ):
                        create_technical_manifest(
                            app_root,
                            producer,
                            validator,
                            integration_test,
                            evidence_dir,
                        )

    def test_manifest_is_create_only(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp)
            )
            create_technical_manifest(
                app_root, producer, validator, integration_test, evidence_dir
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "ja existe"):
                create_technical_manifest(
                    app_root, producer, validator, integration_test, evidence_dir
                )

    def test_verify_rejects_log_tampering(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp)
            )
            create_technical_manifest(
                app_root, producer, validator, integration_test, evidence_dir
            )
            (evidence_dir / evidence.LOG_FILE_NAME).write_text(
                f"{evidence.MARKER}\ntampered\n", encoding="utf-8"
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "logSha256"):
                verify_technical_manifest(
                    app_root, producer, validator, integration_test, evidence_dir
                )

    def test_verify_rejects_human_claim_in_technical_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp)
            )
            manifest_path = create_technical_manifest(
                app_root, producer, validator, integration_test, evidence_dir
            )
            manifest_path.chmod(0o644)
            data = json.loads(manifest_path.read_text(encoding="utf-8"))
            data["operatorHeardPtBr"] = True
            manifest_path.write_text(json.dumps(data), encoding="utf-8")

            with self.assertRaisesRegex(evidence.EvidenceError, "campos inesperados"):
                verify_technical_manifest(
                    app_root, producer, validator, integration_test, evidence_dir
                )

    def test_verify_rejects_source_digest_tampering_even_if_envelope_is_rebound(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp)
            )
            manifest_path = create_technical_manifest(
                app_root, producer, validator, integration_test, evidence_dir
            )
            manifest_path.chmod(0o644)
            data = json.loads(manifest_path.read_text(encoding="utf-8"))
            data["producerSha256"] = "0" * 64
            data["manifestPayloadSha256"] = evidence._manifest_payload_sha256(data)
            manifest_path.write_text(json.dumps(data), encoding="utf-8")

            with self.assertRaisesRegex(evidence.EvidenceError, "producerSha256"):
                verify_technical_manifest(
                    app_root, producer, validator, integration_test, evidence_dir
                )

    def test_verify_rejects_generated_at_tampering_via_manifest_digest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp)
            )
            manifest_path = create_technical_manifest(
                app_root, producer, validator, integration_test, evidence_dir
            )
            manifest_path.chmod(0o644)
            data = json.loads(manifest_path.read_text(encoding="utf-8"))
            data["generatedAtUtc"] = "2020-01-01T00:00:00Z"
            manifest_path.write_text(json.dumps(data), encoding="utf-8")

            with self.assertRaisesRegex(
                evidence.EvidenceError, "manifestPayloadSha256"
            ):
                verify_technical_manifest(
                    app_root, producer, validator, integration_test, evidence_dir
                )

    def test_historical_verify_survives_head_advance_but_current_check_rejects(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp)
            )
            manifest_path = create_technical_manifest(
                app_root, producer, validator, integration_test, evidence_dir
            )
            recorded = json.loads(manifest_path.read_text(encoding="utf-8"))
            git(
                app_root,
                "rm",
                producer.relative_to(app_root).as_posix(),
                validator.relative_to(app_root).as_posix(),
                integration_test.relative_to(app_root).as_posix(),
            )
            git(app_root, "commit", "-qm", "test: advance head and remove old sources")

            verified = verify_technical_manifest(
                app_root, producer, validator, integration_test, evidence_dir
            )

            self.assertEqual(recorded, verified)
            with self.assertRaisesRegex(evidence.EvidenceError, "versao atual"):
                verify_technical_manifest(
                    app_root,
                    producer,
                    validator,
                    integration_test,
                    evidence_dir,
                    require_current_source=True,
                )

    def test_verify_rejects_malformed_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root, producer, validator, integration_test, evidence_dir = create_repo(
                Path(temp)
            )
            (evidence_dir / evidence.MANIFEST_FILE_NAME).write_text(
                "{not-json", encoding="utf-8"
            )

            with self.assertRaisesRegex(evidence.EvidenceError, "JSON invalido"):
                verify_technical_manifest(
                    app_root, producer, validator, integration_test, evidence_dir
                )

    def test_device_smoke_passes_run_binding_to_flutter_and_manifest(self) -> None:
        script = TTS_SCRIPT.read_text(encoding="utf-8")
        validator = evidence.VALIDATOR_PATH.read_text(encoding="utf-8")

        self.assertIn("VANRIDES_NAV_TTS_SOURCE_SHA", script)
        self.assertIn("VANRIDES_NAV_TTS_SOURCE_TREE", script)
        self.assertIn("VANRIDES_NAV_TTS_RUN_ID", script)
        self.assertIn("--run-id", script)
        self.assertIn("manifest_sha256=", validator)
        self.assertLess(
            script.index('if [[ "$test_status" -ne 0 ]]'),
            script.index("qa_navigation_tts_evidence.py"),
        )

    def test_gate_runs_manifest_contract_without_device(self) -> None:
        gate = QA_GATE.read_text(encoding="utf-8")

        self.assertIn("qa_navigation_tts_evidence_test.py", gate)
        self.assertIn("qa_navigation_tts_evidence.py", gate)
        self.assertIn("--check-only", gate)
        self.assertNotIn('qa_navigation_tts_evidence.py" --create', gate)


if __name__ == "__main__":
    unittest.main()

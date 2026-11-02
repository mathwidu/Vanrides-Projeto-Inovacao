#!/usr/bin/env python3
from __future__ import annotations

import contextlib
import io
import json
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import qa_navigation_audio_confirmation_review as review
import qa_navigation_tts_evidence_test as technical_fixture


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/qa_navigation_audio_confirmation_review.py"
QA_GATE = ROOT / "scripts/qa_gate_card.sh"
DOC = ROOT / "backlog/QA-Navigation-TTS-Audio-Confirmation.md"


def write_evidence(evidence_dir: Path, confirmation: str) -> None:
    evidence_dir.mkdir(parents=True, exist_ok=True)
    (evidence_dir / "navigation-tts-device-smoke.log").write_text(
        "\n".join(
            [
                "flutter: NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY:driver-navigation-repeat",
                "NAVIGATION_TTS_DEVICE_SMOKE_DEVICE_OK device=iPhone 16e Simulator",
            ]
        ),
        encoding="utf-8",
    )
    (evidence_dir / "audio-confirmation.md").write_text(
        "\n".join(
            [
                "# Navigation TTS Device Smoke - Audio Confirmation",
                "",
                "- Resultado automatizado: PASS em simulador iOS.",
                f"- Operador ouviu a frase em PT-BR: {confirmation}",
                "- Volume/device: PENDENTE.",
                "- Observacoes: sem dados reais.",
            ]
        ),
        encoding="utf-8",
    )
    (evidence_dir / review.tts_evidence.MANIFEST_FILE_NAME).write_text(
        '{"technical":"fixture"}\n',
        encoding="utf-8",
    )


def verified_technical_evidence() -> dict[str, object]:
    return {
        "runId": "run-technical-001",
        "sourceGitSha": "a" * 40,
        "sourceGitTree": "b" * 40,
        "logSha256": "c" * 64,
    }


class NavigationAudioConfirmationReviewTest(unittest.TestCase):
    def test_script_exists_and_is_executable(self) -> None:
        self.assertTrue(SCRIPT.exists(), "audio confirmation review script is missing")
        mode = SCRIPT.stat().st_mode
        self.assertTrue(mode & stat.S_IXUSR, "audio confirmation script is not executable")

    def test_check_only_passes_without_device_or_audio(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--check-only"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        self.assertIn("NAVIGATION_AUDIO_CONFIRMATION_REVIEW_OK", result.stdout)
        self.assertIn("requires_human_operator=true", result.stdout)

    def test_package_records_pending_human_audio_confirmation(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            evidence_dir = Path(temp) / "tts-evidence"
            write_evidence(evidence_dir, "PENDENTE")

            with mock.patch.object(
                review.tts_evidence,
                "verify_manifest",
                return_value=verified_technical_evidence(),
            ):
                review.write_package(evidence_dir)

            package_md = evidence_dir / "audio-review-package.md"
            package_json = evidence_dir / "audio-review-package.json"
            self.assertTrue(package_md.exists())
            self.assertTrue(package_json.exists())

            data = json.loads(package_json.read_text(encoding="utf-8"))
            self.assertEqual(data["card"], "APP-NAV-AUDIO-CONFIRM-001")
            self.assertTrue(data["automatedMarkerFound"])
            self.assertTrue(data["technicalEvidenceIntegrityVerified"])
            self.assertEqual("run-technical-001", data["technicalRunId"])
            self.assertEqual("a" * 40, data["sourceGitSha"])
            self.assertEqual("b" * 40, data["sourceGitTree"])
            self.assertEqual("c" * 64, data["technicalLogSha256"])
            self.assertEqual(64, len(data["technicalManifestSha256"]))
            self.assertEqual(64, len(data["audioConfirmationSha256"]))
            self.assertTrue(data["requiresHumanOperator"])
            self.assertFalse(data["operatorHeardPtBr"])
            self.assertFalse(data["fieldReady"])
            self.assertEqual(
                data["expectedSpokenText"],
                "Use o fallback para seguir com seguranca",
            )
            self.assertIn(
                "qa_navigation_tts_device_smoke.sh --run-device",
                data["operatorRunCommand"],
            )
            package_text = package_md.read_text(encoding="utf-8")
            self.assertIn("PENDENTE", package_text)
            self.assertIn("Frase esperada", package_text)
            self.assertIn("Use o fallback para seguir com seguranca", package_text)
            self.assertIn("qa_navigation_tts_device_smoke.sh --run-device", package_text)

    def test_assert_confirmed_fails_while_operator_has_not_heard_audio(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            evidence_dir = Path(temp) / "tts-evidence"
            write_evidence(evidence_dir, "PENDENTE")

            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr), mock.patch.object(
                review.tts_evidence,
                "verify_manifest",
                return_value=verified_technical_evidence(),
            ), self.assertRaises(SystemExit) as raised:
                review.assert_confirmed(evidence_dir)

            self.assertEqual(83, raised.exception.code)
            self.assertIn("PENDING_AUDIO_CONFIRMATION", stderr.getvalue())

    def test_assert_confirmed_passes_after_explicit_operator_confirmation(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            evidence_dir = Path(temp) / "tts-evidence"
            write_evidence(evidence_dir, "SIM")

            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout), mock.patch.object(
                review.tts_evidence,
                "verify_manifest",
                return_value=verified_technical_evidence(),
            ):
                review.assert_confirmed(evidence_dir)

            data = json.loads(
                (evidence_dir / "audio-review-package.json").read_text(
                    encoding="utf-8"
                )
            )
            self.assertTrue(data["operatorHeardPtBr"])
            self.assertIn("NAVIGATION_AUDIO_CONFIRMATION_CONFIRMED", stdout.getvalue())

    def test_negative_or_ambiguous_values_never_confirm_human_audio(self) -> None:
        for value in (
            "NAO OUVIU",
            "não ouviu",
            "NAO FOI AUDIVEL",
            "sim",
            "SIM - operador ouviu",
            "confirmado",
            "true",
            "yes",
        ):
            with self.subTest(value=value):
                text = f"- Operador ouviu a frase em PT-BR: {value}\n"
                confirmed, parsed = review.parse_operator_confirmation(text)
                self.assertFalse(confirmed)
                self.assertEqual(value, parsed)

    def test_duplicate_confirmation_lines_never_confirm(self) -> None:
        text = "\n".join(
            (
                "- Operador ouviu a frase em PT-BR: SIM",
                "- Operador ouviu a frase em PT-BR: NAO OUVIU",
            )
        )

        confirmed, parsed = review.parse_operator_confirmation(text)

        self.assertFalse(confirmed)
        self.assertEqual("INVALID_DUPLICATE_CONFIRMATION", parsed)

    def test_invalid_technical_manifest_blocks_human_package(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            evidence_dir = Path(temp) / "tts-evidence"
            write_evidence(evidence_dir, "SIM")

            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr), mock.patch.object(
                review.tts_evidence,
                "verify_manifest",
                side_effect=review.tts_evidence.EvidenceError("digest diverge"),
            ), self.assertRaises(SystemExit) as raised:
                review.write_package(evidence_dir)

            self.assertEqual(1, raised.exception.code)
            self.assertIn("Evidencia tecnica invalida", stderr.getvalue())
            self.assertFalse((evidence_dir / "audio-review-package.json").exists())

    def test_human_package_consumes_real_technical_manifest_schema(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            (
                app_root,
                producer,
                validator,
                integration_test,
                evidence_dir,
            ) = technical_fixture.create_repo(Path(temp))
            technical_fixture.create_technical_manifest(
                app_root,
                producer,
                validator,
                integration_test,
                evidence_dir,
            )
            (evidence_dir / "audio-confirmation.md").write_text(
                "- Operador ouviu a frase em PT-BR: PENDENTE\n",
                encoding="utf-8",
            )

            original_verifier = review.tts_evidence.verify_manifest
            with mock.patch.object(
                review.tts_evidence,
                "verify_manifest",
                side_effect=lambda path: original_verifier(
                    path,
                    app_root=app_root,
                    producer_path=producer,
                    validator_path=validator,
                    test_path=integration_test,
                ),
            ):
                data = review.write_package(evidence_dir)

            self.assertTrue(data["technicalEvidenceIntegrityVerified"])
            self.assertEqual(technical_fixture.RUN_ID, data["technicalRunId"])
            self.assertEqual(
                technical_fixture.git(app_root, "rev-parse", "HEAD"),
                data["sourceGitSha"],
            )

    def test_gate_runs_contract_only(self) -> None:
        gate = QA_GATE.read_text(encoding="utf-8")

        self.assertIn("qa_navigation_audio_confirmation_review_test.py", gate)
        self.assertIn("qa_navigation_audio_confirmation_review.py", gate)
        self.assertIn("--check-only", gate)
        self.assertNotIn("qa_navigation_audio_confirmation_review.py\" --assert-confirmed", gate)

    def test_doc_records_human_boundary_and_no_real_data_policy(self) -> None:
        doc = DOC.read_text(encoding="utf-8")

        self.assertIn("APP-NAV-AUDIO-CONFIRM-001", doc)
        self.assertIn("PENDING_AUDIO_CONFIRMATION", doc)
        self.assertIn("operador humano", doc)
        self.assertIn("nao deve conter dados reais", doc)
        self.assertIn("nao promove piloto externo", doc)
        self.assertIn("Frase esperada", doc)
        self.assertIn("Use o fallback para seguir com seguranca", doc)
        self.assertIn(review.tts_evidence.MANIFEST_FILE_NAME, doc)
        self.assertIn("valor exato `SIM`", doc)
        self.assertIn("NAO OUVIU", doc)


if __name__ == "__main__":
    unittest.main()

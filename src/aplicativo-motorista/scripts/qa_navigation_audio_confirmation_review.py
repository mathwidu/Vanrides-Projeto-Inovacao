#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

import qa_navigation_tts_evidence as tts_evidence


ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "backlog/QA-Navigation-TTS-Audio-Confirmation.md"
CARD = "APP-NAV-AUDIO-CONFIRM-001"
PENDING_EXIT = 83
EXPECTED_SPOKEN_TEXT = "Use o fallback para seguir com seguranca"
DEFAULT_OUTPUT_DIR = "output/navigation-tts-device-smoke/<timestamp>"
OPERATOR_RUN_COMMAND = (
    "./scripts/qa_navigation_tts_device_smoke.sh --run-device "
    f"--device <ios-device-ou-simulator> --output-dir {DEFAULT_OUTPUT_DIR}"
)
PACKAGE_COMMAND = (
    "./scripts/qa_navigation_audio_confirmation_review.py --package "
    f"--evidence-dir {DEFAULT_OUTPUT_DIR}"
)
ASSERT_COMMAND = (
    "./scripts/qa_navigation_audio_confirmation_review.py --assert-confirmed "
    f"--evidence-dir {DEFAULT_OUTPUT_DIR}"
)


def fail(message: str, code: int = 1) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(code)


def require_doc_text(pattern: str) -> None:
    if not DOC.exists():
        fail(f"Documento ausente: {DOC}")

    text = DOC.read_text(encoding="utf-8")
    if pattern not in text:
        fail(f"Contrato de confirmacao audivel sem termo obrigatorio: {pattern}")


def check_only() -> None:
    for pattern in [
        CARD,
        "PENDING_AUDIO_CONFIRMATION",
        "operador humano",
        "nao deve conter dados reais",
        "nao promove piloto externo",
        tts_evidence.MANIFEST_FILE_NAME,
        "valor exato `SIM`",
        "audio-review-package.json",
    ]:
        require_doc_text(pattern)

    print(
        "NAVIGATION_AUDIO_CONFIRMATION_REVIEW_OK "
        "modes=3 requires_human_operator=true"
    )


def find_lines(text: str, prefix: str) -> list[str]:
    return [
        line.strip()
        for line in text.splitlines()
        if line.strip().lower().startswith(prefix.lower())
    ]


def parse_operator_confirmation(text: str) -> tuple[bool, str]:
    lines = find_lines(text, "- Operador ouviu a frase em PT-BR:")
    if not lines:
        return False, "PENDENTE"
    if len(lines) != 1:
        return False, "INVALID_DUPLICATE_CONFIRMATION"

    value = lines[0].split(":", 1)[1].strip()
    confirmed = value == "SIM"
    return confirmed, value or "PENDENTE"


def load_evidence(evidence_dir: Path) -> dict[str, Any]:
    log_file = evidence_dir / "navigation-tts-device-smoke.log"
    confirmation_file = evidence_dir / "audio-confirmation.md"
    manifest_file = evidence_dir / tts_evidence.MANIFEST_FILE_NAME

    if not evidence_dir.exists():
        fail(f"Evidencia ausente: {evidence_dir}")
    if not log_file.exists():
        fail(f"Log ausente: {log_file}")
    if not confirmation_file.exists():
        fail(f"Checklist ausente: {confirmation_file}")

    try:
        technical = tts_evidence.verify_manifest(evidence_dir)
    except (tts_evidence.EvidenceError, OSError) as exc:
        fail(f"Evidencia tecnica invalida: {exc}")

    run_id = technical.get("runId")
    if not isinstance(run_id, str) or not run_id:
        fail("Evidencia tecnica invalida: runId ausente")

    log_text = log_file.read_text(encoding="utf-8", errors="replace")
    confirmation_text = confirmation_file.read_text(encoding="utf-8")
    automated_marker_found = "NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY" in log_text
    if not automated_marker_found:
        fail("NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY ausente no log")

    operator_confirmed, operator_value = parse_operator_confirmation(
        confirmation_text
    )

    return {
        "card": CARD,
        "evidenceDir": str(evidence_dir),
        "logPath": str(log_file),
        "audioConfirmationPath": str(confirmation_file),
        "audioConfirmationSha256": hashlib.sha256(
            confirmation_file.read_bytes()
        ).hexdigest(),
        "automatedMarkerFound": automated_marker_found,
        "technicalEvidenceIntegrityVerified": True,
        "technicalManifestPath": str(manifest_file),
        "technicalManifestSha256": hashlib.sha256(manifest_file.read_bytes()).hexdigest(),
        "technicalRunId": run_id,
        "sourceGitSha": technical["sourceGitSha"],
        "sourceGitTree": technical["sourceGitTree"],
        "technicalLogSha256": technical["logSha256"],
        "requiresHumanOperator": True,
        "operatorHeardPtBr": operator_confirmed,
        "operatorConfirmationValue": operator_value,
        "audioReadyForInternalPilot": operator_confirmed,
        "fieldReady": False,
        "externalPilotReady": False,
        "noRealDataPolicy": True,
        "expectedSpokenText": EXPECTED_SPOKEN_TEXT,
        "operatorRunCommand": OPERATOR_RUN_COMMAND,
        "packageCommand": PACKAGE_COMMAND,
        "assertCommand": ASSERT_COMMAND,
        "status": "CONFIRMED" if operator_confirmed else "PENDING_AUDIO_CONFIRMATION",
    }


def write_package(evidence_dir: Path) -> dict[str, Any]:
    data = load_evidence(evidence_dir)
    package_json = evidence_dir / "audio-review-package.json"
    package_md = evidence_dir / "audio-review-package.md"

    package_json.write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    package_md.write_text(render_markdown(data), encoding="utf-8")
    return data


def render_markdown(data: dict[str, Any]) -> str:
    status = data["status"]
    operator_value = data["operatorConfirmationValue"]
    return "\n".join(
        [
            "# Navigation Audio Confirmation Review",
            "",
            f"- Card: `{data['card']}`",
            f"- Status: `{status}`",
            "- Resultado tecnico: marker `NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY` encontrado.",
            "- Integridade do manifesto tecnico: verificada.",
            f"- Run id tecnico: `{data['technicalRunId']}`",
            f"- App SHA tecnico: `{data['sourceGitSha']}`",
            f"- Manifest SHA-256: `{data['technicalManifestSha256']}`",
            f"- Operador ouviu a frase em PT-BR: {operator_value}",
            f"- Frase esperada: {data['expectedSpokenText']}",
            f"- Audio pronto para piloto interno: {str(data['audioReadyForInternalPilot']).lower()}",
            "- Campo/parceiro externo liberado: false",
            "",
            "## Comandos",
            "",
            "```bash",
            str(data["operatorRunCommand"]),
            str(data["packageCommand"]),
            str(data["assertCommand"]),
            "```",
            "",
            "## Checklist Humano",
            "",
            "- [ ] Abrir o simulador/device usado no smoke.",
            "- [ ] Confirmar volume do Mac/device em nivel audivel.",
            "- [ ] Rodar ou reproduzir a evidencia local sem dados reais.",
            f"- [ ] Ouvir a frase PT-BR: {data['expectedSpokenText']}",
            "- [ ] Editar `audio-confirmation.md` com o valor exato `SIM` somente se a frase foi audivel.",
            "",
            "## Politica",
            "",
            "Este pacote nao deve conter dados reais, nomes de passageiros, endereco privado,",
            "empresa parceira, rota de campo, segredo ou token. Ele nao promove piloto externo.",
            "",
        ]
    )


def assert_confirmed(evidence_dir: Path) -> None:
    data = write_package(evidence_dir)
    if not data["operatorHeardPtBr"]:
        fail(
            "PENDING_AUDIO_CONFIRMATION: operador humano ainda nao confirmou audio PT-BR.",
            PENDING_EXIT,
        )

    print(
        "NAVIGATION_AUDIO_CONFIRMATION_CONFIRMED "
        f"evidence={evidence_dir} field_ready=false"
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Review package for VanRides navigation audio confirmation."
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check-only", action="store_true")
    mode.add_argument("--package", action="store_true")
    mode.add_argument("--assert-confirmed", action="store_true")
    parser.add_argument("--evidence-dir", type=Path)
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)

    if args.check_only:
        check_only()
        return 0

    if args.evidence_dir is None:
        fail("--evidence-dir e obrigatorio para --package/--assert-confirmed", 2)

    evidence_dir = args.evidence_dir.resolve()
    if args.package:
        write_package(evidence_dir)
        print(
            "NAVIGATION_AUDIO_CONFIRMATION_PACKAGE_OK "
            f"evidence={evidence_dir} requires_human_operator=true"
        )
        return 0

    assert_confirmed(evidence_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

import qa_navigation_tts_evidence as tts_evidence


ROOT = Path(__file__).resolve().parents[1]
CARD = "APP-NAV-PRE-PILOT-READINESS-001"
HUMAN_AUDIO_CARD = "APP-NAV-AUDIO-CONFIRM-001"
DEFAULT_OUTPUT_ROOT = ROOT / "output/navigation-pre-pilot-readiness"
TECHNICAL_CHECK_NAMES = (
    "navigationDriveSimulation",
    "navigationActionAutoSync",
    "navigationTtsDeviceContract",
    "navigationAudioOperatorHandoff",
)


def fail(message: str, code: int = 1) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(code)


def default_output_dir() -> Path:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return DEFAULT_OUTPUT_ROOT / timestamp


def relative_path(path: Path) -> str:
    try:
        return path.resolve().relative_to(ROOT.resolve()).as_posix()
    except ValueError:
        return f"external:{path.name}"


def run_contract_check(name: str, path: Path, *args: str) -> dict[str, Any]:
    command = [sys.executable, str(path), *args] if path.suffix == ".py" else [str(path), *args]
    base = {
        "name": name,
        "path": relative_path(path),
        "command": [relative_path(path), *args],
    }
    if not path.is_file():
        return {
            **base,
            "status": "fail",
            "passed": False,
            "exitCode": None,
            "reason": "contrato versionado ausente",
        }

    try:
        result = subprocess.run(
            command,
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
            timeout=30,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return {
            **base,
            "status": "fail",
            "passed": False,
            "exitCode": None,
            "reason": str(exc),
        }

    output = (result.stdout + result.stderr).strip()
    return {
        **base,
        "status": "pass" if result.returncode == 0 else "fail",
        "passed": result.returncode == 0,
        "exitCode": result.returncode,
        "outputTail": output[-500:],
        "reason": None if result.returncode == 0 else "contrato retornou erro",
    }


def invalid_device_evidence(path: Path, reason: str) -> dict[str, Any]:
    return {
        "name": "ttsDeviceEvidence",
        "path": relative_path(path),
        "status": "invalid",
        "passed": False,
        "integrityVerified": False,
        "reason": reason,
    }


def load_tts_device_evidence(evidence_dir: Path | None) -> dict[str, Any]:
    if evidence_dir is None:
        return {
            "name": "ttsDeviceEvidence",
            "path": "not-provided",
            "status": "not_provided",
            "passed": False,
            "integrityVerified": False,
            "reason": "forneca --tts-evidence-dir somente no modo --report",
        }

    evidence_dir = evidence_dir.resolve()
    try:
        data = tts_evidence.verify_manifest(
            evidence_dir,
            require_current_source=True,
        )
    except (tts_evidence.EvidenceError, OSError) as exc:
        return invalid_device_evidence(
            evidence_dir / tts_evidence.MANIFEST_FILE_NAME,
            str(exc),
        )

    return {
        "name": "ttsDeviceEvidence",
        "path": relative_path(evidence_dir / tts_evidence.MANIFEST_FILE_NAME),
        "status": "integrity_verified",
        "passed": True,
        "integrityVerified": True,
        "runId": data["runId"],
        "sourceGitSha": data["sourceGitSha"],
        "sourceGitTree": data["sourceGitTree"],
        "logSha256": data["logSha256"],
        "deviceTargetPlatform": data["deviceTargetPlatform"],
        "reason": None,
    }


def collect_checks(tts_evidence_dir: Path | None = None) -> dict[str, dict[str, Any]]:
    device_evidence = load_tts_device_evidence(tts_evidence_dir)
    return {
        "navigationDriveSimulation": run_contract_check(
            "navigationDriveSimulation",
            ROOT / "scripts/qa_navigation_drive_sim_smoke.sh",
            "--check-only",
        ),
        "navigationActionAutoSync": run_contract_check(
            "navigationActionAutoSync",
            ROOT / "scripts/qa_navigation_action_auto_sync_smoke.py",
            "--check-only",
        ),
        "navigationTtsDeviceContract": run_contract_check(
            "navigationTtsDeviceContract",
            ROOT / "scripts/qa_navigation_tts_device_smoke.sh",
            "--check-only",
        ),
        "navigationAudioOperatorHandoff": run_contract_check(
            "navigationAudioOperatorHandoff",
            ROOT / "scripts/qa_navigation_audio_operator_handoff_test.py",
        ),
        "gateExecution": {
            "name": "gateExecution",
            "path": "scripts/qa_gate_card.sh",
            "status": "not_run",
            "passed": False,
            "reason": "o scorecard nao executa nem declara o gate completo",
        },
        "ttsDeviceEvidence": device_evidence,
        "humanAudioConfirmation": {
            "name": "humanAudioConfirmation",
            "path": HUMAN_AUDIO_CARD,
            "status": "waiting_human",
            "passed": False,
            "operatorHeardPtBr": False,
            "blockingReason": f"{HUMAN_AUDIO_CARD} pendente de operador humano",
        },
    }


def build_summary(tts_evidence_dir: Path | None = None) -> dict[str, Any]:
    checks = collect_checks(tts_evidence_dir)
    contract_checks_passed = all(
        checks[name]["passed"] for name in TECHNICAL_CHECK_NAMES
    )
    contract_status = "pass" if contract_checks_passed else "fail"
    device_status = checks["ttsDeviceEvidence"]["status"]

    blocking_items = [
        checks["humanAudioConfirmation"]["blockingReason"],
        "contrato route-plan executado somente pelo coordenador",
    ]
    if device_status != "integrity_verified":
        blocking_items.append(
            "evidencia de device ausente ou sem proveniencia verificavel"
        )

    return {
        "card": CARD,
        "status": "hold",
        "contractStatus": contract_status,
        "contractChecksPassed": contract_checks_passed,
        "gateExecutionStatus": "not_run",
        "readinessStatus": "hold",
        "deviceEvidenceStatus": device_status,
        "routePlanEvidenceStatus": "coordinator_required",
        "humanAudioStatus": "waiting_human",
        "audioHumanConfirmed": False,
        "externalPilotReady": False,
        "fieldReady": False,
        "usesDevice": False,
        "usesBackend": False,
        "usesVps": False,
        "usesPartnerData": False,
        "usesExternalEvidence": tts_evidence_dir is not None,
        "noRealDataPolicy": True,
        "checks": checks,
        "blockingItems": blocking_items,
    }


def assert_contract_checks(summary: dict[str, Any]) -> None:
    if summary["contractChecksPassed"]:
        return

    failing = [
        name
        for name in TECHNICAL_CHECK_NAMES
        if not summary["checks"][name].get("passed")
    ]
    fail("NAVIGATION_PRE_PILOT_READINESS_TECHNICAL_FAIL " + ",".join(failing))


def render_markdown(summary: dict[str, Any]) -> str:
    lines = [
        "# Navigation Pre-Pilot Readiness",
        "",
        f"- Card: `{summary['card']}`",
        f"- Contract status: `{summary['contractStatus'].upper()}`",
        f"- Gate execution: `{summary['gateExecutionStatus'].upper()}`",
        f"- Readiness status: `{summary['readinessStatus'].upper()}`",
        f"- Contract checks passed: {str(summary['contractChecksPassed']).lower()}",
        f"- Device evidence: `{summary['deviceEvidenceStatus'].upper()}`",
        f"- Route-plan evidence: `{summary['routePlanEvidenceStatus'].upper()}`",
        f"- Human audio: `{summary['humanAudioStatus'].upper()}`",
        f"- Audio human confirmed: {str(summary['audioHumanConfirmed']).lower()}",
        f"- External pilot ready: {str(summary['externalPilotReady']).lower()}",
        f"- Field ready: {str(summary['fieldReady']).lower()}",
        "- Device/backend/VPS executed by this scorecard: false",
        "",
        "## Checks",
        "",
    ]
    for name, check in summary["checks"].items():
        result = str(check.get("status", "unknown")).upper()
        lines.append(f"- `{name}`: {result} (`{check.get('path', 'n/a')}`)")

    lines.extend(
        [
            "",
            "## Hold",
            "",
            f"- `{HUMAN_AUDIO_CARD}` continua pendente.",
            "- `operatorHeardPtBr=false` permanece canônico ate decisão humana.",
            "- Evidencia externa nao altera o estado humano deste scorecard.",
            "- Este report nao libera parceiro.",
            "- Este report nao libera campo.",
            "- Este report nao promove piloto externo.",
            "",
            "## Politica",
            "",
            "Este scorecard nao deve conter dados reais, passageiro real, endereco",
            "privado, empresa parceira, rota de campo, segredo ou token.",
        ]
    )
    return "\n".join(lines) + "\n"


def write_report(summary: dict[str, Any], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "navigation-pre-pilot-readiness.json").write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    (output_dir / "navigation-pre-pilot-readiness.md").write_text(
        render_markdown(summary),
        encoding="utf-8",
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Navigation pre-pilot readiness scorecard."
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check-only", action="store_true")
    mode.add_argument("--report", action="store_true")
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--tts-evidence-dir", type=Path)
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if not args.report and args.output_dir is not None:
        fail("--output-dir e permitido somente com --report", 2)
    if not args.report and args.tts_evidence_dir is not None:
        fail("--tts-evidence-dir e permitido somente com --report", 2)

    evidence_dir = args.tts_evidence_dir.resolve() if args.tts_evidence_dir else None
    summary = build_summary(evidence_dir)
    assert_contract_checks(summary)

    if args.report:
        output_dir = (args.output_dir or default_output_dir()).resolve()
        write_report(summary, output_dir)
        if summary["deviceEvidenceStatus"] == "invalid":
            print(
                "NAVIGATION_PRE_PILOT_READINESS_EVIDENCE_INVALID "
                f"contract_status={summary['contractStatus']} "
                f"readiness_status={summary['readinessStatus']} evidence={output_dir}",
                file=sys.stderr,
            )
            return 85
        print(
            "NAVIGATION_PRE_PILOT_READINESS_REPORT_OK "
            f"contract_status={summary['contractStatus']} "
            f"gate_execution_status={summary['gateExecutionStatus']} "
            f"readiness_status={summary['readinessStatus']} evidence={output_dir}"
        )
        return 0

    print(
        "NAVIGATION_PRE_PILOT_READINESS "
        f"status={summary['status']} "
        f"contract_status={summary['contractStatus']} "
        f"gate_execution_status={summary['gateExecutionStatus']} "
        "contract_checks=pass "
        f"device_evidence_status={summary['deviceEvidenceStatus']} "
        f"route_plan_evidence_status={summary['routePlanEvidenceStatus']} "
        f"human_audio_status={summary['humanAudioStatus']} "
        f"audio_human_confirmed={str(summary['audioHumanConfirmed']).lower()} "
        f"external_pilot_ready={str(summary['externalPilotReady']).lower()} "
        f"field_ready={str(summary['fieldReady']).lower()}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

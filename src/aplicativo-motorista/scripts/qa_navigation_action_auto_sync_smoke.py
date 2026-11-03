#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
CARD = "APP-NAV-ACTION-AUTO-SYNC-QA-001"
TEST_FILE = Path("test/features/driver_trip/home_motorista_active_trip_test.dart")
DEFAULT_OUTPUT_ROOT = ROOT / "output/navigation-action-auto-sync"
REQUIRED_SCENARIOS = {
    "homeLoadAutoSync": "envia fila local persistida automaticamente ao abrir home do motorista",
    "foregroundResumeAutoSync": "rele fila persistida e sincroniza quando app volta ao foreground",
    "manualFallbackAfterFailure": "tenta auto-sync no foreground e mantem enviar agora quando falha",
}


def fail(message: str, code: int = 1) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(code)


def default_output_dir() -> Path:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return DEFAULT_OUTPUT_ROOT / timestamp


def verify_source_contract() -> None:
    source_path = ROOT / TEST_FILE
    if not source_path.exists():
        fail(f"Teste fonte ausente: {TEST_FILE}")

    source = source_path.read_text(encoding="utf-8")
    missing = [
        label for label in REQUIRED_SCENARIOS.values() if label not in source
    ]
    if missing:
        fail(
            "Smoke sem cenarios obrigatorios no teste fonte: "
            + ", ".join(missing)
        )


def run_focused_widget_proof() -> subprocess.CompletedProcess[str]:
    command = [
        "flutter",
        "test",
        TEST_FILE.as_posix(),
        "--reporter",
        "expanded",
    ]
    result = subprocess.run(
        command,
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        print(result.stdout, file=sys.stdout)
        print(result.stderr, file=sys.stderr)
        fail(
            "Falha na prova focada da fila auto-sincronizada "
            f"(exit={result.returncode})."
        )
    return result


def build_evidence(result: subprocess.CompletedProcess[str]) -> dict[str, Any]:
    return {
        "card": CARD,
        "summary": "Smoke local da fila de acoes de navegacao auto-sincronizada.",
        "sourceTestFile": TEST_FILE.as_posix(),
        "command": "flutter test "
        f"{TEST_FILE.as_posix()} --reporter expanded",
        "foregroundOnly": True,
        "backgroundService": False,
        "fieldReady": False,
        "externalPilotReady": False,
        "noRealDataPolicy": True,
        "usesDevice": False,
        "usesBackend": False,
        "usesMapsPlatform": False,
        "usesVps": False,
        "scenarios": {key: True for key in REQUIRED_SCENARIOS},
        "requiredScenarioLabels": REQUIRED_SCENARIOS,
        "testExitCode": result.returncode,
    }


def render_markdown(data: dict[str, Any]) -> str:
    lines = [
        "# Navigation Action Auto-Sync Smoke",
        "",
        f"- Card: `{data['card']}`",
        "- Status: PASS",
        "- Escopo: foreground-only",
        "- Background service nativo: false",
        "- Campo/parceiro externo liberado: false",
        "- Device, backend, Maps Platform e VPS usados: false",
        f"- Teste fonte: `{data['sourceTestFile']}`",
        "",
        "## Cenários",
        "",
    ]
    for key, label in REQUIRED_SCENARIOS.items():
        lines.append(f"- `{key}`: {label}")
    lines.extend(
        [
            "",
            "## Comando",
            "",
            "```bash",
            str(data["command"]),
            "```",
            "",
            "## Politica",
            "",
            "Este smoke nao contem dados reais, parceiro, rota de campo, GPS real,",
            "audio humano, segredo, deploy, push ou VPS. Ele prova apenas o contrato",
            "local foreground-only do app.",
            "",
        ]
    )
    return "\n".join(lines)


def write_evidence(output_dir: Path, data: dict[str, Any]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "navigation-action-auto-sync.json").write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    (output_dir / "navigation-action-auto-sync.md").write_text(
        render_markdown(data),
        encoding="utf-8",
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Smoke QA local para fila de navegacao auto-sincronizada."
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check-only", action="store_true")
    mode.add_argument("--run", action="store_true")
    parser.add_argument("--output-dir", type=Path)
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    run_mode = args.run

    verify_source_contract()
    result = run_focused_widget_proof()
    data = build_evidence(result)

    if run_mode:
        output_dir = (args.output_dir or default_output_dir()).resolve()
        write_evidence(output_dir, data)
        print(
            "NAVIGATION_ACTION_AUTO_SYNC_EVIDENCE_OK "
            f"evidence={output_dir} foreground_only=true "
            "background_service=false field_ready=false"
        )
        return 0

    print(
        "NAVIGATION_ACTION_AUTO_SYNC_SMOKE_OK "
        "home_load=true foreground_resume=true manual_fallback=true "
        "foreground_only=true background_service=false "
        "evidence_root=output/navigation-action-auto-sync"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

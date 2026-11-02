#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import shlex
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_EVIDENCE_ROOT = ROOT / "output/mobile-e2e-long"
DEFAULT_API = "http://127.0.0.1:8080"
EXIT_INVALID_USAGE = 2
EXIT_BLOCKED_DEVICE = 84
EXIT_BLOCKED_SETUP = 83
EXIT_FAILED_UX_CONTRACT = 1

WIRELESS_PATTERN = re.compile(
    r"wirelessly tethered iOS device|--publish-port",
    re.IGNORECASE,
)
DEVICE_PATTERN = re.compile(
    r"No supported devices found|No devices are connected|Device is locked|not trusted|Developer Mode|Cannot start app",
    re.IGNORECASE,
)
CONNECTIVITY_PATTERN = re.compile(
    r"SocketException|Connection refused|Failed host lookup|HandshakeException|TimeoutException|HTTP 5|503|502|504",
    re.IGNORECASE,
)
BLOCKED_PATTERN = re.compile(r"BLOCKED_VALIDATION_[A-Z_]+")


@dataclass(frozen=True)
class MobileRole:
    role: str
    label: str
    suite: str
    setup: str


ROLES = {
    "driver-mobile": MobileRole(
        role="driver-mobile",
        label="motorista",
        suite="integration_test/motorista/all.dart",
        setup="device-and-dev-backend",
    ),
    "passenger-mobile": MobileRole(
        role="passenger-mobile",
        label="passageiro",
        suite="integration_test/passageiro/all.dart",
        setup="device-and-dev-backend",
    ),
    "guardian-mobile": MobileRole(
        role="guardian-mobile",
        label="responsavel",
        suite="integration_test/guardian/all.dart",
        setup="guardian-fixture-device-and-dev-backend",
    ),
    "operational-fixture-mobile": MobileRole(
        role="operational-fixture-mobile",
        label="operacional",
        suite="integration_test/operational_fixture/all.dart",
        setup="operational-fixture-device-and-dev-backend",
    ),
}


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if args.list:
        for role in ROLES.values():
            print("|".join([role.role, role.label, role.suite, role.setup]))
        return 0

    if args.check_only:
        print(
            "MOBILE_E2E_LONG_CONTRACT_OK "
            f"roles={len(ROLES)} "
            "evidence_root=output/mobile-e2e-long"
        )
        return 0

    role = require_role(args.role)
    if args.dry_run:
        evidence_dir = evidence_dir_for(args.evidence_root, role.role, args.timestamp)
        command_text = command_for(role, args)
        write_summary(
            evidence_dir=evidence_dir,
            status="DRY_RUN",
            role=role,
            api=args.api,
            device=args.device,
            runner=args.runner,
            command_text=command_text,
            classification="not-run",
            log_path=None,
        )
        print(f"MOBILE_E2E_LONG_DRY_RUN role={role.role} evidence={evidence_dir}")
        return 0

    if args.run_role:
        return run_role(role, args)

    print("Modo ausente. Use --check-only, --dry-run, --run-role ou --list.", file=sys.stderr)
    return EXIT_INVALID_USAGE


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run or classify mobile long E2E by role.")
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--check-only", action="store_true")
    modes.add_argument("--dry-run", action="store_true")
    modes.add_argument("--run-role", action="store_true")
    modes.add_argument("--list", action="store_true")
    parser.add_argument("--role", default="driver-mobile")
    parser.add_argument("--api", default=DEFAULT_API)
    parser.add_argument("--device", default="")
    parser.add_argument("--fixture-env", default="")
    parser.add_argument("--runner", default=str(ROOT / "scripts/run_dev_e2e.sh"))
    parser.add_argument("--evidence-root", default=str(DEFAULT_EVIDENCE_ROOT))
    parser.add_argument("--timestamp", default="")
    return parser.parse_args(argv)


def require_role(role_id: str) -> MobileRole:
    role = ROLES.get(role_id)
    if role is not None:
        return role
    valid = ", ".join(sorted(ROLES))
    print(f"Papel invalido: {role_id}", file=sys.stderr)
    print(f"Papeis validos: {valid}", file=sys.stderr)
    raise SystemExit(EXIT_INVALID_USAGE)


def evidence_dir_for(root: str, role: str, timestamp: str) -> Path:
    stamp = timestamp or datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return Path(root) / role / stamp


def command_for(role: MobileRole, args: argparse.Namespace) -> str:
    parts = []
    if args.fixture_env:
        parts.append(f"set -a; source {shlex.quote(args.fixture_env)}; set +a")
    parts.append(
        " ".join(
            [
                f"VANRIDES_API_BASE_URL={shlex.quote(args.api)}",
                shlex.quote(args.runner),
                shlex.quote(role.suite),
                shlex.quote(args.device) if args.device else "",
            ]
        ).strip()
    )
    return "; ".join(parts)


def run_role(role: MobileRole, args: argparse.Namespace) -> int:
    evidence_dir = evidence_dir_for(args.evidence_root, role.role, args.timestamp)
    evidence_dir.mkdir(parents=True, exist_ok=True)
    log_path = evidence_dir / "e2e.log"
    command_text = command_for(role, args)

    shell_command = command_text
    result = subprocess.run(
        ["bash", "-lc", shell_command],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )
    combined = result.stdout + result.stderr
    log_path.write_text(combined, encoding="utf-8")

    if result.returncode == 0:
        write_summary(
            evidence_dir=evidence_dir,
            status="PASS",
            role=role,
            api=args.api,
            device=args.device,
            runner=args.runner,
            command_text=command_text,
            classification="passed",
            log_path=log_path,
        )
        print(f"MOBILE_E2E_LONG_PASS role={role.role} evidence={evidence_dir}")
        return 0

    classification = classify_output(combined)
    status = "BLOCKED" if classification.startswith("BLOCKED_VALIDATION_") else "FAILED"
    write_summary(
        evidence_dir=evidence_dir,
        status=status,
        role=role,
        api=args.api,
        device=args.device,
        runner=args.runner,
        command_text=command_text,
        classification=classification,
        log_path=log_path,
    )
    if status == "BLOCKED":
        print(
            f"MOBILE_E2E_LONG_BLOCKED role={role.role} "
            f"classification={classification} evidence={evidence_dir}",
            file=sys.stderr,
        )
        return exit_code_for_blocker(classification)

    print(
        f"MOBILE_E2E_LONG_FAILED role={role.role} "
        f"classification={classification} evidence={evidence_dir}",
        file=sys.stderr,
    )
    return EXIT_FAILED_UX_CONTRACT


def classify_output(output: str) -> str:
    if WIRELESS_PATTERN.search(output) and "Cannot start app" in output:
        return "BLOCKED_VALIDATION_IOS_WIRELESS_PUBLISH_PORT"
    blocked = BLOCKED_PATTERN.search(output)
    if blocked:
        return blocked.group(0)
    if DEVICE_PATTERN.search(output):
        return "BLOCKED_VALIDATION_DEVICE"
    if CONNECTIVITY_PATTERN.search(output):
        return "BLOCKED_VALIDATION_CONNECTIVITY"
    return "FAILED_UX_CONTRACT"


def exit_code_for_blocker(classification: str) -> int:
    if classification in {
        "BLOCKED_VALIDATION_IOS_WIRELESS_PUBLISH_PORT",
        "BLOCKED_VALIDATION_DEVICE",
        "BLOCKED_VALIDATION_MISSING_DEVICE",
    }:
        return EXIT_BLOCKED_DEVICE
    return EXIT_BLOCKED_SETUP


def write_summary(
    *,
    evidence_dir: Path,
    status: str,
    role: MobileRole,
    api: str,
    device: str,
    runner: str,
    command_text: str,
    classification: str,
    log_path: Path | None,
) -> None:
    evidence_dir.mkdir(parents=True, exist_ok=True)
    summary = evidence_dir / "SUMMARY.md"
    summary.write_text(
        "\n".join(
            [
                "# Mobile E2E Long Run",
                "",
                f"Status: {status}",
                f"Papel: {role.role}",
                f"Label: {role.label}",
                f"Suite: {role.suite}",
                f"Setup esperado: {role.setup}",
                f"API: {api}",
                f"Device: {device or 'auto'}",
                f"Runner: {runner}",
                f"Evidencia: {evidence_dir}",
                f"Classificacao: {classification}",
                f"Log: {log_path or 'n/a'}",
                "",
                "## Comando",
                "",
                "```bash",
                command_text,
                "```",
                "",
                "## Limites",
                "",
                "- Este resumo nao imprime valores de fixture ou secrets.",
                "- `BLOCKED_VALIDATION_*` e setup ate prova contraria.",
                "- Falha depois de setup verde deve ser investigada como UX/contrato.",
            ]
        )
        + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

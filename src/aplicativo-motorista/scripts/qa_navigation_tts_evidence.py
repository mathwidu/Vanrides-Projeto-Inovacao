#!/usr/bin/env python3
"""Create and verify technical provenance for the iOS navigation TTS smoke."""

from __future__ import annotations

import argparse
import dataclasses
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
PRODUCER_PATH = ROOT / "scripts/qa_navigation_tts_device_smoke.sh"
VALIDATOR_PATH = Path(__file__).resolve()
TEST_PATH = ROOT / "integration_test/navigation_tts_device_smoke_test.dart"
MANIFEST_FILE_NAME = "navigation-tts-device-smoke.provenance.json"
LOG_FILE_NAME = "navigation-tts-device-smoke.log"
MARKER = "NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY"
SCENARIO = "driver-navigation-repeat"
SCHEMA_VERSION = 2
EVIDENCE_TYPE = "navigation_tts_device_smoke"
RUN_ID_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
GIT_OID_PATTERN = re.compile(r"[0-9a-f]{40,64}")
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")
ALLOWED_FIELDS = {
    "schemaVersion",
    "evidenceType",
    "status",
    "generatedAtUtc",
    "runId",
    "sourceGitSha",
    "sourceGitTree",
    "trackedWorktreeClean",
    "marker",
    "logFile",
    "logSha256",
    "producerFile",
    "producerGitBlob",
    "producerSha256",
    "validatorFile",
    "validatorGitBlob",
    "validatorSha256",
    "testFile",
    "testGitBlob",
    "testSha256",
    "deviceTargetPlatform",
    "noRealDataPolicy",
    "manifestPayloadSha256",
}


class EvidenceError(RuntimeError):
    pass


@dataclasses.dataclass(frozen=True)
class GitFileEvidence:
    path: str
    blob: str
    sha256: str


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def git_bytes(app_root: Path, *args: str) -> bytes:
    result = subprocess.run(
        ["git", "-C", str(app_root), *args],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        if not detail:
            detail = result.stdout.decode("utf-8", errors="replace").strip()
        raise EvidenceError(detail or "git falhou")
    return result.stdout


def git(app_root: Path, *args: str) -> str:
    return git_bytes(app_root, *args).decode("utf-8").strip()


def tracked_worktree_clean(app_root: Path) -> bool:
    return not git(app_root, "status", "--porcelain", "--untracked-files=no")


def tracked_relative_path(app_root: Path, path: Path) -> str:
    relative = relative_path(app_root, path)
    git(app_root, "ls-files", "--error-unmatch", relative)
    return relative


def relative_path(app_root: Path, path: Path) -> str:
    try:
        relative = path.resolve().relative_to(app_root.resolve()).as_posix()
    except ValueError as exc:
        raise EvidenceError(f"arquivo fora do app: {path}") from exc
    _require_safe_relative_path(relative)
    return relative


def marker_for(*, source_sha: str, source_tree: str, run_id: str) -> str:
    _require_git_oid("source sha", source_sha)
    _require_git_oid("source tree", source_tree)
    _require_run_id(run_id)
    return (
        f"{MARKER} source_sha={source_sha} source_tree={source_tree} "
        f"run_id={run_id} scenario={SCENARIO}"
    )


def create_manifest(
    evidence_dir: Path,
    *,
    app_root: Path = ROOT,
    producer_path: Path = PRODUCER_PATH,
    validator_path: Path = VALIDATOR_PATH,
    test_path: Path = TEST_PATH,
    device_target_platform: str,
    run_id: str,
) -> Path:
    evidence_dir = evidence_dir.resolve()
    manifest_path = evidence_dir / MANIFEST_FILE_NAME
    log_path = evidence_dir / LOG_FILE_NAME
    if not evidence_dir.is_dir():
        raise EvidenceError(f"diretorio de evidencia ausente: {evidence_dir}")
    if manifest_path.exists():
        raise EvidenceError(f"manifest ja existe e e imutavel: {manifest_path}")
    if not log_path.is_file():
        raise EvidenceError(f"log ausente: {log_path}")
    if device_target_platform != "ios":
        raise EvidenceError("deviceTargetPlatform deve ser ios")
    _require_run_id(run_id)
    if not tracked_worktree_clean(app_root):
        raise EvidenceError("worktree rastreado deve estar limpo para criar evidencia")

    source_sha = git(app_root, "rev-parse", "--verify", "HEAD^{commit}")
    source_tree = git(app_root, "rev-parse", f"{source_sha}^{{tree}}")
    expected_marker = marker_for(
        source_sha=source_sha,
        source_tree=source_tree,
        run_id=run_id,
    )
    _assert_log_binding(log_path, expected_marker)

    producer = _git_file_evidence(app_root, source_sha, producer_path)
    validator = _git_file_evidence(app_root, source_sha, validator_path)
    integration_test = _git_file_evidence(app_root, source_sha, test_path)
    payload: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "evidenceType": EVIDENCE_TYPE,
        "status": "PASS",
        "generatedAtUtc": datetime.now(timezone.utc)
        .isoformat()
        .replace("+00:00", "Z"),
        "runId": run_id,
        "sourceGitSha": source_sha,
        "sourceGitTree": source_tree,
        "trackedWorktreeClean": True,
        "marker": expected_marker,
        "logFile": LOG_FILE_NAME,
        "logSha256": sha256(log_path),
        "producerFile": producer.path,
        "producerGitBlob": producer.blob,
        "producerSha256": producer.sha256,
        "validatorFile": validator.path,
        "validatorGitBlob": validator.blob,
        "validatorSha256": validator.sha256,
        "testFile": integration_test.path,
        "testGitBlob": integration_test.blob,
        "testSha256": integration_test.sha256,
        "deviceTargetPlatform": device_target_platform,
        "noRealDataPolicy": True,
    }
    payload["manifestPayloadSha256"] = _manifest_payload_sha256(payload)
    try:
        with manifest_path.open("x", encoding="utf-8") as handle:
            handle.write(json.dumps(payload, indent=2, sort_keys=True) + "\n")
    except FileExistsError as exc:
        raise EvidenceError(f"manifest ja existe e e imutavel: {manifest_path}") from exc
    manifest_path.chmod(0o444)
    return manifest_path


def verify_manifest(
    evidence_dir: Path,
    *,
    app_root: Path = ROOT,
    producer_path: Path = PRODUCER_PATH,
    validator_path: Path = VALIDATOR_PATH,
    test_path: Path = TEST_PATH,
    require_current_source: bool = False,
) -> dict[str, Any]:
    evidence_dir = evidence_dir.resolve()
    manifest_path = evidence_dir / MANIFEST_FILE_NAME
    log_path = evidence_dir / LOG_FILE_NAME
    data = _read_manifest(manifest_path)
    _assert_manifest_shape(data)

    actual_manifest_digest = _manifest_payload_sha256(data)
    if data["manifestPayloadSha256"] != actual_manifest_digest:
        raise EvidenceError("manifestPayloadSha256 diverge")

    source_sha = data["sourceGitSha"]
    source_tree = data["sourceGitTree"]
    resolved_source = git(app_root, "rev-parse", "--verify", f"{source_sha}^{{commit}}")
    if resolved_source != source_sha:
        raise EvidenceError("sourceGitSha nao resolve para o commit registrado")
    resolved_tree = git(app_root, "rev-parse", f"{source_sha}^{{tree}}")
    if resolved_tree != source_tree:
        raise EvidenceError("sourceGitTree diverge do commit registrado")

    expected_marker = marker_for(
        source_sha=source_sha,
        source_tree=source_tree,
        run_id=data["runId"],
    )
    if data["marker"] != expected_marker:
        raise EvidenceError("marker diverge da versao/run registrados")
    if data["logFile"] != LOG_FILE_NAME:
        raise EvidenceError("logFile invalido")
    if not log_path.is_file():
        raise EvidenceError(f"log ausente: {log_path}")
    if data["logSha256"] != sha256(log_path):
        raise EvidenceError("logSha256 diverge")
    _assert_log_binding(log_path, expected_marker)

    _assert_git_file_record(
        "producer",
        data,
        _git_file_evidence_at_relative_path(
            app_root, source_sha, data["producerFile"], producer_path
        ),
    )
    _assert_git_file_record(
        "validator",
        data,
        _git_file_evidence_at_relative_path(
            app_root, source_sha, data["validatorFile"], validator_path
        ),
    )
    _assert_git_file_record(
        "test",
        data,
        _git_file_evidence_at_relative_path(
            app_root, source_sha, data["testFile"], test_path
        ),
    )

    if require_current_source:
        current_sha = git(app_root, "rev-parse", "--verify", "HEAD^{commit}")
        current_tree = git(app_root, "rev-parse", "HEAD^{tree}")
        if current_sha != source_sha or current_tree != source_tree:
            raise EvidenceError("evidencia nao pertence a versao atual do app")
        if not tracked_worktree_clean(app_root):
            raise EvidenceError("worktree rastreado atual nao esta limpo")
    return data


def _git_file_evidence(app_root: Path, source_sha: str, path: Path) -> GitFileEvidence:
    relative = tracked_relative_path(app_root, path)
    evidence = _git_file_evidence_at_relative_path(
        app_root, source_sha, relative, expected_path=path
    )
    if path.read_bytes() != git_bytes(app_root, "show", f"{source_sha}:./{relative}"):
        raise EvidenceError(f"arquivo atual diverge do blob versionado: {relative}")
    return evidence


def _git_file_evidence_at_relative_path(
    app_root: Path,
    source_sha: str,
    relative: str,
    expected_path: Path,
) -> GitFileEvidence:
    _require_safe_relative_path(relative)
    expected_relative = relative_path(app_root, expected_path)
    if relative != expected_relative:
        raise EvidenceError(
            f"caminho registrado {relative} diverge do esperado {expected_relative}"
        )
    blob = git(app_root, "rev-parse", f"{source_sha}:./{relative}")
    raw = git_bytes(app_root, "show", f"{source_sha}:./{relative}")
    return GitFileEvidence(path=relative, blob=blob, sha256=sha256_bytes(raw))


def _assert_git_file_record(
    prefix: str, data: dict[str, Any], actual: GitFileEvidence
) -> None:
    expected = {
        f"{prefix}File": actual.path,
        f"{prefix}GitBlob": actual.blob,
        f"{prefix}Sha256": actual.sha256,
    }
    mismatches = [key for key, value in expected.items() if data[key] != value]
    if mismatches:
        raise EvidenceError("manifest diverge em: " + ",".join(sorted(mismatches)))


def _assert_log_binding(log_path: Path, expected_marker: str) -> None:
    log_text = log_path.read_text(encoding="utf-8", errors="replace")
    marker_count = log_text.count(MARKER)
    if marker_count != 1:
        raise EvidenceError(
            f"log deve conter exatamente um marker {MARKER}; encontrou {marker_count}"
        )
    if expected_marker not in log_text:
        raise EvidenceError("marker do log diverge de source SHA/tree/run id")


def _read_manifest(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise EvidenceError(f"manifest ausente: {path}") from exc
    except json.JSONDecodeError as exc:
        raise EvidenceError(f"manifest JSON invalido: {exc}") from exc
    if not isinstance(data, dict):
        raise EvidenceError("manifest deve ser objeto JSON")
    return data


def _assert_manifest_shape(data: dict[str, Any]) -> None:
    unexpected = sorted(set(data).difference(ALLOWED_FIELDS))
    missing = sorted(ALLOWED_FIELDS.difference(data))
    if unexpected:
        raise EvidenceError("campos inesperados no manifest: " + ",".join(unexpected))
    if missing:
        raise EvidenceError("campos ausentes no manifest: " + ",".join(missing))
    expected_values = {
        "schemaVersion": SCHEMA_VERSION,
        "evidenceType": EVIDENCE_TYPE,
        "status": "PASS",
        "trackedWorktreeClean": True,
        "deviceTargetPlatform": "ios",
        "noRealDataPolicy": True,
    }
    mismatches = [key for key, expected in expected_values.items() if data[key] != expected]
    if mismatches:
        raise EvidenceError("manifest diverge em: " + ",".join(sorted(mismatches)))
    _require_run_id(data["runId"])
    _require_git_oid("sourceGitSha", data["sourceGitSha"])
    _require_git_oid("sourceGitTree", data["sourceGitTree"])
    for key in ("producerGitBlob", "validatorGitBlob", "testGitBlob"):
        _require_git_oid(key, data[key])
    for key in (
        "logSha256",
        "producerSha256",
        "validatorSha256",
        "testSha256",
        "manifestPayloadSha256",
    ):
        if not isinstance(data[key], str) or not SHA256_PATTERN.fullmatch(data[key]):
            raise EvidenceError(f"{key} invalido")
    for key in ("marker", "logFile", "producerFile", "validatorFile", "testFile"):
        if not isinstance(data[key], str) or not data[key]:
            raise EvidenceError(f"{key} invalido")
    try:
        generated_at = datetime.fromisoformat(
            data["generatedAtUtc"].replace("Z", "+00:00")
        )
    except (AttributeError, ValueError) as exc:
        raise EvidenceError("generatedAtUtc invalido") from exc
    if not data["generatedAtUtc"].endswith("Z") or generated_at.tzinfo is None:
        raise EvidenceError("generatedAtUtc deve estar em UTC")


def _manifest_payload_sha256(data: dict[str, Any]) -> str:
    payload = dict(data)
    payload.pop("manifestPayloadSha256", None)
    canonical = json.dumps(
        payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False
    ).encode("utf-8")
    return sha256_bytes(canonical)


def _require_run_id(run_id: Any) -> None:
    if not isinstance(run_id, str) or not RUN_ID_PATTERN.fullmatch(run_id):
        raise EvidenceError("runId invalido")


def _require_git_oid(label: str, value: Any) -> None:
    if not isinstance(value, str) or not GIT_OID_PATTERN.fullmatch(value):
        raise EvidenceError(f"{label} invalido")


def _require_safe_relative_path(value: str) -> None:
    path = PurePosixPath(value)
    if path.is_absolute() or not path.parts or ".." in path.parts:
        raise EvidenceError(f"caminho Git invalido: {value}")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Cria e valida proveniencia tecnica do smoke TTS em device."
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check-only", action="store_true")
    mode.add_argument("--create", action="store_true")
    mode.add_argument("--verify", action="store_true")
    parser.add_argument("--evidence-dir", type=Path)
    parser.add_argument("--device-target-platform", default="ios")
    parser.add_argument("--run-id")
    parser.add_argument("--require-current-source", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    if args.check_only:
        print(
            "NAVIGATION_TTS_EVIDENCE_CONTRACT_OK "
            f"schema={SCHEMA_VERSION} human_confirmation=false historical_verify=true"
        )
        return 0
    if args.evidence_dir is None:
        print("--evidence-dir e obrigatorio para --create/--verify", file=sys.stderr)
        return 2
    if args.create and not args.run_id:
        print("--run-id e obrigatorio para --create", file=sys.stderr)
        return 2
    if args.create and args.require_current_source:
        print("--require-current-source so pode acompanhar --verify", file=sys.stderr)
        return 2

    try:
        if args.create:
            manifest_path = create_manifest(
                args.evidence_dir,
                device_target_platform=args.device_target_platform,
                run_id=args.run_id,
            )
            data = verify_manifest(args.evidence_dir)
            print(
                "NAVIGATION_TTS_EVIDENCE_CREATED "
                f"manifest={manifest_path} run_id={data['runId']} "
                f"source_sha={data['sourceGitSha']} "
                f"source_tree={data['sourceGitTree']} log_sha256={data['logSha256']} "
                f"manifest_sha256={sha256(manifest_path)}"
            )
            return 0

        data = verify_manifest(
            args.evidence_dir,
            require_current_source=args.require_current_source,
        )
        print(
            "NAVIGATION_TTS_EVIDENCE_VERIFIED "
            f"run_id={data['runId']} source_sha={data['sourceGitSha']} "
            f"source_tree={data['sourceGitTree']} log_sha256={data['logSha256']} "
            f"manifest_sha256={sha256(args.evidence_dir / MANIFEST_FILE_NAME)}"
        )
        return 0
    except (EvidenceError, OSError) as exc:
        print(f"NAVIGATION_TTS_EVIDENCE_INVALID {exc}", file=sys.stderr)
        return 85


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

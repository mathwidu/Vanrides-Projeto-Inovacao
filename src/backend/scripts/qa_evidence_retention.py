#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import stat
import sys
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Iterable, Mapping, Sequence


LOCAL_RETENTION_DAYS = 30
LOCAL_MINIMUM_PER_SUITE = 20
MAX_MANIFEST_BYTES = 1_048_576
SAFE_IDENTIFIER = re.compile(r"^[A-Za-z0-9._-]+$")
GIT_OBJECT_ID = re.compile(r"^[0-9a-f]{40}(?:[0-9a-f]{24})?$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
DOCKER_API_VERSION = re.compile(
    r"^(?:0|[1-9][0-9]{0,2})\.(?:0|[1-9][0-9]{0,2})$"
)
JAVA_CLASS_NAME = re.compile(
    r"^[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*$"
)
TERMINAL_STATUSES = frozenset({"passed", "failed"})
PROTECTED_RETENTION_CLASSES = frozenset({"ci", "release"})
FAILURE_STAGES = frozenset({"preflight", "maven", "validation", "signal"})
QUARANTINE_SUFFIX = "-retention-quarantine"


class RetentionValidationError(RuntimeError):
    pass


@dataclass(frozen=True)
class EvidenceFileIdentity:
    name: str
    device: int
    inode: int
    size: int
    sha256: str


@dataclass(frozen=True)
class RetentionCandidate:
    path: Path
    run_id: str
    suite: str
    archived_at: datetime
    directory_device: int
    directory_inode: int
    manifest_sha256: str
    evidence_files: tuple[EvidenceFileIdentity, ...]


@dataclass(frozen=True)
class RetentionPlan:
    root: Path
    root_device: int
    root_inode: int
    generated_at: datetime
    active_run_ids: frozenset[str]
    candidates: tuple[RetentionCandidate, ...]
    protected_reasons: Mapping[str, str]
    scanned_count: int

    @property
    def delete_run_ids(self) -> tuple[str, ...]:
        return tuple(candidate.run_id for candidate in self.candidates)


@dataclass(frozen=True)
class _LocalRun:
    candidate: RetentionCandidate
    active: bool


def build_retention_plan(
        root: Path,
        *,
        now: datetime | None = None,
        active_run_ids: Iterable[str] = ()) -> RetentionPlan:
    """Build a conservative local-evidence pruning plan without deleting files."""
    root_stat = _validate_root(root)
    effective_now = _normalized_now(now)
    active = frozenset(active_run_ids)
    for run_id in active:
        if not SAFE_IDENTIFIER.fullmatch(run_id):
            raise RetentionValidationError(f"unsafe active run id: {run_id!r}")

    protected: dict[str, str] = {}
    local_by_suite: dict[str, list[_LocalRun]] = {}
    scanned_count = 0

    with os.scandir(root) as entries:
        for entry in sorted(entries, key=lambda item: item.name):
            if not entry.is_dir(follow_symlinks=False) and not entry.is_symlink():
                continue
            scanned_count += 1
            run_id = entry.name
            path = root / run_id
            if entry.is_symlink():
                protected[run_id] = "symlink-run"
                continue
            if not SAFE_IDENTIFIER.fullmatch(run_id):
                protected[run_id] = "unsafe-run-id"
                continue

            directory_stat = entry.stat(follow_symlinks=False)
            if not stat.S_ISDIR(directory_stat.st_mode) or path.is_symlink():
                protected[run_id] = "symlink-run"
                continue
            if directory_stat.st_uid != os.getuid():
                protected[run_id] = "unsafe-directory-owner"
                continue
            if directory_stat.st_mode & 0o022:
                protected[run_id] = "unsafe-directory-permissions"
                continue

            manifest_path = path / "manifest.json"
            if manifest_path.is_symlink():
                protected[run_id] = "manifest-symlink"
                continue
            if not manifest_path.exists():
                protected[run_id] = "manifest-missing"
                continue
            try:
                manifest_bytes = _read_regular_manifest(manifest_path)
                manifest = _load_manifest(manifest_bytes)
            except (OSError, UnicodeDecodeError, json.JSONDecodeError, RetentionValidationError):
                protected[run_id] = "manifest-invalid"
                continue
            if not isinstance(manifest, dict):
                protected[run_id] = "manifest-invalid"
                continue

            retention_class = manifest.get("retentionClass", "unknown")
            if retention_class in PROTECTED_RETENTION_CLASSES:
                protected[run_id] = "protected-retention-class"
                continue
            if retention_class != "local" or manifest.get("schemaVersion") != 2:
                protected[run_id] = "unknown-retention-class"
                continue
            if manifest.get("status") not in TERMINAL_STATUSES:
                protected[run_id] = "nonterminal-status"
                continue
            if _contains_symlink(path):
                protected[run_id] = "contains-symlink"
                continue

            try:
                suite, archived_at, evidence_files = _validate_local_terminal_manifest(
                    manifest,
                    run_id=run_id,
                    run_path=path,
                )
            except RetentionValidationError:
                protected[run_id] = "manifest-invalid"
                continue
            if archived_at > effective_now:
                protected[run_id] = "future-archive-time"
                continue

            candidate = RetentionCandidate(
                path=path,
                run_id=run_id,
                suite=suite,
                archived_at=archived_at,
                directory_device=directory_stat.st_dev,
                directory_inode=directory_stat.st_ino,
                manifest_sha256=hashlib.sha256(manifest_bytes).hexdigest(),
                evidence_files=evidence_files,
            )
            local_by_suite.setdefault(suite, []).append(
                _LocalRun(candidate=candidate, active=run_id in active)
            )

    cutoff = effective_now - timedelta(days=LOCAL_RETENTION_DAYS)
    deletions: list[RetentionCandidate] = []
    for suite_runs in local_by_suite.values():
        ordered = sorted(
            suite_runs,
            key=lambda item: (
                item.candidate.archived_at,
                item.candidate.run_id,
            ),
            reverse=True,
        )
        for index, local_run in enumerate(ordered):
            candidate = local_run.candidate
            if local_run.active:
                protected[candidate.run_id] = "active-run"
            elif index < LOCAL_MINIMUM_PER_SUITE:
                protected[candidate.run_id] = "minimum-per-suite"
            elif candidate.archived_at >= cutoff:
                protected[candidate.run_id] = "within-retention-window"
            else:
                deletions.append(candidate)

    deletions.sort(key=lambda item: (item.suite, item.archived_at, item.run_id))
    return RetentionPlan(
        root=root,
        root_device=root_stat.st_dev,
        root_inode=root_stat.st_ino,
        generated_at=effective_now,
        active_run_ids=active,
        candidates=tuple(deletions),
        protected_reasons=dict(sorted(protected.items())),
        scanned_count=scanned_count,
    )


def apply_retention_plan(plan: RetentionPlan) -> tuple[str, ...]:
    """Apply an already-reviewed plan after revalidating every destructive target."""
    if not shutil.rmtree.avoids_symlink_attacks:
        raise RetentionValidationError(
            "platform does not provide symlink-safe recursive removal"
        )
    root_stat = _validate_root(plan.root)
    if (
        root_stat.st_dev != plan.root_device
        or root_stat.st_ino != plan.root_inode
    ):
        raise RetentionValidationError("evidence root changed after planning")

    root_descriptor = _open_directory(plan.root)
    try:
        opened_root = os.fstat(root_descriptor)
        if (
            opened_root.st_dev != plan.root_device
            or opened_root.st_ino != plan.root_inode
        ):
            raise RetentionValidationError("evidence root changed after planning")

        current_plan = build_retention_plan(
            plan.root,
            now=plan.generated_at,
            active_run_ids=plan.active_run_ids,
        )
        if current_plan.candidates != plan.candidates:
            raise RetentionValidationError("retention plan changed after planning")

        for candidate in plan.candidates:
            _revalidate_candidate(candidate, plan.root)

        quarantine_root = _ensure_quarantine_root(plan.root)
        quarantine_descriptor = _open_directory(quarantine_root)
        try:
            quarantine_stat = os.fstat(quarantine_descriptor)
            if quarantine_stat.st_dev != opened_root.st_dev:
                raise RetentionValidationError(
                    "retention quarantine must share the evidence filesystem"
                )
            _validate_open_directory(quarantine_root, quarantine_descriptor)

            removed: list[str] = []
            for candidate in plan.candidates:
                _revalidate_candidate(candidate, plan.root)
                quarantined = _move_candidate_to_quarantine(
                    candidate,
                    evidence_root=plan.root,
                    quarantine_root=quarantine_root,
                    evidence_descriptor=root_descriptor,
                    quarantine_descriptor=quarantine_descriptor,
                )
                try:
                    _validate_open_directory(
                        quarantine_root,
                        quarantine_descriptor,
                    )
                    _validate_candidate_entry(
                        candidate,
                        quarantine_descriptor,
                        quarantined.name,
                    )
                    _revalidate_candidate(
                        candidate,
                        quarantine_root,
                        candidate_path=quarantined,
                        require_canonical_name=False,
                    )
                    _validate_candidate_entry(
                        candidate,
                        quarantine_descriptor,
                        quarantined.name,
                    )
                except (OSError, RetentionValidationError) as error:
                    raise RetentionValidationError(
                        f"run {candidate.run_id} changed after quarantine; "
                        "preserved in quarantine for manual recovery"
                    ) from error

                _remove_from_open_directory(
                    quarantine_descriptor,
                    quarantined.name,
                )
                if _entry_exists(quarantine_descriptor, quarantined.name):
                    raise RetentionValidationError(
                        f"quarantined run {candidate.run_id} was not fully removed"
                    )
                _fsync_descriptor(quarantine_descriptor)
                removed.append(candidate.run_id)
            return tuple(removed)
        finally:
            os.close(quarantine_descriptor)
    finally:
        os.close(root_descriptor)


def _ensure_quarantine_root(evidence_root: Path) -> Path:
    quarantine_root = (
        evidence_root.parent
        / f".{evidence_root.name}{QUARANTINE_SUFFIX}"
    )
    try:
        quarantine_root.mkdir(mode=0o700)
    except FileExistsError:
        pass
    try:
        quarantine_stat = quarantine_root.lstat()
    except OSError as error:
        raise RetentionValidationError(
            f"cannot inspect retention quarantine: {error}"
        ) from error
    if (
        not stat.S_ISDIR(quarantine_stat.st_mode)
        or quarantine_root.is_symlink()
        or quarantine_stat.st_uid != os.getuid()
        or quarantine_stat.st_mode & 0o077
    ):
        raise RetentionValidationError(
            "retention quarantine must be a private owned directory"
        )
    return quarantine_root


def _move_candidate_to_quarantine(
        candidate: RetentionCandidate,
        *,
        evidence_root: Path,
        quarantine_root: Path,
        evidence_descriptor: int,
        quarantine_descriptor: int) -> Path:
    quarantined_name: str | None = None
    for _ in range(32):
        candidate_name = f"{candidate.run_id}-{secrets.token_hex(16)}"
        if not _entry_exists(quarantine_descriptor, candidate_name):
            quarantined_name = candidate_name
            break
    if quarantined_name is None:
        raise RetentionValidationError("cannot allocate unique quarantine path")

    _validate_open_directory(evidence_root, evidence_descriptor)
    _validate_open_directory(quarantine_root, quarantine_descriptor)
    _validate_candidate_entry(
        candidate,
        evidence_descriptor,
        candidate.run_id,
    )
    try:
        os.rename(
            candidate.run_id,
            quarantined_name,
            src_dir_fd=evidence_descriptor,
            dst_dir_fd=quarantine_descriptor,
        )
        _fsync_descriptor(evidence_descriptor)
        _fsync_descriptor(quarantine_descriptor)
    except OSError as error:
        raise RetentionValidationError(
            f"cannot quarantine run {candidate.run_id}: {error}"
        ) from error
    return quarantine_root / quarantined_name


def _open_directory(path: Path) -> int:
    flags = os.O_RDONLY
    if hasattr(os, "O_DIRECTORY"):
        flags |= os.O_DIRECTORY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    return os.open(path, flags)


def _validate_open_directory(path: Path, descriptor: int) -> None:
    path_stat = path.lstat()
    descriptor_stat = os.fstat(descriptor)
    if (
        not stat.S_ISDIR(path_stat.st_mode)
        or path.is_symlink()
        or path_stat.st_dev != descriptor_stat.st_dev
        or path_stat.st_ino != descriptor_stat.st_ino
        or descriptor_stat.st_uid != os.getuid()
    ):
        raise RetentionValidationError("open retention directory identity changed")


def _validate_candidate_entry(
        candidate: RetentionCandidate,
        directory_descriptor: int,
        name: str) -> None:
    entry_stat = os.stat(
        name,
        dir_fd=directory_descriptor,
        follow_symlinks=False,
    )
    if (
        not stat.S_ISDIR(entry_stat.st_mode)
        or entry_stat.st_dev != candidate.directory_device
        or entry_stat.st_ino != candidate.directory_inode
    ):
        raise RetentionValidationError(
            f"run {candidate.run_id} changed after planning"
        )


def _entry_exists(directory_descriptor: int, name: str) -> bool:
    try:
        os.stat(name, dir_fd=directory_descriptor, follow_symlinks=False)
    except FileNotFoundError:
        return False
    return True


def _remove_from_open_directory(
        directory_descriptor: int,
        name: str) -> None:
    original_directory = _open_directory(Path("."))
    try:
        os.fchdir(directory_descriptor)
        shutil.rmtree(name)
    finally:
        os.fchdir(original_directory)
        os.close(original_directory)


def _fsync_descriptor(descriptor: int) -> None:
    os.fsync(descriptor)


def _validate_root(root: Path) -> os.stat_result:
    if not root.is_absolute():
        raise RetentionValidationError("evidence root must be absolute")
    if root == Path(root.anchor):
        raise RetentionValidationError("filesystem root is not a valid evidence root")
    if root.is_symlink():
        raise RetentionValidationError("evidence root must not be a symlink")
    try:
        root_stat = root.stat()
    except OSError as error:
        raise RetentionValidationError(f"cannot inspect evidence root: {error}") from error
    if not stat.S_ISDIR(root_stat.st_mode):
        raise RetentionValidationError("evidence root must be a directory")
    if root_stat.st_uid != os.getuid():
        raise RetentionValidationError("evidence root must be owned by the current user")
    if root_stat.st_mode & 0o022:
        raise RetentionValidationError("evidence root has unsafe permissions")
    return root_stat


def _normalized_now(now: datetime | None) -> datetime:
    value = now or datetime.now(timezone.utc)
    if value.tzinfo is None:
        raise RetentionValidationError("current time must be timezone-aware")
    return value.astimezone(timezone.utc)


def _parse_archived_at(value: object) -> datetime:
    if not isinstance(value, str) or len(value) > 64:
        raise RetentionValidationError("invalid archivedAtUtc")
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError as error:
        raise RetentionValidationError("invalid archivedAtUtc") from error
    if parsed.tzinfo is None:
        raise RetentionValidationError("archivedAtUtc must be timezone-aware")
    return parsed.astimezone(timezone.utc)


def _read_regular_manifest(path: Path) -> bytes:
    before = path.lstat()
    if (
        not stat.S_ISREG(before.st_mode)
        or before.st_uid != os.getuid()
        or before.st_mode & 0o022
        or before.st_size > MAX_MANIFEST_BYTES
    ):
        raise RetentionValidationError("manifest is not a safe regular file")
    content = path.read_bytes()
    after = path.lstat()
    if (
        before.st_dev != after.st_dev
        or before.st_ino != after.st_ino
        or before.st_mtime_ns != after.st_mtime_ns
        or before.st_size != after.st_size
        or len(content) != after.st_size
    ):
        raise RetentionValidationError("manifest changed while being read")
    return content


def _load_manifest(content: bytes) -> object:
    def reject_duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
        result: dict[str, object] = {}
        for key, value in pairs:
            if key in result:
                raise RetentionValidationError(f"duplicate manifest key: {key}")
            result[key] = value
        return result

    def reject_nonstandard_constant(value: str) -> object:
        raise RetentionValidationError(f"nonstandard JSON constant: {value}")

    return json.loads(
        content.decode("utf-8"),
        object_pairs_hook=reject_duplicate_keys,
        parse_constant=reject_nonstandard_constant,
    )


def _validate_local_terminal_manifest(
        manifest: Mapping[str, object],
        *,
        run_id: str,
        run_path: Path) -> tuple[str, datetime, tuple[EvidenceFileIdentity, ...]]:
    common_keys = {
        "schemaVersion",
        "status",
        "suite",
        "runId",
        "startedAtEpoch",
        "archivedAtUtc",
        "retentionClass",
        "eligibleForRelease",
        "source",
    }
    status = manifest.get("status")
    allowed_keys = set(common_keys)
    if status == "passed":
        allowed_keys.update({"commandProfile", "environment", "totals", "reports"})
        if "allReports" in manifest:
            allowed_keys.add("allReports")
    elif status == "failed":
        allowed_keys.add("failure")
    else:
        raise RetentionValidationError("manifest status is not terminal")
    if set(manifest) != allowed_keys:
        raise RetentionValidationError("manifest fields do not match schema v2")

    suite = manifest.get("suite")
    if not isinstance(suite, str) or not SAFE_IDENTIFIER.fullmatch(suite):
        raise RetentionValidationError("manifest suite is invalid")
    if manifest.get("runId") != run_id:
        raise RetentionValidationError("manifest run id does not match directory")
    started_epoch = _positive_int(manifest.get("startedAtEpoch"), "startedAtEpoch")
    archived_at = _parse_archived_at(manifest.get("archivedAtUtc"))
    if archived_at.timestamp() < started_epoch:
        raise RetentionValidationError("manifest archive time precedes run start")
    if manifest.get("eligibleForRelease") is not False:
        raise RetentionValidationError("local manifest cannot be release eligible")

    source = manifest.get("source")
    if not isinstance(source, dict) or set(source) != {
        "gitSha",
        "gitTree",
        "runStartGitSha",
        "runStartGitTree",
        "clean",
        "dirtyPathCount",
        "cleanRequired",
    }:
        raise RetentionValidationError("manifest source is incomplete")
    for key in ("gitSha", "gitTree", "runStartGitSha", "runStartGitTree"):
        value = source.get(key)
        if not isinstance(value, str) or not GIT_OBJECT_ID.fullmatch(value):
            raise RetentionValidationError(f"manifest source {key} is invalid")
    dirty_path_count = _nonnegative_int(
        source.get("dirtyPathCount"),
        "source.dirtyPathCount",
    )
    if source.get("clean") is not (dirty_path_count == 0):
        raise RetentionValidationError("manifest clean state is inconsistent")
    if not isinstance(source.get("cleanRequired"), bool):
        raise RetentionValidationError("manifest cleanRequired is invalid")

    if status == "passed":
        evidence_files = _validate_passed_manifest(
            manifest,
            suite=suite,
            source=source,
            run_path=run_path,
        )
    else:
        _validate_failed_manifest(manifest, run_path=run_path)
        evidence_files = ()
    return suite, archived_at, evidence_files


def _validate_passed_manifest(
        manifest: Mapping[str, object],
        *,
        suite: str,
        source: Mapping[str, object],
        run_path: Path) -> tuple[EvidenceFileIdentity, ...]:
    command_profile = manifest.get("commandProfile")
    environment = manifest.get("environment")
    _validate_public_execution_metadata(
        suite=suite,
        command_profile=command_profile,
        environment=environment,
    )
    if source["gitSha"] != source["runStartGitSha"] \
            or source["gitTree"] != source["runStartGitTree"]:
        raise RetentionValidationError("passed manifest source drifted")
    if source["cleanRequired"] and not source["clean"]:
        raise RetentionValidationError("passed manifest violated clean requirement")

    totals = _validated_green_totals(manifest.get("totals"), "totals")
    primary_reports = _validated_report_entries(
        manifest.get("reports"),
        label="reports",
        include_outcomes=False,
    )
    report_test_total = sum(entry["tests"] for entry in primary_reports.values())
    if report_test_total != totals["tests"]:
        raise RetentionValidationError("passed report totals are inconsistent")

    bundle_reports = primary_reports
    if "allReports" in manifest:
        all_reports = manifest["allReports"]
        if not isinstance(all_reports, dict) or set(all_reports) != {
            "requiredGreen", "reportCount", "totals", "reports"
        }:
            raise RetentionValidationError("allReports payload is invalid")
        if all_reports.get("requiredGreen") is not True:
            raise RetentionValidationError("allReports is not required green")
        all_report_entries = _validated_report_entries(
            all_reports.get("reports"),
            label="allReports.reports",
            include_outcomes=True,
        )
        if _positive_int(all_reports.get("reportCount"), "allReports.reportCount") \
                != len(all_report_entries):
            raise RetentionValidationError("allReports count is inconsistent")
        all_totals = _validated_green_totals(
            all_reports.get("totals"),
            "allReports.totals",
        )
        if sum(entry["tests"] for entry in all_report_entries.values()) \
                != all_totals["tests"]:
            raise RetentionValidationError("allReports totals are inconsistent")
        for filename, primary in primary_reports.items():
            expanded = all_report_entries.get(filename)
            if (
                expanded is None
                or expanded["className"] != primary["className"]
                or expanded["tests"] != primary["tests"]
                or expanded["sha256"] != primary["sha256"]
            ):
                raise RetentionValidationError(
                    "primary report is inconsistent with allReports"
                )
        bundle_reports = all_report_entries

    expected_names = {"manifest.json", *bundle_reports}
    with os.scandir(run_path) as entries:
        actual_names = {entry.name for entry in entries}
    if actual_names != expected_names:
        raise RetentionValidationError("passed evidence bundle has unexpected entries")
    return tuple(
        _capture_evidence_file(run_path / filename, expected["sha256"])
        for filename, expected in sorted(bundle_reports.items())
    )


def _validate_public_execution_metadata(
        *,
        suite: str,
        command_profile: object,
        environment: object) -> None:
    if not isinstance(environment, dict):
        raise RetentionValidationError("passed manifest environment is invalid")
    if suite == "rbac":
        if command_profile != "maven-rbac-suite" or set(environment) != {
            "userHomeIsolated"
        }:
            raise RetentionValidationError("RBAC execution metadata is invalid")
        if not isinstance(environment.get("userHomeIsolated"), bool):
            raise RetentionValidationError("RBAC userHomeIsolated is invalid")
        return

    if suite != "integration":
        raise RetentionValidationError("passed manifest suite is unsupported")
    expected_environment_keys = {
        "scope",
        "testcontainersApiVersion",
        "dockerMinApiVersion",
        "dockerMaxApiVersion",
        "dockerEndpointTransport",
        "mavenRcSkipped",
        "userHomeIsolated",
    }
    if set(environment) != expected_environment_keys:
        raise RetentionValidationError("integration execution metadata is invalid")
    scope = environment.get("scope")
    if scope not in {"focused", "all-tests"}:
        raise RetentionValidationError("integration scope is invalid")
    if command_profile != f"maven-integration-{scope}":
        raise RetentionValidationError("integration command profile is invalid")
    for key in (
        "testcontainersApiVersion",
        "dockerMinApiVersion",
        "dockerMaxApiVersion",
    ):
        value = environment.get(key)
        if not isinstance(value, str) or not DOCKER_API_VERSION.fullmatch(value):
            raise RetentionValidationError("integration Docker API metadata is invalid")
    if environment.get("dockerEndpointTransport") not in {"unix", "npipe"}:
        raise RetentionValidationError("integration Docker transport is invalid")
    if environment.get("mavenRcSkipped") is not True:
        raise RetentionValidationError("integration mavenRcSkipped is invalid")
    if environment.get("userHomeIsolated") is not True:
        raise RetentionValidationError("integration userHomeIsolated is invalid")


def _validate_failed_manifest(
        manifest: Mapping[str, object],
        *,
        run_path: Path) -> None:
    failure = manifest.get("failure")
    if not isinstance(failure, dict) or set(failure) != {"stage", "exitCode"}:
        raise RetentionValidationError("failed manifest payload is invalid")
    if failure.get("stage") not in FAILURE_STAGES:
        raise RetentionValidationError("failed manifest stage is invalid")
    exit_code = failure.get("exitCode")
    if (
        not isinstance(exit_code, int)
        or isinstance(exit_code, bool)
        or not 1 <= exit_code <= 255
    ):
        raise RetentionValidationError("failed manifest exit code is invalid")
    with os.scandir(run_path) as entries:
        if {entry.name for entry in entries} != {"manifest.json"}:
            raise RetentionValidationError(
                "failed evidence bundle has unexpected entries"
            )


def _validated_report_entries(
        value: object,
        *,
        label: str,
        include_outcomes: bool) -> dict[str, dict[str, object]]:
    if not isinstance(value, list) or not value:
        raise RetentionValidationError(f"{label} is empty")
    required_keys = {"className", "file", "tests", "mtimeNs", "sha256"}
    if include_outcomes:
        required_keys.update({"skipped", "failures", "errors", "inode"})
    validated: dict[str, dict[str, object]] = {}
    for report in value:
        if not isinstance(report, dict) or set(report) != required_keys:
            raise RetentionValidationError(f"{label} entry is invalid")
        class_name = report.get("className")
        filename = report.get("file")
        if (
            not isinstance(class_name, str)
            or not JAVA_CLASS_NAME.fullmatch(class_name)
            or filename != f"TEST-{class_name}.xml"
            or filename in validated
        ):
            raise RetentionValidationError(f"{label} identity is invalid")
        tests = _positive_int(report.get("tests"), f"{label}.tests")
        _positive_int(report.get("mtimeNs"), f"{label}.mtimeNs")
        digest = report.get("sha256")
        if not isinstance(digest, str) or not SHA256.fullmatch(digest):
            raise RetentionValidationError(f"{label} digest is invalid")
        if include_outcomes:
            _positive_int(report.get("inode"), f"{label}.inode")
            for outcome in ("skipped", "failures", "errors"):
                if _nonnegative_int(
                    report.get(outcome),
                    f"{label}.{outcome}",
                ) != 0:
                    raise RetentionValidationError(f"{label} is not green")
        validated[filename] = {
            "className": class_name,
            "tests": tests,
            "sha256": digest,
        }
    return validated


def _capture_evidence_file(
        path: Path,
        expected_sha256: str) -> EvidenceFileIdentity:
    try:
        before = path.lstat()
        if (
            not stat.S_ISREG(before.st_mode)
            or before.st_uid != os.getuid()
            or before.st_mode & 0o022
        ):
            raise RetentionValidationError("evidence report file is unsafe")
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        after = path.lstat()
    except OSError as error:
        raise RetentionValidationError("evidence report file is unreadable") from error
    if (
        before.st_dev != after.st_dev
        or before.st_ino != after.st_ino
        or before.st_mtime_ns != after.st_mtime_ns
        or before.st_size != after.st_size
        or digest.hexdigest() != expected_sha256
    ):
        raise RetentionValidationError("evidence report file changed or mismatched")
    return EvidenceFileIdentity(
        name=path.name,
        device=after.st_dev,
        inode=after.st_ino,
        size=after.st_size,
        sha256=digest.hexdigest(),
    )


def _validated_green_totals(value: object, label: str) -> dict[str, int]:
    if not isinstance(value, dict) or set(value) != {
        "tests", "skipped", "failures", "errors"
    }:
        raise RetentionValidationError(f"{label} is invalid")
    totals = {
        key: _nonnegative_int(value.get(key), f"{label}.{key}")
        for key in ("tests", "skipped", "failures", "errors")
    }
    if totals["tests"] <= 0 or any(
        totals[key] != 0 for key in ("skipped", "failures", "errors")
    ):
        raise RetentionValidationError(f"{label} is not green")
    return totals


def _positive_int(value: object, label: str) -> int:
    result = _nonnegative_int(value, label)
    if result <= 0:
        raise RetentionValidationError(f"{label} must be positive")
    return result


def _nonnegative_int(value: object, label: str) -> int:
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise RetentionValidationError(f"{label} must be a nonnegative integer")
    return value


def _contains_symlink(root: Path) -> bool:
    for current_root, directory_names, file_names in os.walk(root, followlinks=False):
        current = Path(current_root)
        for name in (*directory_names, *file_names):
            if (current / name).is_symlink():
                return True
    return False


def _revalidate_candidate(
        candidate: RetentionCandidate,
        root: Path,
        *,
        candidate_path: Path | None = None,
        require_canonical_name: bool = True) -> None:
    path = candidate_path or candidate.path
    if path.parent != root or (
        require_canonical_name and path.name != candidate.run_id
    ):
        raise RetentionValidationError("retention candidate escaped the evidence root")
    if path.is_symlink():
        raise RetentionValidationError(
            f"run {candidate.run_id} changed after planning"
        )
    try:
        directory_stat = path.stat()
        manifest_bytes = _read_regular_manifest(path / "manifest.json")
        manifest = _load_manifest(manifest_bytes)
        if not isinstance(manifest, dict):
            raise RetentionValidationError("manifest changed after planning")
        suite, archived_at, evidence_files = _validate_local_terminal_manifest(
            manifest,
            run_id=candidate.run_id,
            run_path=path,
        )
    except (
        OSError,
        UnicodeDecodeError,
        json.JSONDecodeError,
        RetentionValidationError,
    ) as error:
        raise RetentionValidationError(
            f"run {candidate.run_id} changed after planning"
        ) from error
    if (
        directory_stat.st_dev != candidate.directory_device
        or directory_stat.st_ino != candidate.directory_inode
        or directory_stat.st_uid != os.getuid()
        or directory_stat.st_mode & 0o022
        or hashlib.sha256(manifest_bytes).hexdigest() != candidate.manifest_sha256
        or suite != candidate.suite
        or archived_at != candidate.archived_at
        or evidence_files != candidate.evidence_files
    ):
        raise RetentionValidationError(
            f"run {candidate.run_id} changed after planning"
        )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Plan local Maven evidence retention. Dry-run is the default; "
            "--apply deletes only revalidated local candidates."
        )
    )
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument(
        "--active-run-id",
        action="append",
        default=[],
        metavar="RUN_ID",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        plan = build_retention_plan(
            args.root,
            active_run_ids=args.active_run_id,
        )
        removed = apply_retention_plan(plan) if args.apply else ()
    except (OSError, RetentionValidationError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1

    mode = "apply" if args.apply else "dry-run"
    for run_id in plan.delete_run_ids:
        action = "REMOVED" if run_id in removed else "WOULD_REMOVE"
        print(f"{action}: {run_id}")
    print(
        f"SUMMARY: mode={mode} scanned={plan.scanned_count} "
        f"eligible={len(plan.candidates)} removed={len(removed)} "
        f"protected={len(plan.protected_reasons)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

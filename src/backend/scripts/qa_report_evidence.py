#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Mapping, Sequence


SAFE_IDENTIFIER = re.compile(r"^[A-Za-z0-9._-]+$")
GIT_OBJECT_ID = re.compile(r"^[0-9a-f]{40}(?:[0-9a-f]{24})?$")
JAVA_CLASS_NAME = re.compile(
    r"^[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*$"
)
RESERVED_ENVIRONMENT_METADATA_KEYS = frozenset({
    "userHome",
    "userHomeIsolated",
})
RETENTION_CLASSES = frozenset({"local", "ci", "release"})
FAILURE_STAGES = frozenset({"preflight", "maven", "validation", "signal"})
PREPARED_MANIFEST_NAME = "manifest.json"
PREPARED_ROOT_NAME = ".qa-evidence-prepared"
COMMITTING_SUFFIX = ".committing"
MAX_MANIFEST_BYTES = 1_048_576
GIT_ENVIRONMENT_ALLOWLIST = frozenset({"PATH"})
CONTROLLED_GIT_ENVIRONMENT = {
    "GIT_CONFIG_GLOBAL": os.devnull,
    "GIT_CONFIG_NOSYSTEM": "1",
    "GIT_NO_REPLACE_OBJECTS": "1",
    "GIT_OPTIONAL_LOCKS": "0",
    "GIT_TERMINAL_PROMPT": "0",
    "LANG": "C",
    "LC_ALL": "C",
}
GIT_CONFIG_OVERRIDES = (
    "-c", "core.fsmonitor=false",
    "-c", "core.untrackedCache=false",
)
FILE_READ_CHUNK_BYTES = 1024 * 1024
INTEGRATION_METADATA_KEYS = frozenset({
    "scope",
    "testcontainersApiVersion",
    "dockerMinApiVersion",
    "dockerMaxApiVersion",
    "dockerEndpointTransport",
    "mavenHomeIsolated",
    "mavenRcSkipped",
})
DOCKER_API_VERSION = re.compile(r"^[0-9]{1,3}\.[0-9]{1,3}$")


class EvidenceValidationError(RuntimeError):
    pass


@dataclass(frozen=True)
class SourceState:
    git_sha: str
    git_tree: str
    dirty_path_count: int
    dirty_state_sha256: str = ""


@dataclass(frozen=True)
class EvidenceRequest:
    suite: str
    run_id: str
    started_epoch: int
    reports_dir: Path
    evidence_dir: Path
    expected_classes: Mapping[str, int]
    source_state: SourceState
    require_clean: bool
    metadata: Mapping[str, str]
    expected_user_home: str | None = None
    require_all_reports_green: bool = False
    expected_git_sha: str | None = None
    expected_git_tree: str | None = None
    retention_class: str = "local"


@dataclass(frozen=True)
class FailureEvidenceRequest:
    suite: str
    run_id: str
    started_epoch: int
    evidence_dir: Path
    source_state: SourceState
    clean_required: bool
    failure_stage: str
    exit_code: int
    retention_class: str
    expected_git_sha: str
    expected_git_tree: str


@dataclass(frozen=True)
class ValidatedReport:
    class_name: str
    tests: int
    skipped: int
    failures: int
    errors: int
    mtime_ns: int
    inode: int
    content: bytes
    archived_content: bytes

    @property
    def filename(self) -> str:
        return f"TEST-{self.class_name}.xml"

    @property
    def sha256(self) -> str:
        return hashlib.sha256(self.archived_content).hexdigest()


def capture_source_state(repo_root: Path) -> SourceState:
    """Capture one stable source snapshot from exactly ``repo_root``.

    Git repository-selection variables are deliberately not inherited.  The
    explicit top-level check also prevents ``-C`` from silently discovering a
    parent repository when the caller supplied the wrong directory.  Two
    complete reads are required so a concurrently changing index/worktree
    fails closed instead of yielding a mixed provenance snapshot.
    """
    canonical_root = _canonical_repository_root(repo_root)
    git_executable = _resolve_git_executable()
    git_environment = _sanitized_git_environment()

    first = _capture_source_snapshot(
        canonical_root,
        git_executable=git_executable,
        git_environment=git_environment,
    )
    second = _capture_source_snapshot(
        canonical_root,
        git_executable=git_executable,
        git_environment=git_environment,
    )
    if first != second:
        raise EvidenceValidationError(
            "Git source state changed while provenance was being captured"
        )
    return first


def _canonical_repository_root(repo_root: Path) -> Path:
    try:
        canonical_root = repo_root.resolve(strict=True)
    except OSError as error:
        raise EvidenceValidationError(
            "explicit repository root cannot be resolved"
        ) from error
    if not canonical_root.is_dir():
        raise EvidenceValidationError("explicit repository root is not a directory")
    return canonical_root


def _resolve_git_executable() -> str:
    candidate = shutil.which("git")
    if candidate is None:
        raise EvidenceValidationError("Git executable is unavailable")
    try:
        resolved = Path(candidate).resolve(strict=True)
    except OSError as error:
        raise EvidenceValidationError("Git executable cannot be resolved") from error
    if not resolved.is_file() or not os.access(resolved, os.X_OK):
        raise EvidenceValidationError("Git executable is not executable")
    return str(resolved)


def _sanitized_git_environment() -> dict[str, str]:
    environment = {
        key: value
        for key in GIT_ENVIRONMENT_ALLOWLIST
        if (value := os.environ.get(key)) is not None
    }
    environment.update(CONTROLLED_GIT_ENVIRONMENT)
    return environment


def _run_git_bytes(
        repo_root: Path,
        *arguments: str,
        git_executable: str,
        git_environment: Mapping[str, str]) -> bytes:
    try:
        result = subprocess.run(
            [
                git_executable,
                *GIT_CONFIG_OVERRIDES,
                "-C", str(repo_root),
                *arguments,
            ],
            cwd=repo_root,
            env=dict(git_environment),
            check=True,
            capture_output=True,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        raise EvidenceValidationError("canonical Git source capture failed") from error
    if not isinstance(result.stdout, bytes):
        raise EvidenceValidationError("Git source capture did not return raw bytes")
    return result.stdout


def _capture_source_snapshot(
        repo_root: Path,
        *,
        git_executable: str,
        git_environment: Mapping[str, str]) -> SourceState:
    def git(*arguments: str) -> bytes:
        return _run_git_bytes(
            repo_root,
            *arguments,
            git_executable=git_executable,
            git_environment=git_environment,
        )

    reported_root_bytes = git("rev-parse", "--show-toplevel")
    reported_root = _parse_reported_repository_root(reported_root_bytes)
    if reported_root != repo_root:
        raise EvidenceValidationError(
            "Git top-level does not match the explicit repository root"
        )

    git_sha = _parse_git_object_id(git("rev-parse", "--verify", "HEAD"), "HEAD")
    git_tree = _parse_git_object_id(
        git("rev-parse", "--verify", "HEAD^{tree}"),
        "HEAD tree",
    )
    porcelain = git(
        "status",
        "--porcelain=v1",
        "--untracked-files=all",
        "--ignore-submodules=none",
    )
    index_diff = git(
        "diff",
        "--cached",
        "--binary",
        "--full-index",
        "--no-color",
        "--no-ext-diff",
        "--no-textconv",
        "--no-renames",
        "--ignore-submodules=none",
        "HEAD",
        "--",
    )
    worktree_diff = git(
        "diff",
        "--binary",
        "--full-index",
        "--no-color",
        "--no-ext-diff",
        "--no-textconv",
        "--no-renames",
        "--ignore-submodules=none",
        "--",
    )
    untracked_listing = git(
        "ls-files",
        "--others",
        "--exclude-standard",
        "-z",
    )
    untracked_digest = _untracked_state_digest(repo_root, untracked_listing)
    dirty_path_count = len(porcelain.splitlines()) if porcelain else 0
    dirty_state_sha256 = _framed_sha256((
        (b"porcelain-v1", porcelain),
        (b"index-diff-binary", index_diff),
        (b"worktree-diff-binary", worktree_diff),
        (b"untracked-listing", untracked_listing),
        (b"untracked-state-sha256", untracked_digest.encode("ascii")),
    ))
    return SourceState(
        git_sha=git_sha,
        git_tree=git_tree,
        dirty_path_count=dirty_path_count,
        dirty_state_sha256=dirty_state_sha256,
    )


def _parse_reported_repository_root(output: bytes) -> Path:
    raw = output[:-1] if output.endswith(b"\n") else output
    if raw.endswith(b"\r"):
        raw = raw[:-1]
    if not raw or b"\n" in raw or b"\r" in raw:
        raise EvidenceValidationError("Git returned an invalid top-level path")
    try:
        return Path(os.fsdecode(raw)).resolve(strict=True)
    except OSError as error:
        raise EvidenceValidationError("Git top-level path cannot be resolved") from error


def _parse_git_object_id(output: bytes, label: str) -> str:
    try:
        value = output.decode("ascii").strip()
    except UnicodeDecodeError as error:
        raise EvidenceValidationError(f"Git returned an invalid {label}") from error
    if not GIT_OBJECT_ID.fullmatch(value):
        raise EvidenceValidationError(f"Git returned an invalid {label}")
    return value


def _framed_hash_update(digest: object, label: bytes, value: bytes) -> None:
    digest.update(len(label).to_bytes(4, "big"))
    digest.update(label)
    digest.update(len(value).to_bytes(8, "big"))
    digest.update(value)


def _framed_sha256(parts: Sequence[tuple[bytes, bytes]]) -> str:
    digest = hashlib.sha256()
    for label, value in parts:
        _framed_hash_update(digest, label, value)
    return digest.hexdigest()


def _untracked_state_digest(repo_root: Path, listing: bytes) -> str:
    if listing and not listing.endswith(b"\0"):
        raise EvidenceValidationError("Git returned an invalid untracked path list")
    paths = listing.split(b"\0")[:-1] if listing else []
    if len(paths) != len(set(paths)) or any(not path for path in paths):
        raise EvidenceValidationError("Git returned an invalid untracked path list")

    digest = hashlib.sha256()
    for relative_path in paths:
        _hash_untracked_entry(repo_root, relative_path, digest)
    return digest.hexdigest()


def _hash_untracked_entry(repo_root: Path, relative_path: bytes, digest: object) -> None:
    components = relative_path.split(b"/")
    if (
        relative_path.startswith(b"/")
        or any(component in (b"", b".", b"..") for component in components)
    ):
        raise EvidenceValidationError("Git returned an unsafe untracked path")

    directory_flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
    no_follow = getattr(os, "O_NOFOLLOW", 0)
    descriptors: list[int] = []
    try:
        current = os.open(repo_root, directory_flags)
        descriptors.append(current)
        for component in components[:-1]:
            current = os.open(
                component,
                directory_flags | no_follow,
                dir_fd=current,
            )
            descriptors.append(current)

        name = components[-1]
        before = os.stat(name, dir_fd=current, follow_symlinks=False)
        metadata = _stable_file_metadata(before)
        _framed_hash_update(digest, b"path", relative_path)
        _framed_hash_update(digest, b"metadata", metadata)

        if stat.S_ISREG(before.st_mode):
            descriptor = os.open(name, os.O_RDONLY | no_follow, dir_fd=current)
            descriptors.append(descriptor)
            opened = os.fstat(descriptor)
            if _stable_file_metadata(opened) != metadata:
                raise EvidenceValidationError("untracked file changed before read")
            content_digest = hashlib.sha256()
            while True:
                chunk = os.read(descriptor, FILE_READ_CHUNK_BYTES)
                if not chunk:
                    break
                content_digest.update(chunk)
            after = os.fstat(descriptor)
            current_state = os.stat(name, dir_fd=current, follow_symlinks=False)
            if (
                _stable_file_metadata(after) != metadata
                or _stable_file_metadata(current_state) != metadata
            ):
                raise EvidenceValidationError("untracked file changed while read")
            _framed_hash_update(
                digest,
                b"regular-content-sha256",
                content_digest.hexdigest().encode("ascii"),
            )
        elif stat.S_ISLNK(before.st_mode):
            target = os.readlink(name, dir_fd=current)
            target_bytes = os.fsencode(target) if isinstance(target, str) else target
            after = os.stat(name, dir_fd=current, follow_symlinks=False)
            if _stable_file_metadata(after) != metadata:
                raise EvidenceValidationError("untracked symlink changed while read")
            _framed_hash_update(digest, b"symlink-target", target_bytes)
        else:
            raise EvidenceValidationError(
                "untracked entries must be regular files or symbolic links"
            )
    except OSError as error:
        raise EvidenceValidationError("cannot fingerprint an untracked entry") from error
    finally:
        for descriptor in reversed(descriptors):
            os.close(descriptor)


def _stable_file_metadata(value: os.stat_result) -> bytes:
    fields = (
        value.st_dev,
        value.st_ino,
        value.st_mode,
        value.st_size,
        value.st_mtime_ns,
        value.st_ctime_ns,
    )
    return "\0".join(str(field) for field in fields).encode("ascii")


def prepare_success_evidence(request: EvidenceRequest) -> dict[str, object]:
    """Validate immutable report bytes and publish only a non-terminal bundle."""
    _validate_request(request)

    reports: list[ValidatedReport] = []
    reports_by_filename: dict[str, ValidatedReport] = {}
    user_homes: set[str] = set()
    totals = {"tests": 0, "skipped": 0, "failures": 0, "errors": 0}

    for class_name, expected_count in request.expected_classes.items():
        path = request.reports_dir / f"TEST-{class_name}.xml"
        report = _read_stable_report(path, request.started_epoch)
        suite = _parse_suite(report.content, class_name)
        user_home = _validate_report_provenance(suite, class_name, request)
        user_homes.add(user_home)

        class_totals = _validated_suite_totals(
            suite,
            class_name,
            require_testcase_count=False,
        )
        actual_count = class_totals["tests"]
        testcase_count = len(suite.findall("./testcase"))
        if actual_count != expected_count or testcase_count != expected_count:
            raise EvidenceValidationError(
                f"{class_name}: expected tests={expected_count}, "
                f"attribute={actual_count}, testcases={testcase_count}"
            )

        for field, value in class_totals.items():
            totals[field] += value
        validated_report = ValidatedReport(
            class_name=class_name,
            tests=actual_count,
            skipped=class_totals["skipped"],
            failures=class_totals["failures"],
            errors=class_totals["errors"],
            mtime_ns=report.mtime_ns,
            inode=report.inode,
            content=report.content,
            archived_content=_sanitized_report_bytes(suite, class_name),
        )
        reports.append(validated_report)
        reports_by_filename[validated_report.filename] = validated_report

    if len(user_homes) != 1:
        raise EvidenceValidationError("mixed user.home values across reports")

    if totals["skipped"] or totals["failures"] or totals["errors"]:
        raise EvidenceValidationError(
            "suite is not green: "
            f"skipped={totals['skipped']}, failures={totals['failures']}, "
            f"errors={totals['errors']}"
        )

    expected_total = sum(request.expected_classes.values())
    if totals["tests"] != expected_total:
        raise EvidenceValidationError(
            f"expected total={expected_total}, actual={totals['tests']}"
        )

    validated_user_home = next(iter(user_homes))
    all_reports = None
    reports_to_archive = reports
    if request.require_all_reports_green:
        all_reports, reports_to_archive = _validate_all_reports_green(
            request,
            reports_by_filename,
            validated_user_home,
        )

    prepared_manifest = _build_prepared_manifest_payload(
        request,
        reports,
        totals,
        all_reports,
    )
    _archive_prepared_atomically(
        request.evidence_dir,
        reports_to_archive,
        prepared_manifest,
    )
    return prepared_manifest


def validate_and_archive(request: EvidenceRequest) -> dict[str, object]:
    """Compatibility API: prepare and finalize against one stable source snapshot.

    Production runners use the explicit CLI phases. This wrapper remains for
    callers that already supplied an immutable SourceState directly.
    """
    prepare_success_evidence(request)
    return finalize_success_evidence(
        evidence_dir=request.evidence_dir,
        suite=request.suite,
        run_id=request.run_id,
        started_epoch=request.started_epoch,
        expected_git_sha=request.expected_git_sha or "",
        expected_git_tree=request.expected_git_tree or "",
        retention_class=request.retention_class,
        clean_required=request.require_clean,
        source_state_reader=lambda: request.source_state,
    )


def finalize_success_evidence(
        *,
        evidence_dir: Path,
        suite: str,
        run_id: str,
        started_epoch: int,
        expected_git_sha: str,
        expected_git_tree: str,
        retention_class: str,
        clean_required: bool,
        source_state_reader: Callable[[], SourceState]) -> dict[str, object]:
    """Commit PASS as the final atomic mutation after canonical revalidation."""
    _validate_finalize_inputs(
        evidence_dir=evidence_dir,
        suite=suite,
        run_id=run_id,
        started_epoch=started_epoch,
        expected_git_sha=expected_git_sha,
        expected_git_tree=expected_git_tree,
        retention_class=retention_class,
    )
    prepared_dir = _prepared_evidence_dir(evidence_dir)
    committing_dir = _committing_evidence_dir(evidence_dir)
    prepared_root = _ensure_safe_prepared_root(evidence_dir)
    passed_candidate = _passed_candidate_path(evidence_dir)
    _remove_safe_regular_file_if_present(passed_candidate)
    existing = tuple(
        path
        for path in (evidence_dir, prepared_dir, committing_dir)
        if os.path.lexists(path)
    )
    if len(existing) != 1:
        raise EvidenceValidationError(
            "exactly one prepared evidence bundle must exist for finalize"
        )
    bundle_dir = existing[0]
    _validate_owned_directory(bundle_dir)
    prepared_manifest = _load_prepared_bundle(
        bundle_dir,
        suite=suite,
        run_id=run_id,
        started_epoch=started_epoch,
        expected_git_sha=expected_git_sha,
        expected_git_tree=expected_git_tree,
        retention_class=retention_class,
        clean_required=clean_required,
    )

    first_source = _capture_finalize_source_or_fail(
        source_state_reader,
        prepared_manifest,
        evidence_dir=evidence_dir,
        bundle_dir=bundle_dir,
        suite=suite,
        run_id=run_id,
        started_epoch=started_epoch,
        expected_git_sha=expected_git_sha,
        expected_git_tree=expected_git_tree,
        retention_class=retention_class,
        clean_required=clean_required,
        message="cannot capture source before evidence finalization",
    )
    _assert_finalize_source(
        first_source,
        prepared_manifest,
        expected_git_sha=expected_git_sha,
        expected_git_tree=expected_git_tree,
        clean_required=clean_required,
        failure_callback=lambda message: _fail_prepared_and_raise(
            message,
            evidence_dir=evidence_dir,
            bundle_dir=bundle_dir,
            suite=suite,
            run_id=run_id,
            started_epoch=started_epoch,
            source_state=first_source,
            expected_git_sha=expected_git_sha,
            expected_git_tree=expected_git_tree,
            retention_class=retention_class,
            clean_required=clean_required,
        ),
    )

    latest_source = first_source
    try:
        # Publish only the non-terminal prepared bundle. An artifact collector
        # may observe this path from this point onward, so manifest.json must
        # remain prepared until the final atomic replacement below.
        if bundle_dir != evidence_dir:
            os.replace(bundle_dir, evidence_dir)
            _fsync_directory(bundle_dir.parent)
            _fsync_directory(evidence_dir.parent)
            bundle_dir = evidence_dir

        # Re-read only the exact staged bytes after the canonical move. The
        # mutable Surefire report directory is never reopened.
        prepared_manifest = _load_prepared_bundle(
            evidence_dir,
            suite=suite,
            run_id=run_id,
            started_epoch=started_epoch,
            expected_git_sha=expected_git_sha,
            expected_git_tree=expected_git_tree,
            retention_class=retention_class,
            clean_required=clean_required,
        )
        latest_source = source_state_reader()
        if not _source_is_finalizable(
                latest_source,
                prepared_manifest,
                expected_git_sha=expected_git_sha,
                expected_git_tree=expected_git_tree,
                clean_required=clean_required):
            raise EvidenceValidationError(
                "source changed after canonical prepared publication"
            )

        passed_manifest = _build_passed_manifest_from_prepared(prepared_manifest)
        _write_bytes_exclusive(passed_candidate, _manifest_bytes(passed_manifest))
        _fsync_directory(prepared_root)

        # Writing the outside-the-artifact candidate takes time. Re-capture
        # once more so the atomic replacement below immediately follows the
        # last successful source validation.
        latest_source = source_state_reader()
        if not _source_is_finalizable(
                latest_source,
                prepared_manifest,
                expected_git_sha=expected_git_sha,
                expected_git_tree=expected_git_tree,
                clean_required=clean_required):
            raise EvidenceValidationError(
                "source changed immediately before terminal evidence commit"
            )

        # This is deliberately the terminal classification mutation. Until
        # this atomic replace succeeds, canonical manifest.json stays prepared
        # and eligibleForRelease=false, including across an uncatchable kill.
        os.replace(passed_candidate, evidence_dir / PREPARED_MANIFEST_NAME)
        return passed_manifest
    except Exception as error:
        try:
            _fail_prepared_and_raise(
                "finalization failed before terminal PASS commit",
                evidence_dir=evidence_dir,
                bundle_dir=evidence_dir,
                suite=suite,
                run_id=run_id,
                started_epoch=started_epoch,
                source_state=latest_source,
                expected_git_sha=expected_git_sha,
                expected_git_tree=expected_git_tree,
                retention_class=retention_class,
                clean_required=clean_required,
            )
        except EvidenceValidationError as failure:
            raise failure from error

    raise AssertionError("unreachable")


def record_failure_diagnostic(
        request: FailureEvidenceRequest) -> dict[str, object]:
    """Persist a minimal failure record without reclassifying it as evidence."""
    _validate_failure_request(request)
    manifest = _build_failure_manifest(request)
    _archive_or_replace_failure(request.evidence_dir, manifest)
    return manifest


def _build_failure_manifest(
        request: FailureEvidenceRequest) -> dict[str, object]:
    return {
        "schemaVersion": 2,
        "status": "failed",
        "suite": request.suite,
        "runId": request.run_id,
        "startedAtEpoch": request.started_epoch,
        "archivedAtUtc": datetime.now(timezone.utc).isoformat(),
        "retentionClass": request.retention_class,
        "eligibleForRelease": False,
        "source": {
            "gitSha": request.source_state.git_sha,
            "gitTree": request.source_state.git_tree,
            "runStartGitSha": request.expected_git_sha,
            "runStartGitTree": request.expected_git_tree,
            "clean": request.source_state.dirty_path_count == 0,
            "dirtyPathCount": request.source_state.dirty_path_count,
            "cleanRequired": request.clean_required,
        },
        "failure": {
            "stage": request.failure_stage,
            "exitCode": request.exit_code,
        },
    }


def _validate_report_provenance(
        suite: ET.Element,
        class_name: str,
        request: EvidenceRequest) -> str:
    critical_properties = {
        "vanrides.qa.run-id": [],
        "vanrides.qa.git-sha": [],
        "user.home": [],
    }
    for element in suite.findall("./properties/property"):
        name = element.attrib.get("name")
        if name in critical_properties:
            critical_properties[name].append(element.attrib.get("value"))
    for name, values in critical_properties.items():
        if len(values) != 1:
            safe_label = {
                "vanrides.qa.run-id": "run-id",
                "vanrides.qa.git-sha": "git-sha",
                "user.home": "user.home",
            }[name]
            raise EvidenceValidationError(
                f"{class_name}: {safe_label} property count mismatch"
            )

    actual_run_id = critical_properties["vanrides.qa.run-id"][0]
    if actual_run_id != request.run_id:
        raise EvidenceValidationError(
            f"{class_name}: run-id provenance mismatch"
        )

    actual_git_sha = critical_properties["vanrides.qa.git-sha"][0]
    if actual_git_sha != request.expected_git_sha:
        raise EvidenceValidationError(
            f"{class_name}: git-sha provenance mismatch"
        )

    user_home = critical_properties["user.home"][0]
    if not user_home:
        raise EvidenceValidationError(f"{class_name}: user.home missing from report")
    if (
        request.expected_user_home is not None
        and user_home != request.expected_user_home
    ):
        raise EvidenceValidationError(
            f"{class_name}: user.home does not match the isolated expected value"
        )
    return user_home


def _validate_all_reports_green(
        request: EvidenceRequest,
        cached_reports: Mapping[str, ValidatedReport],
        expected_user_home: str) -> tuple[dict[str, object], list[ValidatedReport]]:
    report_paths = tuple(sorted(request.reports_dir.glob("TEST-*.xml")))
    if not report_paths:
        raise EvidenceValidationError("all-report scan found no Surefire XML reports")

    totals = {"tests": 0, "skipped": 0, "failures": 0, "errors": 0}
    violations: list[str] = []
    class_names: set[str] = set()
    validated_reports: list[ValidatedReport] = []

    for path in report_paths:
        class_name = path.name.removeprefix("TEST-").removesuffix(".xml")
        if not JAVA_CLASS_NAME.fullmatch(class_name):
            raise EvidenceValidationError(
                "invalid class name in Surefire report filename"
            )
        if class_name in class_names:
            raise EvidenceValidationError(
                f"duplicate class in all-report scan: {class_name}"
            )
        class_names.add(class_name)

        report = cached_reports.get(path.name)
        if report is None:
            report = _read_stable_report(path, request.started_epoch)
        suite = _parse_suite(report.content, class_name)
        user_home = _validate_report_provenance(suite, class_name, request)
        if user_home != expected_user_home:
            raise EvidenceValidationError(
                f"{class_name}: mixed user.home in all-report scan"
            )

        class_totals = _validated_suite_totals(
            suite,
            class_name,
            require_testcase_count=True,
        )
        for field, value in class_totals.items():
            totals[field] += value
        validated_reports.append(
            ValidatedReport(
                class_name=class_name,
                tests=class_totals["tests"],
                skipped=class_totals["skipped"],
                failures=class_totals["failures"],
                errors=class_totals["errors"],
                mtime_ns=report.mtime_ns,
                inode=report.inode,
                content=report.content,
                archived_content=(
                    report.archived_content
                    or _sanitized_report_bytes(suite, class_name)
                ),
            )
        )
        if any(class_totals[field] for field in ("skipped", "failures", "errors")):
            violations.append(
                f"{class_name}(skipped={class_totals['skipped']}, "
                f"failures={class_totals['failures']}, errors={class_totals['errors']})"
            )

    final_report_names = tuple(
        path.name for path in sorted(request.reports_dir.glob("TEST-*.xml"))
    )
    initial_report_names = tuple(path.name for path in report_paths)
    if final_report_names != initial_report_names:
        raise EvidenceValidationError("Surefire report set changed during all-report scan")
    for report in validated_reports:
        _assert_report_unchanged(request.reports_dir / report.filename, report)
    post_revalidation_report_names = tuple(
        path.name for path in sorted(request.reports_dir.glob("TEST-*.xml"))
    )
    if post_revalidation_report_names != initial_report_names:
        raise EvidenceValidationError(
            "Surefire report set changed during final report revalidation"
        )
    if violations:
        raise EvidenceValidationError(
            "all-report scan is not green: " + "; ".join(violations)
        )

    summary: dict[str, object] = {
        "requiredGreen": True,
        "reportCount": len(report_paths),
        "totals": totals,
        "reports": [
            {
                "className": report.class_name,
                "file": report.filename,
                "tests": report.tests,
                "skipped": report.skipped,
                "failures": report.failures,
                "errors": report.errors,
                "mtimeNs": report.mtime_ns,
                "inode": report.inode,
                "sha256": report.sha256,
            }
            for report in validated_reports
        ],
    }
    return summary, validated_reports


def _assert_report_unchanged(path: Path, report: ValidatedReport) -> None:
    before = path.stat()
    content = path.read_bytes()
    after = path.stat()
    if (
        before.st_ino != report.inode
        or after.st_ino != report.inode
        or before.st_mtime_ns != report.mtime_ns
        or after.st_mtime_ns != report.mtime_ns
        or before.st_size != len(report.content)
        or after.st_size != len(report.content)
        or content != report.content
    ):
        raise EvidenceValidationError("Surefire report changed before archive")


def _validate_request(request: EvidenceRequest) -> None:
    _validate_common_request(
        suite=request.suite,
        run_id=request.run_id,
        started_epoch=request.started_epoch,
        evidence_dir=request.evidence_dir,
        retention_class=request.retention_class,
        source_state=request.source_state,
        expected_git_sha=request.expected_git_sha,
        expected_git_tree=request.expected_git_tree,
        allow_source_drift=False,
        allow_existing_prepared=False,
    )
    if not request.expected_classes:
        raise EvidenceValidationError("expected test matrix is empty")
    for class_name, count in request.expected_classes.items():
        if not JAVA_CLASS_NAME.fullmatch(class_name) or count <= 0:
            raise EvidenceValidationError(
                f"invalid expected class entry: {class_name!r}={count}"
            )
    if any(
        not isinstance(key, str)
        or not isinstance(value, str)
        or not SAFE_IDENTIFIER.fullmatch(key)
        or len(key) > 64
        or len(value) > 64
        for key, value in request.metadata.items()
    ):
        raise EvidenceValidationError("invalid public metadata shape")
    _public_execution_metadata(request)
    if request.require_clean and request.source_state.dirty_path_count != 0:
        raise EvidenceValidationError(
            "clean source required, but git reports "
            f"{request.source_state.dirty_path_count} dirty path(s)"
        )
    if request.retention_class == "release" and not request.require_clean:
        raise EvidenceValidationError(
            "release retention requires clean source enforcement"
        )


def _validate_failure_request(request: FailureEvidenceRequest) -> None:
    _validate_common_request(
        suite=request.suite,
        run_id=request.run_id,
        started_epoch=request.started_epoch,
        evidence_dir=request.evidence_dir,
        retention_class=request.retention_class,
        source_state=request.source_state,
        expected_git_sha=request.expected_git_sha,
        expected_git_tree=request.expected_git_tree,
        allow_source_drift=True,
        allow_existing_prepared=True,
    )
    if request.failure_stage not in FAILURE_STAGES:
        raise EvidenceValidationError(
            f"invalid failure stage: {request.failure_stage!r}"
        )
    if not 1 <= request.exit_code <= 255:
        raise EvidenceValidationError(
            f"failure exit code must be between 1 and 255: {request.exit_code}"
        )


def _validate_common_request(
        *,
        suite: str,
        run_id: str,
        started_epoch: int,
        evidence_dir: Path,
        retention_class: str,
        source_state: SourceState,
        expected_git_sha: str | None,
        expected_git_tree: str | None,
        allow_source_drift: bool,
        allow_existing_prepared: bool) -> None:
    for label, value in (("suite", suite), ("run-id", run_id)):
        if not SAFE_IDENTIFIER.fullmatch(value):
            raise EvidenceValidationError(f"unsafe or empty {label}: {value!r}")
    if started_epoch <= 0:
        raise EvidenceValidationError("started_epoch must be positive")
    if retention_class not in RETENTION_CLASSES:
        raise EvidenceValidationError(
            f"invalid retention class: {retention_class!r}"
        )
    for label, value in (
        ("source git SHA", source_state.git_sha),
        ("source git tree", source_state.git_tree),
        ("run-start git SHA", expected_git_sha),
        ("run-start git tree", expected_git_tree),
    ):
        if value is None or not GIT_OBJECT_ID.fullmatch(value):
            raise EvidenceValidationError(f"invalid {label}: {value!r}")
    if (
        not isinstance(source_state.dirty_path_count, int)
        or isinstance(source_state.dirty_path_count, bool)
        or source_state.dirty_path_count < 0
    ):
        raise EvidenceValidationError("invalid dirty path count")
    _validate_evidence_identity(evidence_dir, run_id=run_id)
    destinations = (
        evidence_dir,
        _prepared_evidence_dir(evidence_dir),
        _committing_evidence_dir(evidence_dir),
    )
    if not allow_existing_prepared and any(os.path.lexists(path) for path in destinations):
        raise EvidenceValidationError(
            f"evidence directory already exists: {evidence_dir}"
        )
    if allow_source_drift:
        return
    if expected_git_sha != source_state.git_sha:
        raise EvidenceValidationError(
            "git SHA changed during evidence run: "
            f"start={expected_git_sha}, end={source_state.git_sha}"
        )
    if expected_git_tree != source_state.git_tree:
        raise EvidenceValidationError(
            "git tree changed during evidence run: "
            f"start={expected_git_tree}, end={source_state.git_tree}"
        )


def _read_stable_report(path: Path, started_epoch: int) -> ValidatedReport:
    if not path.is_file():
        raise EvidenceValidationError("missing Surefire report")

    before = path.stat()
    content = path.read_bytes()
    after = path.stat()
    stable_fields = (
        before.st_ino == after.st_ino,
        before.st_size == after.st_size == len(content),
        before.st_mtime_ns == after.st_mtime_ns,
    )
    if not all(stable_fields):
        raise EvidenceValidationError("Surefire report changed while being read")
    if after.st_mtime_ns < started_epoch * 1_000_000_000:
        raise EvidenceValidationError("Surefire report predates this run")

    return ValidatedReport(
        class_name="",
        tests=0,
        skipped=0,
        failures=0,
        errors=0,
        mtime_ns=after.st_mtime_ns,
        inode=after.st_ino,
        content=content,
        archived_content=b"",
    )


def _parse_suite(content: bytes, class_name: str) -> ET.Element:
    try:
        suite = ET.fromstring(content)
    except ET.ParseError as error:
        raise EvidenceValidationError(f"{class_name}: invalid XML") from error
    if suite.tag != "testsuite":
        raise EvidenceValidationError(f"{class_name}: root element is not testsuite")
    actual_name = suite.attrib.get("name")
    if actual_name != class_name:
        raise EvidenceValidationError(f"{class_name}: testsuite name mismatch")
    return suite


def _int_attribute(suite: ET.Element, field: str, class_name: str) -> int:
    raw = suite.attrib.get(field, "0")
    try:
        value = int(raw)
    except ValueError as error:
        raise EvidenceValidationError(
            f"{class_name}: invalid {field} attribute"
        ) from error
    if value < 0:
        raise EvidenceValidationError(
            f"{class_name}: negative {field} attribute: {value}"
        )
    return value


def _validated_suite_totals(
        suite: ET.Element,
        class_name: str,
        *,
        require_testcase_count: bool) -> dict[str, int]:
    totals = {
        field: _int_attribute(suite, field, class_name)
        for field in ("tests", "skipped", "failures", "errors")
    }
    testcase_count = len(suite.findall("./testcase"))
    if require_testcase_count and totals["tests"] != testcase_count:
        raise EvidenceValidationError(
            f"{class_name}: inconsistent tests: "
            f"attribute={totals['tests']}, testcases={testcase_count}"
        )

    for field, element_name in (
        ("skipped", "skipped"),
        ("failures", "failure"),
        ("errors", "error"),
    ):
        element_count = len(suite.findall(f"./testcase/{element_name}"))
        if totals[field] != element_count:
            raise EvidenceValidationError(
                f"{class_name}: inconsistent {field}: "
                f"attribute={totals[field]}, elements={element_count}"
            )

    flaky_count = sum(
        len(suite.findall(f"./testcase/{element_name}"))
        for element_name in ("flakyFailure", "flakyError", "rerunFailure", "rerunError")
    )
    if flaky_count:
        raise EvidenceValidationError(
            f"{class_name}: flaky/rerun outcomes are not accepted: elements={flaky_count}"
        )
    return totals


def _sanitized_report_bytes(suite: ET.Element, class_name: str) -> bytes:
    """Materialize the minimal deterministic XML allowed in evidence.

    Surefire properties and output/error elements routinely contain local
    paths, classpaths, environment details, tokens, or application logs.  They
    are used for in-process provenance validation but are never copied into the
    canonical evidence bundle.  A green report needs only aggregate totals and
    deterministic testcase ordinals.  Raw testcase display names and class
    attributes are intentionally excluded because parameterized tests may put
    user-controlled values or credentials in those fields.
    """
    sanitized_suite = ET.Element("testsuite", {
        "name": class_name,
        "tests": str(_int_attribute(suite, "tests", class_name)),
        "skipped": str(_int_attribute(suite, "skipped", class_name)),
        "failures": str(_int_attribute(suite, "failures", class_name)),
        "errors": str(_int_attribute(suite, "errors", class_name)),
    })
    for index, _testcase in enumerate(suite.findall("./testcase"), start=1):
        ET.SubElement(sanitized_suite, "testcase", {
            "index": str(index),
        })
    return ET.tostring(
        sanitized_suite,
        encoding="utf-8",
        xml_declaration=True,
        short_empty_elements=True,
    ) + b"\n"


def _build_prepared_manifest_payload(
        request: EvidenceRequest,
        reports: Sequence[ValidatedReport],
        totals: Mapping[str, int],
        all_reports: Mapping[str, object] | None) -> dict[str, object]:
    command_profile, public_environment = _public_execution_metadata(request)
    manifest: dict[str, object] = {
        "schemaVersion": 2,
        "status": "prepared",
        "suite": request.suite,
        "runId": request.run_id,
        "startedAtEpoch": request.started_epoch,
        "preparedAtUtc": datetime.now(timezone.utc).isoformat(),
        "retentionClass": request.retention_class,
        "eligibleForRelease": False,
        "preparedSourceStateSha256": _source_state_digest(request.source_state),
        "commandProfile": command_profile,
        "source": {
            "gitSha": request.source_state.git_sha,
            "gitTree": request.source_state.git_tree,
            "runStartGitSha": request.expected_git_sha,
            "runStartGitTree": request.expected_git_tree,
            "clean": request.source_state.dirty_path_count == 0,
            "dirtyPathCount": request.source_state.dirty_path_count,
            "cleanRequired": request.require_clean,
        },
        "environment": public_environment,
        "totals": dict(totals),
        "reports": [
            {
                "className": report.class_name,
                "file": report.filename,
                "tests": report.tests,
                "mtimeNs": report.mtime_ns,
                "sha256": report.sha256,
            }
            for report in reports
        ],
    }
    if all_reports is not None:
        manifest["allReports"] = dict(all_reports)
    return manifest


def _public_execution_metadata(
        request: EvidenceRequest) -> tuple[str, dict[str, object]]:
    user_home_isolated = request.expected_user_home is not None
    metadata = dict(request.metadata)
    if request.suite == "rbac":
        if metadata:
            raise EvidenceValidationError(
                "rbac evidence does not accept public metadata"
            )
        return (
            "maven-rbac-suite",
            {"userHomeIsolated": user_home_isolated},
        )

    if request.suite != "integration":
        raise EvidenceValidationError(
            f"unsupported success evidence suite: {request.suite}"
        )
    actual_keys = frozenset(metadata)
    if actual_keys != INTEGRATION_METADATA_KEYS:
        missing = sorted(INTEGRATION_METADATA_KEYS - actual_keys)
        unexpected = sorted(actual_keys - INTEGRATION_METADATA_KEYS)
        details = []
        if missing:
            details.append("missing=" + ",".join(missing))
        if unexpected:
            details.append(f"unexpected-count={len(unexpected)}")
        raise EvidenceValidationError(
            "integration metadata contract mismatch: " + "; ".join(details)
        )

    scope = metadata["scope"]
    if scope not in ("focused", "all-tests"):
        raise EvidenceValidationError("invalid integration metadata: scope")
    if (scope == "all-tests") is not request.require_all_reports_green:
        raise EvidenceValidationError(
            "integration scope does not match all-report validation mode"
        )
    for key in (
        "testcontainersApiVersion",
        "dockerMinApiVersion",
        "dockerMaxApiVersion",
    ):
        if not DOCKER_API_VERSION.fullmatch(metadata[key]):
            raise EvidenceValidationError(
                f"invalid integration metadata: {key}"
            )
    if metadata["dockerEndpointTransport"] not in ("unix", "npipe"):
        raise EvidenceValidationError(
            "invalid integration metadata: dockerEndpointTransport"
        )
    if metadata["mavenHomeIsolated"] != "true" or not user_home_isolated:
        raise EvidenceValidationError(
            "integration evidence requires an isolated validated user.home"
        )
    if metadata["mavenRcSkipped"] != "true":
        raise EvidenceValidationError(
            "invalid integration metadata: mavenRcSkipped"
        )

    return (
        f"maven-integration-{scope}",
        {
            "scope": scope,
            "testcontainersApiVersion": metadata["testcontainersApiVersion"],
            "dockerMinApiVersion": metadata["dockerMinApiVersion"],
            "dockerMaxApiVersion": metadata["dockerMaxApiVersion"],
            "dockerEndpointTransport": metadata["dockerEndpointTransport"],
            "mavenRcSkipped": True,
            "userHomeIsolated": True,
        },
    )


def _manifest_bytes(manifest: Mapping[str, object]) -> bytes:
    return (
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")


def _source_state_digest(source_state: SourceState) -> str:
    if re.fullmatch(r"[0-9a-f]{64}", source_state.dirty_state_sha256):
        return source_state.dirty_state_sha256
    fallback = (
        f"{source_state.git_sha}\0{source_state.git_tree}\0"
        f"{source_state.dirty_path_count}"
    ).encode("ascii", errors="strict")
    return hashlib.sha256(fallback).hexdigest()


def _prepared_evidence_dir(evidence_dir: Path) -> Path:
    return evidence_dir.parent.parent / PREPARED_ROOT_NAME / evidence_dir.name


def _committing_evidence_dir(evidence_dir: Path) -> Path:
    return (
        evidence_dir.parent.parent
        / PREPARED_ROOT_NAME
        / f".{evidence_dir.name}{COMMITTING_SUFFIX}"
    )


def _passed_candidate_path(evidence_dir: Path) -> Path:
    return (
        evidence_dir.parent.parent
        / PREPARED_ROOT_NAME
        / f".{evidence_dir.name}.manifest.passed"
    )


def _build_passed_manifest_from_prepared(
        prepared_manifest: Mapping[str, object]) -> dict[str, object]:
    passed = copy.deepcopy(dict(prepared_manifest))
    passed["status"] = "passed"
    passed["archivedAtUtc"] = datetime.now(timezone.utc).isoformat()
    passed.pop("preparedAtUtc", None)
    passed.pop("preparedSourceStateSha256", None)
    source = passed.get("source")
    totals = passed.get("totals")
    all_reports = passed.get("allReports")
    passed["eligibleForRelease"] = bool(
        passed.get("retentionClass") == "release"
        and isinstance(source, dict)
        and source.get("cleanRequired") is True
        and source.get("clean") is True
        and source.get("dirtyPathCount") == 0
        and source.get("gitSha") == source.get("runStartGitSha")
        and source.get("gitTree") == source.get("runStartGitTree")
        and isinstance(totals, dict)
        and all(totals.get(field) == 0 for field in ("skipped", "failures", "errors"))
        and isinstance(all_reports, dict)
        and all_reports.get("requiredGreen") is True
    )
    return passed


def _validate_evidence_identity(
        evidence_dir: Path,
        *,
        run_id: str) -> None:
    if not evidence_dir.is_absolute() or evidence_dir == Path(evidence_dir.anchor):
        raise EvidenceValidationError("evidence directory must be an absolute child path")
    if evidence_dir.name != run_id or not SAFE_IDENTIFIER.fullmatch(run_id):
        raise EvidenceValidationError("evidence directory name must equal the safe run-id")
    if any(ord(character) < 32 for character in str(evidence_dir)):
        raise EvidenceValidationError("evidence directory contains control characters")


def _ensure_safe_evidence_parent(parent: Path) -> None:
    parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    try:
        parent_stat = parent.lstat()
    except OSError as error:
        raise EvidenceValidationError("cannot inspect evidence parent") from error
    if (
        stat.S_ISLNK(parent_stat.st_mode)
        or not stat.S_ISDIR(parent_stat.st_mode)
        or parent_stat.st_uid != os.getuid()
        or parent_stat.st_mode & 0o022
    ):
        raise EvidenceValidationError("evidence parent is not a safe owned directory")


def _ensure_safe_prepared_root(evidence_dir: Path) -> Path:
    root = evidence_dir.parent.parent / PREPARED_ROOT_NAME
    root.mkdir(mode=0o700, parents=False, exist_ok=True)
    try:
        root_stat = root.lstat()
    except OSError as error:
        raise EvidenceValidationError("cannot inspect prepared evidence root") from error
    if (
        stat.S_ISLNK(root_stat.st_mode)
        or not stat.S_ISDIR(root_stat.st_mode)
        or root_stat.st_uid != os.getuid()
        or stat.S_IMODE(root_stat.st_mode) != 0o700
    ):
        raise EvidenceValidationError(
            "prepared evidence root must be an owned non-symlink directory with mode 0700"
        )
    return root


def _validate_owned_directory(path: Path) -> os.stat_result:
    try:
        path_stat = path.lstat()
    except OSError as error:
        raise EvidenceValidationError(f"cannot inspect evidence bundle: {path.name}") from error
    if (
        stat.S_ISLNK(path_stat.st_mode)
        or not stat.S_ISDIR(path_stat.st_mode)
        or path_stat.st_uid != os.getuid()
        or path_stat.st_mode & 0o022
    ):
        raise EvidenceValidationError("evidence bundle is not a safe owned directory")
    return path_stat


def _write_bytes_exclusive(path: Path, content: bytes) -> None:
    no_follow = getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(
        path,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | no_follow,
        0o600,
    )
    try:
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, "wb", closefd=False) as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
    finally:
        os.close(descriptor)


def _fsync_directory(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _remove_tree_created_by_us(path: Path) -> None:
    if path.exists() and not path.is_symlink():
        shutil.rmtree(path, ignore_errors=True)


def _archive_prepared_atomically(
        evidence_dir: Path,
        reports: Sequence[ValidatedReport],
        prepared_manifest: Mapping[str, object]) -> None:
    _validate_evidence_identity(
        evidence_dir,
        run_id=str(prepared_manifest.get("runId", "")),
    )
    _ensure_safe_evidence_parent(evidence_dir.parent)
    prepared_root = _ensure_safe_prepared_root(evidence_dir)
    prepared_dir = _prepared_evidence_dir(evidence_dir)
    committing_dir = _committing_evidence_dir(evidence_dir)
    if (
        os.path.lexists(evidence_dir)
        or os.path.lexists(prepared_dir)
        or os.path.lexists(committing_dir)
    ):
        raise EvidenceValidationError("evidence destination already exists")

    temp_dir = Path(tempfile.mkdtemp(
        prefix=f".{evidence_dir.name}.preparing.",
        dir=prepared_root,
    ))
    os.chmod(temp_dir, 0o700)
    try:
        for report in reports:
            _write_bytes_exclusive(
                temp_dir / report.filename,
                report.archived_content,
            )
        _write_bytes_exclusive(
            temp_dir / PREPARED_MANIFEST_NAME,
            _manifest_bytes(prepared_manifest),
        )
        _fsync_directory(temp_dir)
        os.replace(temp_dir, prepared_dir)
        _fsync_directory(prepared_root)
    except Exception:
        _remove_tree_created_by_us(temp_dir)
        raise


def _read_regular_owned_bytes(path: Path, *, maximum: int | None = None) -> bytes:
    no_follow = getattr(os, "O_NOFOLLOW", 0)
    try:
        before = path.lstat()
        descriptor = os.open(path, os.O_RDONLY | no_follow)
    except OSError as error:
        raise EvidenceValidationError(f"cannot read safe evidence file: {path.name}") from error
    try:
        opened = os.fstat(descriptor)
        if (
            stat.S_ISLNK(before.st_mode)
            or not stat.S_ISREG(before.st_mode)
            or before.st_uid != os.getuid()
            or before.st_mode & 0o022
            or (before.st_dev, before.st_ino) != (opened.st_dev, opened.st_ino)
            or (maximum is not None and opened.st_size > maximum)
        ):
            raise EvidenceValidationError(f"unsafe evidence file: {path.name}")
        with os.fdopen(descriptor, "rb", closefd=False) as stream:
            content = stream.read()
        after = os.fstat(descriptor)
        current = path.lstat()
        if (
            (opened.st_dev, opened.st_ino, opened.st_size)
            != (after.st_dev, after.st_ino, after.st_size)
            or (opened.st_dev, opened.st_ino)
            != (current.st_dev, current.st_ino)
            or len(content) != opened.st_size
        ):
            raise EvidenceValidationError(f"evidence file changed while read: {path.name}")
        return content
    finally:
        os.close(descriptor)


def _strict_json_object(content: bytes, *, label: str) -> dict[str, object]:
    def reject_duplicates(pairs: list[tuple[str, object]]) -> dict[str, object]:
        result: dict[str, object] = {}
        for key, value in pairs:
            if key in result:
                raise EvidenceValidationError(f"duplicate key in {label}: {key}")
            result[key] = value
        return result

    try:
        value = json.loads(
            content.decode("utf-8"),
            object_pairs_hook=reject_duplicates,
            parse_constant=lambda item: (_ for _ in ()).throw(
                EvidenceValidationError(f"nonstandard JSON in {label}: {item}")
            ),
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise EvidenceValidationError(f"invalid JSON in {label}") from error
    if not isinstance(value, dict):
        raise EvidenceValidationError(f"{label} must be a JSON object")
    return value


def _prepared_report_files(passed_manifest: Mapping[str, object]) -> dict[str, str]:
    report_entries: list[object] = []
    reports = passed_manifest.get("reports")
    if isinstance(reports, list):
        report_entries.extend(reports)
    all_reports = passed_manifest.get("allReports")
    if isinstance(all_reports, dict) and isinstance(all_reports.get("reports"), list):
        report_entries.extend(all_reports["reports"])

    files: dict[str, str] = {}
    for entry in report_entries:
        if not isinstance(entry, dict):
            raise EvidenceValidationError("prepared report metadata is invalid")
        filename = entry.get("file")
        digest = entry.get("sha256")
        if (
            not isinstance(filename, str)
            or Path(filename).name != filename
            or not filename.startswith("TEST-")
            or not filename.endswith(".xml")
            or not isinstance(digest, str)
            or not re.fullmatch(r"[0-9a-f]{64}", digest)
        ):
            raise EvidenceValidationError("prepared report file metadata is invalid")
        previous = files.setdefault(filename, digest)
        if previous != digest:
            raise EvidenceValidationError("prepared report digest is inconsistent")
    if not files:
        raise EvidenceValidationError("prepared evidence has no report files")
    return files


def _load_prepared_bundle(
        bundle_dir: Path,
        *,
        suite: str,
        run_id: str,
        started_epoch: int,
        expected_git_sha: str,
        expected_git_tree: str,
        retention_class: str,
        clean_required: bool) -> dict[str, object]:
    _validate_owned_directory(bundle_dir)
    if bundle_dir.parent.name == PREPARED_ROOT_NAME:
        prepared_root_stat = bundle_dir.parent.lstat()
        if (
            stat.S_ISLNK(prepared_root_stat.st_mode)
            or not stat.S_ISDIR(prepared_root_stat.st_mode)
            or prepared_root_stat.st_uid != os.getuid()
            or stat.S_IMODE(prepared_root_stat.st_mode) != 0o700
        ):
            raise EvidenceValidationError("prepared evidence root is unsafe")
    prepared_bytes = _read_regular_owned_bytes(
        bundle_dir / PREPARED_MANIFEST_NAME,
        maximum=MAX_MANIFEST_BYTES,
    )
    prepared = _strict_json_object(prepared_bytes, label="prepared manifest")
    if prepared.get("status") != "prepared" or prepared.get("eligibleForRelease") is not False:
        raise EvidenceValidationError("manifest.json is not a non-terminal prepared manifest")
    if (
        prepared.get("suite") != suite
        or prepared.get("runId") != run_id
        or prepared.get("startedAtEpoch") != started_epoch
        or prepared.get("retentionClass") != retention_class
    ):
        raise EvidenceValidationError("prepared manifest identity does not match finalize request")
    prepared_source = prepared.get("source")
    if not isinstance(prepared_source, dict):
        raise EvidenceValidationError("prepared manifest source is missing")
    if (
        prepared_source.get("runStartGitSha") != expected_git_sha
        or prepared_source.get("runStartGitTree") != expected_git_tree
        or prepared_source.get("cleanRequired") is not clean_required
    ):
        raise EvidenceValidationError("prepared manifest source contract does not match finalize request")
    if not re.fullmatch(
            r"[0-9a-f]{64}",
            str(prepared.get("preparedSourceStateSha256", ""))):
        raise EvidenceValidationError("prepared source-state digest is invalid")

    report_files = _prepared_report_files(prepared)
    allowed_entries = {
        PREPARED_MANIFEST_NAME,
        *report_files,
    }

    actual_entries: set[str] = set()
    with os.scandir(bundle_dir) as entries:
        for entry in entries:
            actual_entries.add(entry.name)
            if entry.is_symlink() or not entry.is_file(follow_symlinks=False):
                raise EvidenceValidationError("prepared evidence contains a non-regular entry")
    if actual_entries != allowed_entries:
        raise EvidenceValidationError("prepared evidence contains unexpected or missing files")
    for filename, expected_digest in report_files.items():
        report_bytes = _read_regular_owned_bytes(bundle_dir / filename)
        if hashlib.sha256(report_bytes).hexdigest() != expected_digest:
            raise EvidenceValidationError(f"prepared report digest changed: {filename}")
    return prepared


def _remove_safe_regular_file_if_present(path: Path) -> None:
    if not os.path.lexists(path):
        return
    file_stat = path.lstat()
    if (
        stat.S_ISLNK(file_stat.st_mode)
        or not stat.S_ISREG(file_stat.st_mode)
        or file_stat.st_uid != os.getuid()
        or file_stat.st_mode & 0o022
    ):
        raise EvidenceValidationError(f"cannot remove unsafe evidence file: {path.name}")
    path.unlink()


def _failure_manifest_for_finalize(
        *,
        evidence_dir: Path,
        suite: str,
        run_id: str,
        started_epoch: int,
        source_state: SourceState,
        expected_git_sha: str,
        expected_git_tree: str,
        retention_class: str,
        clean_required: bool) -> dict[str, object]:
    return _build_failure_manifest(FailureEvidenceRequest(
        suite=suite,
        run_id=run_id,
        started_epoch=started_epoch,
        evidence_dir=evidence_dir,
        source_state=source_state,
        clean_required=clean_required,
        failure_stage="validation",
        exit_code=1,
        retention_class=retention_class,
        expected_git_sha=expected_git_sha,
        expected_git_tree=expected_git_tree,
    ))


def _validate_prepared_directory_for_failure(
        path: Path,
        failure_manifest: Mapping[str, object],
        *,
        allow_passed_staging: bool = False) -> None:
    _validate_owned_directory(path)
    manifest_bytes = _read_regular_owned_bytes(
        path / PREPARED_MANIFEST_NAME,
        maximum=MAX_MANIFEST_BYTES,
    )
    manifest = _strict_json_object(manifest_bytes, label="replaceable manifest")
    replaceable_statuses = {"prepared", "passed"} if allow_passed_staging else {"prepared"}
    if manifest.get("status") not in replaceable_statuses:
        raise EvidenceValidationError("existing final evidence is not safely replaceable")
    if manifest.get("status") == "prepared" and manifest.get("eligibleForRelease") is not False:
        raise EvidenceValidationError("prepared evidence unexpectedly authorizes release")
    for field in ("suite", "runId", "startedAtEpoch", "retentionClass"):
        if manifest.get(field) != failure_manifest.get(field):
            raise EvidenceValidationError("prepared evidence identity does not match failure")
    with os.scandir(path) as entries:
        for entry in entries:
            if entry.is_symlink() or not entry.is_file(follow_symlinks=False):
                raise EvidenceValidationError("prepared evidence contains unsafe entries")
            _read_regular_owned_bytes(path / entry.name)


def _replace_directory_manifest_with_failure(
        evidence_dir: Path,
        manifest: Mapping[str, object]) -> None:
    failure_temp = evidence_dir / ".manifest.failed"
    _remove_safe_regular_file_if_present(failure_temp)
    _write_bytes_exclusive(failure_temp, _manifest_bytes(manifest))
    os.replace(failure_temp, evidence_dir / PREPARED_MANIFEST_NAME)
    _fsync_directory(evidence_dir)
    for path in tuple(evidence_dir.iterdir()):
        if path.name == PREPARED_MANIFEST_NAME:
            continue
        _remove_safe_regular_file_if_present(path)
    _fsync_directory(evidence_dir)


def _archive_or_replace_failure(
        evidence_dir: Path,
        manifest: Mapping[str, object]) -> None:
    run_id = str(manifest.get("runId", ""))
    _validate_evidence_identity(evidence_dir, run_id=run_id)
    _ensure_safe_evidence_parent(evidence_dir.parent)
    prepared_root = _ensure_safe_prepared_root(evidence_dir)
    _remove_safe_regular_file_if_present(_passed_candidate_path(evidence_dir))
    prepared_dir = _prepared_evidence_dir(evidence_dir)
    committing_dir = _committing_evidence_dir(evidence_dir)
    final_exists = os.path.lexists(evidence_dir)
    external = tuple(
        path for path in (prepared_dir, committing_dir) if os.path.lexists(path)
    )
    if final_exists and external:
        raise EvidenceValidationError("ambiguous final and staged evidence both exist")
    if len(external) > 1:
        raise EvidenceValidationError("ambiguous prepared and committing evidence both exist")
    if final_exists:
        _validate_prepared_directory_for_failure(evidence_dir, manifest)
        _replace_directory_manifest_with_failure(evidence_dir, manifest)
        return
    if external:
        staged_dir = external[0]
        _validate_prepared_directory_for_failure(
            staged_dir,
            manifest,
            allow_passed_staging=staged_dir == committing_dir,
        )
        _replace_directory_manifest_with_failure(staged_dir, manifest)
        os.replace(staged_dir, evidence_dir)
        _fsync_directory(prepared_root)
        _fsync_directory(evidence_dir.parent)
        return

    temp_dir = Path(tempfile.mkdtemp(
        prefix=f".{evidence_dir.name}.failing.",
        dir=prepared_root,
    ))
    os.chmod(temp_dir, 0o700)
    try:
        _write_bytes_exclusive(
            temp_dir / PREPARED_MANIFEST_NAME,
            _manifest_bytes(manifest),
        )
        _fsync_directory(temp_dir)
        os.replace(temp_dir, evidence_dir)
        _fsync_directory(prepared_root)
        _fsync_directory(evidence_dir.parent)
    except Exception:
        _remove_tree_created_by_us(temp_dir)
        raise


def _capture_finalize_source_or_fail(
        source_state_reader: Callable[[], SourceState],
        prepared_manifest: Mapping[str, object],
        *,
        evidence_dir: Path,
        bundle_dir: Path,
        suite: str,
        run_id: str,
        started_epoch: int,
        expected_git_sha: str,
        expected_git_tree: str,
        retention_class: str,
        clean_required: bool,
        message: str) -> SourceState:
    try:
        return source_state_reader()
    except Exception as error:
        source = prepared_manifest.get("source")
        fallback = SourceState(
            str(source.get("gitSha", "")) if isinstance(source, dict) else "",
            str(source.get("gitTree", "")) if isinstance(source, dict) else "",
            int(source.get("dirtyPathCount", 1))
            if isinstance(source, dict) and isinstance(source.get("dirtyPathCount"), int)
            else 1,
        )
        try:
            _fail_prepared_and_raise(
                message,
                evidence_dir=evidence_dir,
                bundle_dir=bundle_dir,
                suite=suite,
                run_id=run_id,
                started_epoch=started_epoch,
                source_state=fallback,
                expected_git_sha=expected_git_sha,
                expected_git_tree=expected_git_tree,
                retention_class=retention_class,
                clean_required=clean_required,
            )
        except EvidenceValidationError as failure:
            raise failure from error
        raise AssertionError("unreachable")


def _source_is_finalizable(
        source_state: SourceState,
        prepared_manifest: Mapping[str, object],
        *,
        expected_git_sha: str,
        expected_git_tree: str,
        clean_required: bool) -> bool:
    source = prepared_manifest.get("source")
    if not isinstance(source, dict):
        return False
    prepared_digest = prepared_manifest.get("preparedSourceStateSha256")
    dirty_count = source.get("dirtyPathCount")
    clean_state_is_consistent = (
        isinstance(dirty_count, int)
        and not isinstance(dirty_count, bool)
        and source.get("clean") is (dirty_count == 0)
    )
    clean_is_required = (
        clean_required or prepared_manifest.get("retentionClass") == "release"
    )
    return (
        source_state.git_sha == expected_git_sha == source.get("gitSha")
        and source_state.git_tree == expected_git_tree == source.get("gitTree")
        and source.get("runStartGitSha") == expected_git_sha
        and source.get("runStartGitTree") == expected_git_tree
        and source.get("cleanRequired") is clean_required
        and clean_state_is_consistent
        and source_state.dirty_path_count == dirty_count
        and _source_state_digest(source_state) == prepared_digest
        and (not clean_is_required or dirty_count == 0)
    )


def _assert_finalize_source(
        source_state: SourceState,
        prepared_manifest: Mapping[str, object],
        *,
        expected_git_sha: str,
        expected_git_tree: str,
        clean_required: bool,
        failure_callback: Callable[[str], None]) -> None:
    if not _source_is_finalizable(
            source_state,
            prepared_manifest,
            expected_git_sha=expected_git_sha,
            expected_git_tree=expected_git_tree,
            clean_required=clean_required):
        failure_callback("source is dirty or drifted from the prepared evidence snapshot")


def _fail_prepared_and_raise(
        message: str,
        *,
        evidence_dir: Path,
        bundle_dir: Path,
        suite: str,
        run_id: str,
        started_epoch: int,
        source_state: SourceState,
        expected_git_sha: str,
        expected_git_tree: str,
        retention_class: str,
        clean_required: bool) -> None:
    del bundle_dir  # The replacement path is always derived from evidence_dir.
    manifest = _failure_manifest_for_finalize(
        evidence_dir=evidence_dir,
        suite=suite,
        run_id=run_id,
        started_epoch=started_epoch,
        source_state=source_state,
        expected_git_sha=expected_git_sha,
        expected_git_tree=expected_git_tree,
        retention_class=retention_class,
        clean_required=clean_required,
    )
    _archive_or_replace_failure(evidence_dir, manifest)
    raise EvidenceValidationError(f"{message}; prepared evidence downgraded to failed")


def _validate_finalize_inputs(
        *,
        evidence_dir: Path,
        suite: str,
        run_id: str,
        started_epoch: int,
        expected_git_sha: str,
        expected_git_tree: str,
        retention_class: str) -> None:
    _validate_evidence_identity(evidence_dir, run_id=run_id)
    if not SAFE_IDENTIFIER.fullmatch(suite):
        raise EvidenceValidationError("unsafe or empty suite")
    if started_epoch <= 0:
        raise EvidenceValidationError("started_epoch must be positive")
    if retention_class not in RETENTION_CLASSES:
        raise EvidenceValidationError("invalid retention class")
    for label, value in (
        ("expected git SHA", expected_git_sha),
        ("expected git tree", expected_git_tree),
    ):
        if not GIT_OBJECT_ID.fullmatch(value):
            raise EvidenceValidationError(f"invalid {label}")
    _ensure_safe_evidence_parent(evidence_dir.parent)


def _parse_expected(values: Sequence[str]) -> dict[str, int]:
    expected: dict[str, int] = {}
    for value in values:
        class_name, separator, raw_count = value.rpartition("=")
        if not separator or not class_name:
            raise EvidenceValidationError(
                f"expected entry must use CLASS=COUNT: {value!r}"
            )
        try:
            count = int(raw_count)
        except ValueError as error:
            raise EvidenceValidationError(
                f"invalid expected count in {value!r}"
            ) from error
        if class_name in expected:
            raise EvidenceValidationError(f"duplicate expected class: {class_name}")
        expected[class_name] = count
    return expected


def _parse_metadata(values: Sequence[str]) -> dict[str, str]:
    metadata: dict[str, str] = {}
    for value in values:
        key, separator, item = value.partition("=")
        if not separator or not SAFE_IDENTIFIER.fullmatch(key):
            raise EvidenceValidationError("metadata must use SAFE_KEY=VALUE")
        if key in RESERVED_ENVIRONMENT_METADATA_KEYS:
            raise EvidenceValidationError(f"reserved metadata key: {key}")
        if key in metadata:
            raise EvidenceValidationError(f"duplicate metadata key: {key}")
        metadata[key] = item
    return metadata


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Validate fresh Surefire XML reports and atomically archive only "
            "their deterministic sanitized representation."
        )
    )
    parser.add_argument("--suite", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--started-epoch", required=True, type=int)
    parser.add_argument("--reports-dir", type=Path)
    parser.add_argument("--evidence-dir", required=True, type=Path)
    parser.add_argument("--repo-root", required=True, type=Path)
    parser.add_argument("--expected-git-sha", required=True)
    parser.add_argument("--expected-git-tree", required=True)
    parser.add_argument("--expected-user-home")
    parser.add_argument("--require-clean", action="store_true")
    parser.add_argument("--require-all-reports-green", action="store_true")
    parser.add_argument("--phase", choices=("prepare", "finalize"))
    parser.add_argument(
        "--retention-class",
        choices=sorted(RETENTION_CLASSES),
        default="local",
    )
    parser.add_argument("--failure-stage", choices=sorted(FAILURE_STAGES))
    parser.add_argument("--exit-code", type=int)
    parser.add_argument("--expected", action="append", default=[], metavar="CLASS=COUNT")
    parser.add_argument("--metadata", action="append", default=[], metavar="KEY=VALUE")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.failure_stage is not None:
            if args.phase is not None:
                raise EvidenceValidationError(
                    "--phase is not accepted with failure diagnostics"
                )
            if args.exit_code is None:
                raise EvidenceValidationError(
                    "--exit-code is required with --failure-stage"
                )
            forbidden_failure_inputs = {
                "--reports-dir": args.reports_dir,
                "--expected-user-home": args.expected_user_home,
                "--require-all-reports-green": args.require_all_reports_green,
                "--expected": args.expected,
                "--metadata": args.metadata,
            }
            supplied = [
                option for option, value in forbidden_failure_inputs.items() if value
            ]
            if supplied:
                raise EvidenceValidationError(
                    "failure diagnostics reject non-minimal inputs: "
                    + ", ".join(supplied)
                )
            source_state = capture_source_state(args.repo_root)
            manifest = record_failure_diagnostic(FailureEvidenceRequest(
                suite=args.suite,
                run_id=args.run_id,
                started_epoch=args.started_epoch,
                evidence_dir=args.evidence_dir,
                source_state=source_state,
                clean_required=args.require_clean,
                failure_stage=args.failure_stage,
                exit_code=args.exit_code,
                retention_class=args.retention_class,
                expected_git_sha=args.expected_git_sha,
                expected_git_tree=args.expected_git_tree,
            ))
        else:
            if args.exit_code is not None:
                raise EvidenceValidationError(
                    "--exit-code is accepted only with --failure-stage"
                )
            if args.phase is None:
                raise EvidenceValidationError(
                    "success evidence requires explicit --phase prepare or --phase finalize"
                )
            if args.phase == "prepare":
                if args.reports_dir is None:
                    raise EvidenceValidationError(
                        "--reports-dir is required for prepare"
                    )
                source_state = capture_source_state(args.repo_root)
                expected = _parse_expected(args.expected)
                manifest = prepare_success_evidence(EvidenceRequest(
                    suite=args.suite,
                    run_id=args.run_id,
                    started_epoch=args.started_epoch,
                    reports_dir=args.reports_dir,
                    evidence_dir=args.evidence_dir,
                    expected_classes=expected,
                    source_state=source_state,
                    require_clean=args.require_clean,
                    metadata=_parse_metadata(args.metadata),
                    expected_user_home=args.expected_user_home,
                    require_all_reports_green=args.require_all_reports_green,
                    expected_git_sha=args.expected_git_sha,
                    expected_git_tree=args.expected_git_tree,
                    retention_class=args.retention_class,
                ))
            else:
                forbidden_finalize_inputs = {
                    "--reports-dir": args.reports_dir,
                    "--expected-user-home": args.expected_user_home,
                    "--require-all-reports-green": args.require_all_reports_green,
                    "--expected": args.expected,
                    "--metadata": args.metadata,
                }
                supplied = [
                    option
                    for option, value in forbidden_finalize_inputs.items()
                    if value
                ]
                if supplied:
                    raise EvidenceValidationError(
                        "finalize rejects prepare-only inputs: " + ", ".join(supplied)
                    )
                manifest = finalize_success_evidence(
                    evidence_dir=args.evidence_dir,
                    suite=args.suite,
                    run_id=args.run_id,
                    started_epoch=args.started_epoch,
                    expected_git_sha=args.expected_git_sha,
                    expected_git_tree=args.expected_git_tree,
                    retention_class=args.retention_class,
                    clean_required=args.require_clean,
                    source_state_reader=lambda: capture_source_state(args.repo_root),
                )
    except (EvidenceValidationError, OSError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1

    if manifest["status"] == "failed":
        print(
            f"RECORDED: {args.suite} failure diagnostic archived; "
            f"stage={manifest['failure']['stage']}; run-id={args.run_id}"
        )
        return 0

    if manifest["status"] == "prepared":
        print(
            f"PREPARED: {args.suite} evidence validated but not release-eligible; "
            f"run-id={args.run_id}"
        )
        return 0

    totals = manifest["totals"]
    print(
        f"PASS: {args.suite} evidence archived with tests={totals['tests']}, "
        f"skipped=0, failures=0, errors=0; run-id={args.run_id}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

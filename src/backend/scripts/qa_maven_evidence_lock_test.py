#!/usr/bin/env python3
from __future__ import annotations

import os
import re
import shutil
import shlex
import signal
import stat
import subprocess
import tempfile
import time
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "scripts/qa_maven_evidence_lock.sh"
KEEPER = ROOT / "scripts/qa_maven_evidence_lock.py"
RBAC = ROOT / "scripts/qa_rbac_suite.sh"
INTEGRATION = ROOT / "scripts/qa_integration_profile.sh"
GIT_SHA = "a" * 40


class MavenEvidenceLockTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.temp = Path(self.temp_dir.name)
        self.lock_root = self.temp / "lock-root"
        self.lock_root.mkdir(mode=0o700)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _environment(self, root: Path | str | None = None) -> dict[str, str]:
        environment = os.environ.copy()
        environment["VANRIDES_QA_LOCK_ROOT"] = str(
            self.lock_root if root is None else root
        )
        return environment

    def _run_shell(
        self,
        body: str,
        *,
        root: Path | str | None = None,
        module: Path = MODULE,
        timeout: float = 10,
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["bash", "-c", 'source "$1"; shift; ' + body, "lock-test", str(module)],
            cwd=ROOT,
            env=self._environment(root),
            text=True,
            capture_output=True,
            timeout=timeout,
            check=False,
        )

    def _start_holder(
        self,
        *,
        suite: str = "integration",
        run_id: str = "holder-run",
        body_after_ready: str = 'while IFS= read -r line; do [[ "$line" == stop ]] && break; done',
    ) -> subprocess.Popen[str]:
        body = (
            'source "$1"; '
            f'qa_maven_evidence_lock_acquire {suite} {run_id} {GIT_SHA} || exit $?; '
            'printf "READY %s\\n" "$$"; '
            f"{body_after_ready}; "
            "qa_maven_evidence_lock_release"
        )
        process = subprocess.Popen(
            ["bash", "-c", body, "lock-holder", str(MODULE)],
            cwd=ROOT,
            env=self._environment(),
            text=True,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        assert process.stdout is not None
        ready = process.stdout.readline().strip()
        self.assertRegex(ready, r"^READY [1-9][0-9]*$")
        return process

    def _stop_holder(self, holder: subprocess.Popen[str]) -> tuple[str, str]:
        assert holder.stdin is not None
        holder.stdin.write("stop\n")
        holder.stdin.flush()
        stdout, stderr = holder.communicate(timeout=5)
        self.assertEqual(holder.returncode, 0, stderr)
        return stdout, stderr

    def _acquire_and_release(self, run_id: str = "probe-run") -> subprocess.CompletedProcess[str]:
        return self._run_shell(
            f"qa_maven_evidence_lock_acquire rbac {run_id} {GIT_SHA} && "
            "qa_maven_evidence_lock_release"
        )

    def _wait_until_acquirable(self, timeout: float = 5) -> subprocess.CompletedProcess[str]:
        deadline = time.monotonic() + timeout
        last: subprocess.CompletedProcess[str] | None = None
        while time.monotonic() < deadline:
            last = self._acquire_and_release("recovery-probe")
            if last.returncode == 0:
                return last
            time.sleep(0.05)
        assert last is not None
        self.fail(f"lock did not become acquirable: {last.stderr}")

    @staticmethod
    def _owner_payload(
        *,
        pid: int,
        process_start_id: str,
        run_id: str = "stale-owner",
    ) -> str:
        return (
            "schema_version=1\n"
            f"pid={pid}\n"
            f"process_start_id={process_start_id}\n"
            f"owner_token={'c' * 32}\n"
            "suite=integration\n"
            f"run_id={run_id}\n"
            "started_epoch=1\n"
            f"git_sha={GIT_SHA}\n"
        )

    def test_module_and_keeper_exist_and_source_has_no_side_effect(self) -> None:
        self.assertTrue(MODULE.is_file())
        self.assertTrue(KEEPER.is_file())
        result = self._run_shell("declare -F qa_maven_evidence_lock_acquire >/dev/null")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(list(self.lock_root.iterdir()), [])

    def test_normal_acquire_release_persists_secure_lock_and_owner_schema(self) -> None:
        result = self._acquire_and_release("normal-run")
        self.assertEqual(result.returncode, 0, result.stderr)

        lock_file = self.lock_root / "maven-evidence.lock"
        owner_file = self.lock_root / "owner"
        self.assertTrue(lock_file.is_file())
        self.assertTrue(owner_file.is_file())
        self.assertEqual(stat.S_IMODE(lock_file.stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE(owner_file.stat().st_mode), 0o600)
        self.assertEqual(lock_file.stat().st_uid, os.getuid())
        self.assertEqual(owner_file.stat().st_uid, os.getuid())
        self.assertFalse(lock_file.is_symlink())
        self.assertFalse(owner_file.is_symlink())

        lines = owner_file.read_text(encoding="utf-8").splitlines()
        self.assertEqual(
            [line.split("=", 1)[0] for line in lines],
            [
                "schema_version",
                "pid",
                "process_start_id",
                "owner_token",
                "suite",
                "run_id",
                "started_epoch",
                "git_sha",
            ],
        )
        owner = dict(line.split("=", 1) for line in lines)
        self.assertEqual(owner["schema_version"], "1")
        self.assertRegex(owner["pid"], r"^[1-9][0-9]*$")
        self.assertRegex(owner["process_start_id"], r"^[a-f0-9]{64}$")
        self.assertRegex(owner["owner_token"], r"^[a-f0-9]{32}$")
        self.assertEqual(owner["suite"], "rbac")
        self.assertEqual(owner["run_id"], "normal-run")
        self.assertRegex(owner["started_epoch"], r"^[1-9][0-9]*$")
        self.assertEqual(owner["git_sha"], GIT_SHA)

        transient = [
            child.name
            for child in self.lock_root.iterdir()
            if child.name not in {"maven-evidence.lock", "owner"}
        ]
        self.assertEqual(transient, [])

    def test_lock_inode_is_persistent_across_runs(self) -> None:
        first = self._acquire_and_release("first-run")
        self.assertEqual(first.returncode, 0, first.stderr)
        lock_file = self.lock_root / "maven-evidence.lock"
        first_identity = (lock_file.stat().st_dev, lock_file.stat().st_ino)

        second = self._acquire_and_release("second-run")
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(
            (lock_file.stat().st_dev, lock_file.stat().st_ino), first_identity
        )

    def test_live_holder_blocks_with_sanitized_diagnostics(self) -> None:
        holder = self._start_holder(run_id="live-holder")
        try:
            result = self._acquire_and_release("blocked-probe")
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("BLOCKED", result.stderr)
            self.assertIn("suite=integration", result.stderr)
            self.assertIn("run_id=live-holder", result.stderr)
            self.assertRegex(result.stderr, r"pid=[1-9][0-9]*")
            owner_token = dict(
                line.split("=", 1)
                for line in (self.lock_root / "owner")
                .read_text(encoding="utf-8")
                .splitlines()
            )["owner_token"]
            self.assertNotIn(owner_token, result.stderr)
        finally:
            self._stop_holder(holder)

    def test_invalid_owner_variants_while_occupied_stay_blocked(self) -> None:
        variants: tuple[tuple[str, bytes | None], ...] = (
            ("missing", None),
            ("truncated", b"schema_version=1\npid=broken\n"),
            (
                "duplicate",
                (
                    self._owner_payload(pid=os.getpid(), process_start_id="d" * 64)
                    + "pid=42\n"
                ).encode("ascii"),
            ),
            (
                "unknown-key",
                self._owner_payload(
                    pid=os.getpid(), process_start_id="d" * 64
                ).replace("suite=integration", "unexpected=value").encode("ascii"),
            ),
            (
                "malicious-control",
                self._owner_payload(
                    pid=os.getpid(), process_start_id="d" * 64
                ).replace("run_id=stale-owner", "run_id=bad\x00value").encode(
                    "ascii"
                ),
            ),
        )
        for label, owner_contents in variants:
            with self.subTest(label=label):
                holder = self._start_holder(run_id="unknown-owner")
                owner_file = self.lock_root / "owner"
                if owner_contents is None:
                    owner_file.unlink()
                else:
                    owner_file.write_bytes(owner_contents)
                    owner_file.chmod(0o600)
                try:
                    result = self._acquire_and_release("blocked-unknown")
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("BLOCKED", result.stderr)
                    self.assertIn("owner=unknown", result.stderr)
                finally:
                    self._stop_holder(holder)

    def test_stale_regular_owner_is_replaced_only_after_kernel_lock(self) -> None:
        first = self._acquire_and_release("stale-source")
        self.assertEqual(first.returncode, 0, first.stderr)
        owner_file = self.lock_root / "owner"
        variants = (
            ("dead-pid", self._owner_payload(pid=99_999_999, process_start_id="d" * 64)),
            (
                "reused-pid",
                self._owner_payload(pid=os.getpid(), process_start_id="0" * 64),
            ),
            ("corrupt", "corrupt-but-regular\n"),
        )
        for label, contents in variants:
            with self.subTest(label=label):
                owner_file.write_text(contents, encoding="utf-8")
                owner_file.chmod(0o600)
                run_id = f"replacement-{label}"
                result = self._acquire_and_release(run_id)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn(
                    f"run_id={run_id}\n", owner_file.read_text(encoding="utf-8")
                )

    def test_invalid_roots_fail_closed(self) -> None:
        unsafe = self.temp / "unsafe"
        unsafe.mkdir(mode=0o755)
        # mkdir is filtered by the caller's umask; this fixture must be unsafe.
        unsafe.chmod(0o755)
        self.assertEqual(stat.S_IMODE(unsafe.stat().st_mode), 0o755)
        target = self.temp / "target"
        target.mkdir(mode=0o700)
        symlink = self.temp / "root-link"
        symlink.symlink_to(target, target_is_directory=True)
        cases: tuple[tuple[str, Path | str], ...] = (
            ("empty", ""),
            ("relative", "relative-lock-root"),
            ("filesystem-root", "/"),
            ("missing", self.temp / "missing"),
            ("unsafe-mode", unsafe),
            ("symlink", symlink),
        )
        for label, root in cases:
            with self.subTest(label=label):
                result = self._run_shell(
                    f"qa_maven_evidence_lock_acquire rbac invalid-root {GIT_SHA}",
                    root=root,
                )
                self.assertNotEqual(result.returncode, 0)

    def test_invalid_arguments_fail_before_creating_lock(self) -> None:
        cases = (
            ("suite", ("bad suite", "run", GIT_SHA)),
            ("run-id", ("rbac", "../../run", GIT_SHA)),
            ("git-sha", ("rbac", "run", "not-a-sha")),
            ("arity", ("rbac", "run", GIT_SHA, "extra")),
        )
        for label, arguments in cases:
            with self.subTest(label=label):
                result = self._run_shell(
                    "qa_maven_evidence_lock_acquire "
                    + " ".join(shlex.quote(value) for value in arguments)
                )
                self.assertNotEqual(result.returncode, 0)
        self.assertEqual(list(self.lock_root.iterdir()), [])

    def test_lock_and_owner_symlinks_or_unsafe_files_fail_closed(self) -> None:
        outside = self.temp / "outside"
        outside.write_text("sentinel\n", encoding="utf-8")

        (self.lock_root / "maven-evidence.lock").symlink_to(outside)
        lock_result = self._acquire_and_release("lock-symlink")
        self.assertNotEqual(lock_result.returncode, 0)
        self.assertEqual(outside.read_text(encoding="utf-8"), "sentinel\n")
        (self.lock_root / "maven-evidence.lock").unlink()

        initial = self._acquire_and_release("owner-primer")
        self.assertEqual(initial.returncode, 0, initial.stderr)
        owner = self.lock_root / "owner"
        owner.unlink()
        owner.symlink_to(outside)
        owner_result = self._acquire_and_release("owner-symlink")
        self.assertNotEqual(owner_result.returncode, 0)
        self.assertEqual(outside.read_text(encoding="utf-8"), "sentinel\n")

    def test_non_regular_or_insecure_lock_and_owner_files_fail_closed(self) -> None:
        lock_file = self.lock_root / "maven-evidence.lock"
        owner_file = self.lock_root / "owner"

        lock_file.mkdir(mode=0o700)
        result = self._acquire_and_release("lock-directory")
        self.assertNotEqual(result.returncode, 0)
        lock_file.rmdir()

        lock_file.write_text("", encoding="utf-8")
        lock_file.chmod(0o644)
        result = self._acquire_and_release("lock-mode")
        self.assertNotEqual(result.returncode, 0)
        lock_file.chmod(0o600)

        owner_file.mkdir(mode=0o700)
        result = self._acquire_and_release("owner-directory")
        self.assertNotEqual(result.returncode, 0)
        owner_file.rmdir()

        owner_file.write_text("stale\n", encoding="utf-8")
        owner_file.chmod(0o644)
        result = self._acquire_and_release("owner-mode")
        self.assertNotEqual(result.returncode, 0)

    def test_default_root_is_shared_by_git_worktrees(self) -> None:
        repository = self.temp / "repository"
        worktree = self.temp / "worktree"
        repository.mkdir()
        subprocess.run(["git", "init", "-q"], cwd=repository, check=True)
        (repository / "tracked.txt").write_text("lock test\n", encoding="utf-8")
        subprocess.run(["git", "add", "tracked.txt"], cwd=repository, check=True)
        subprocess.run(
            [
                "git",
                "-c",
                "user.name=Lock Test",
                "-c",
                "user.email=lock@example.invalid",
                "commit",
                "-qm",
                "test: seed repository",
            ],
            cwd=repository,
            check=True,
        )
        subprocess.run(
            ["git", "worktree", "add", "-q", "-b", "lock-probe", str(worktree)],
            cwd=repository,
            check=True,
        )
        environment = os.environ.copy()
        environment.pop("VANRIDES_QA_LOCK_ROOT", None)
        holder = subprocess.Popen(
            [
                "bash",
                "-c",
                'source "$1"; '
                f"qa_maven_evidence_lock_acquire integration main-worktree {GIT_SHA} || exit $?; "
                'echo READY; while IFS= read -r line; do [[ "$line" == stop ]] && break; done; '
                "qa_maven_evidence_lock_release",
                "main-holder",
                str(MODULE),
            ],
            cwd=repository,
            env=environment,
            text=True,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        assert holder.stdout is not None
        self.assertEqual(holder.stdout.readline().strip(), "READY")
        try:
            challenger = subprocess.run(
                [
                    "bash",
                    "-c",
                    'source "$1"; '
                    f"qa_maven_evidence_lock_acquire rbac linked-worktree {GIT_SHA}",
                    "worktree-challenger",
                    str(MODULE),
                ],
                cwd=worktree,
                env=environment,
                text=True,
                capture_output=True,
                timeout=5,
                check=False,
            )
            self.assertNotEqual(challenger.returncode, 0)
            self.assertIn("BLOCKED", challenger.stderr)
            common_root = repository / ".git/vanrides-qa-lock"
            self.assertEqual(stat.S_IMODE(common_root.stat().st_mode), 0o700)
        finally:
            assert holder.stdin is not None
            holder.stdin.write("stop\n")
            holder.stdin.flush()
            _, stderr = holder.communicate(timeout=5)
            self.assertEqual(holder.returncode, 0, stderr)

    def test_release_with_token_or_keeper_mismatch_does_not_unlock(self) -> None:
        body = f"""
qa_maven_evidence_lock_acquire rbac mismatch-run {GIT_SHA} || exit $?
saved_token="$_QA_MAVEN_EVIDENCE_LOCK_OWNER_TOKEN"
_QA_MAVEN_EVIDENCE_LOCK_OWNER_TOKEN={'b' * 32}
if qa_maven_evidence_lock_release; then exit 91; fi
_QA_MAVEN_EVIDENCE_LOCK_OWNER_TOKEN="$saved_token"
saved_keeper="$_QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID"
_QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID=$$
if qa_maven_evidence_lock_release; then exit 92; fi
_QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID="$saved_keeper"
qa_maven_evidence_lock_release
"""
        result = self._run_shell(body)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_two_concurrent_acquirers_never_both_own_lock(self) -> None:
        holder = self._start_holder(suite="rbac", run_id="racer-one")
        try:
            challenger = self._acquire_and_release("racer-two")
            self.assertEqual(1, challenger.returncode, challenger.stderr)
            self.assertIn("BLOCKED", challenger.stderr)
        finally:
            self._stop_holder(holder)

        recovered = self._acquire_and_release("post-race-probe")
        self.assertEqual(0, recovered.returncode, recovered.stderr)

    def test_sigkill_without_child_releases_when_control_fd_closes(self) -> None:
        holder = self._start_holder(run_id="sigkill-no-child")
        os.kill(holder.pid, signal.SIGKILL)
        holder.communicate(timeout=5)
        self.assertEqual(holder.returncode, -signal.SIGKILL)
        recovered = self._wait_until_acquirable()
        self.assertEqual(recovered.returncode, 0, recovered.stderr)

    def test_sigkill_with_inheriting_child_holds_until_child_exits(self) -> None:
        body = (
            'source "$1"; '
            f"qa_maven_evidence_lock_acquire integration inherited-child {GIT_SHA} || exit $?; "
            'sleep 1.2 & child=$!; printf "READY %s %s\\n" "$$" "$child"; wait "$child"'
        )
        holder = subprocess.Popen(
            ["bash", "-c", body, "inherited", str(MODULE)],
            cwd=ROOT,
            env=self._environment(),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        assert holder.stdout is not None
        ready = holder.stdout.readline().strip()
        self.assertRegex(ready, r"^READY [1-9][0-9]* [1-9][0-9]*$")
        child_pid = int(ready.split()[2])
        os.kill(holder.pid, signal.SIGKILL)
        holder.wait(timeout=5)

        blocked = self._acquire_and_release("child-still-running")
        self.assertNotEqual(blocked.returncode, 0)
        self.assertIn("BLOCKED", blocked.stderr)
        deadline = time.monotonic() + 4
        while time.monotonic() < deadline:
            try:
                os.kill(child_pid, 0)
            except ProcessLookupError:
                break
            time.sleep(0.05)
        if holder.stdout is not None:
            holder.stdout.close()
        if holder.stderr is not None:
            holder.stderr.close()
        recovered = self._wait_until_acquirable()
        self.assertEqual(recovered.returncode, 0, recovered.stderr)

    def test_normal_release_waits_for_inheriting_child_to_exit(self) -> None:
        body = (
            'source "$1"; '
            f"qa_maven_evidence_lock_acquire integration release-child {GIT_SHA} || exit $?; "
            "sleep 0.8 & child=$!; "
            'printf "READY %s\\n" "$child"; '
            "qa_maven_evidence_lock_release"
        )
        started = time.monotonic()
        result = subprocess.run(
            ["bash", "-c", body, "release-child", str(MODULE)],
            cwd=ROOT,
            env=self._environment(),
            capture_output=True,
            text=True,
            timeout=5,
        )
        elapsed = time.monotonic() - started

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertRegex(result.stdout, r"^READY [1-9][0-9]*\n$")
        self.assertGreaterEqual(elapsed, 0.65)
        recovered = self._wait_until_acquirable()
        self.assertEqual(recovered.returncode, 0, recovered.stderr)

    def test_handshake_timeout_fails_closed_and_cleans_transient_channel(self) -> None:
        fake_scripts = self.temp / "scripts"
        fake_scripts.mkdir()
        fake_module = fake_scripts / MODULE.name
        shutil.copy2(MODULE, fake_module)
        fake_keeper = fake_scripts / KEEPER.name
        fake_keeper.write_text(
            "#!/usr/bin/env python3\nimport time\ntime.sleep(30)\n",
            encoding="utf-8",
        )
        result = self._run_shell(
            f"qa_maven_evidence_lock_acquire rbac timeout-run {GIT_SHA}",
            module=fake_module,
            timeout=8,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("handshake", result.stderr.lower())
        self.assertEqual(list(self.lock_root.iterdir()), [])

    def test_runners_use_only_shared_lock_interface_and_keep_signal_contract(self) -> None:
        for runner in (RBAC, INTEGRATION):
            with self.subTest(runner=runner.name):
                source = runner.read_text(encoding="utf-8")
                self.assertIn("qa_maven_evidence_lock.sh", source)
                self.assertIn("qa_maven_evidence_lock_acquire", source)
                self.assertIn("qa_maven_evidence_lock_release", source)
                self.assertNotIn("maven-evidence.lock", source)
                self.assertNotRegex(source, r"mkdir\s+\"?\$LOCK_DIR")
                self.assertNotRegex(source, r"printf .*LOCK_DIR/owner")
                self.assertIn("forward_signal_to_maven TERM 130", source)
                self.assertIn("forward_signal_to_maven TERM 143", source)


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
from __future__ import annotations

import errno
import fcntl
import os
import re
import shutil
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT.parents[1] / ".github/workflows/backend.yml"
CI_SCRIPT = ROOT / "scripts/ci_quality_gate.sh"
CARD_GATE_SCRIPT = ROOT / "scripts/qa_gate_card.sh"
INTEGRATION_PROFILE_SCRIPT = ROOT / "scripts/qa_integration_profile.sh"
RBAC_SCRIPT = ROOT / "scripts/qa_rbac_suite.sh"
MAVEN_EVIDENCE_LOCK_SCRIPT = ROOT / "scripts/qa_maven_evidence_lock.sh"
MAVEN_EVIDENCE_LOCK_KEEPER = ROOT / "scripts/qa_maven_evidence_lock.py"
REPORT_EVIDENCE_SCRIPT = ROOT / "scripts/qa_report_evidence.py"
REPORT_EVIDENCE_TEST = ROOT / "scripts/qa_report_evidence_test.py"
LOCATION_READ_SMOKE = ROOT / "scripts/qa_location_read_smoke.sh"
LOCATION_READ_SMOKE_TEST = ROOT / "scripts/qa_location_read_smoke_test.py"
RATE_LIMIT_LOAD = ROOT / "scripts/qa_rate_limit_trust_load.py"
RATE_LIMIT_LOAD_TEST = ROOT / "scripts/qa_rate_limit_trust_load_test.py"
RATE_LIMIT_SMOKE = ROOT / "scripts/qa_rate_limit_trust_smoke.sh"
RATE_LIMIT_SMOKE_TEST = ROOT / "scripts/qa_rate_limit_trust_smoke_test.py"
EMAIL_CONFIRMATION_INTENT_SMOKE = ROOT / (
    "scripts/qa_email_confirmation_intent_smoke.sh"
)
EMAIL_CONFIRMATION_INTENT_SMOKE_TEST = ROOT / (
    "scripts/qa_email_confirmation_intent_smoke_test.py"
)
CODEX_CLOUD_DOC = ROOT / "docs/codex-cloud.md"
CODEX_CLOUD_SETUP = ROOT / "scripts/codex_cloud_setup.sh"
DOCKER_OPTIONAL_TESTCONTAINERS = re.compile(
    r"@\s*(?:[A-Za-z_$][A-Za-z0-9_$]*\s*\.\s*)*Testcontainers\b"
)


def _strip_java_comments(source: str) -> str:
    """Remove Java comments without turning comment markers in literals into syntax."""
    output: list[str] = []
    index = 0
    state = "code"
    while index < len(source):
        current = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "code":
            if current == "/" and following == "/":
                output.extend((" ", " "))
                index += 2
                state = "line-comment"
                continue
            if current == "/" and following == "*":
                output.extend((" ", " "))
                index += 2
                state = "block-comment"
                continue
            if current == '"':
                state = "string"
            elif current == "'":
                state = "character"
            output.append(current)
            index += 1
            continue
        if state == "line-comment":
            output.append("\n" if current == "\n" else " ")
            index += 1
            if current == "\n":
                state = "code"
            continue
        if state == "block-comment":
            if current == "*" and following == "/":
                output.extend((" ", " "))
                index += 2
                state = "code"
            else:
                output.append("\n" if current == "\n" else " ")
                index += 1
            continue

        output.append(current)
        index += 1
        if current == "\\" and index < len(source):
            output.append(source[index])
            index += 1
        elif (state == "string" and current == '"') or (
            state == "character" and current == "'"
        ):
            state = "code"
    return "".join(output)


def _annotation_arguments(source: str, opening_parenthesis: int) -> str:
    depth = 0
    state = "code"
    for index in range(opening_parenthesis, len(source)):
        current = source[index]
        if state == "code":
            if current == '"':
                state = "string"
            elif current == "'":
                state = "character"
            elif current == "(":
                depth += 1
            elif current == ")":
                depth -= 1
                if depth == 0:
                    return source[opening_parenthesis + 1 : index]
        elif current == "\\":
            state = "escape-string" if state == "string" else "escape-character"
        elif (state == "string" and current == '"') or (
            state == "character" and current == "'"
        ):
            state = "code"
        elif state == "escape-string":
            state = "string"
        elif state == "escape-character":
            state = "character"
    raise AssertionError("@Testcontainers possui parenteses desbalanceados")


def _declares_docker_optional_testcontainers(source: str) -> bool:
    comment_free = _strip_java_comments(source)
    for match in DOCKER_OPTIONAL_TESTCONTAINERS.finditer(comment_free):
        cursor = match.end()
        while cursor < len(comment_free) and comment_free[cursor].isspace():
            cursor += 1
        if cursor >= len(comment_free) or comment_free[cursor] != "(":
            continue
        arguments = _annotation_arguments(comment_free, cursor)
        assignment = re.search(r"\bdisabledWithoutDocker\s*=", arguments)
        if assignment is None:
            continue
        value = arguments[assignment.end() :].split(",", maxsplit=1)[0]
        # Fail closed for constants and equivalent expressions. Only an explicit
        # literal false is known not to make Docker optional.
        if re.sub(r"\s+", "", value) != "false":
            return True
    return False


def _discover_docker_optional_test_classes(source_root: Path) -> set[str]:
    records: dict[str, tuple[bool, bool, str | None]] = {}
    for source in source_root.rglob("*.java"):
        text = source.read_text(encoding="utf-8")
        comment_free = _strip_java_comments(text)
        package_match = re.search(
            r"^\s*package\s+([A-Za-z0-9_.]+);", comment_free, re.MULTILINE
        )
        class_match = re.search(
            rf"\b(?P<abstract>abstract\s+)?class\s+{re.escape(source.stem)}\b"
            r"(?:\s+extends\s+(?P<base>[A-Za-z0-9_$.]+))?",
            comment_free,
        )
        if class_match is None:
            if _declares_docker_optional_testcontainers(text):
                raise AssertionError(
                    "Docker-optional source uses an unsupported top-level type: "
                    f"{source}"
                )
            continue
        if package_match is None:
            raise AssertionError(f"package missing in {source}")
        package_name = package_match.group(1)
        class_name = f"{package_name}.{source.stem}"
        base_name = class_match.group("base")
        if base_name and "." not in base_name:
            import_match = re.search(
                rf"^\s*import\s+([A-Za-z0-9_.]+\.{re.escape(base_name)});",
                comment_free,
                re.MULTILINE,
            )
            base_name = (
                import_match.group(1)
                if import_match is not None
                else f"{package_name}.{base_name}"
            )
        records[class_name] = (
            _declares_docker_optional_testcontainers(text),
            class_match.group("abstract") is not None,
            base_name,
        )

    unresolved_bases = {
        name: base_name
        for name, (_, _, base_name) in records.items()
        if base_name is not None and base_name not in records
    }
    if unresolved_bases:
        details = ", ".join(
            f"{name} -> {base_name}"
            for name, base_name in sorted(unresolved_bases.items())
        )
        raise AssertionError(
            "Test inheritance cannot be proven for Docker-optional discovery: "
            + details
        )

    inherited_optional = {name for name, (direct, _, _) in records.items() if direct}
    while True:
        expanded = inherited_optional | {
            name
            for name, (_, _, base_name) in records.items()
            if base_name in inherited_optional
        }
        if expanded == inherited_optional:
            break
        inherited_optional = expanded
    return {
        name
        for name in inherited_optional
        if name in records and not records[name][1]
    }


class BackendCiQualityGateWiringTest(unittest.TestCase):
    @staticmethod
    def _validator_invocations(arguments: list[str]) -> list[list[str]]:
        invocations: list[list[str]] = []
        current: list[str] | None = None
        for argument in arguments:
            if argument == "__CALL__":
                current = []
                invocations.append(current)
            elif current is not None:
                current.append(argument)
        return invocations

    def _observe_released_maven_evidence_lock(
        self, lock_root: Path
    ) -> tuple[tuple[str, ...], tuple[str, ...], bool]:
        entries = tuple(sorted(path.name for path in lock_root.iterdir()))
        transient_entries = tuple(
            name
            for name in entries
            if name not in {"maven-evidence.lock", "owner"}
        )
        lock_path = lock_root / "maven-evidence.lock"
        if not lock_path.is_file() or lock_path.is_symlink():
            return entries, transient_entries, False

        descriptor = os.open(lock_path, os.O_RDWR)
        lock_is_reacquirable = False
        try:
            try:
                fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
                lock_is_reacquirable = True
            except OSError as error:
                if error.errno not in (errno.EACCES, errno.EAGAIN):
                    raise
            if lock_is_reacquirable:
                fcntl.flock(descriptor, fcntl.LOCK_UN)
        finally:
            os.close(descriptor)
        return entries, transient_entries, lock_is_reacquirable

    def _run_profile_with_fake_runtime(
        self,
        *arguments: str,
        docker_min_api: str = "1.44",
        docker_max_api: str = "1.53",
        environment_overrides: dict[str, str] | None = None,
        validator_exit: int = 0,
        lock_release_exit: int | None = None,
        shadow_compgen: bool = False,
        module_relative: str | None = None,
        fake_dirty: bool = False,
        lock_observations: list[
            tuple[tuple[str, ...], tuple[str, ...], bool]
        ]
        | None = None,
    ) -> tuple[subprocess.CompletedProcess[str], list[str], list[str], list[str]]:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            repository = temp / "project"
            project = repository / module_relative if module_relative else repository
            scripts = project / "scripts"
            fake_bin = temp / "bin"
            lock_root = temp / "maven-evidence-lock"
            scripts.mkdir(parents=True)
            fake_bin.mkdir()
            lock_root.mkdir(mode=0o700)
            lock_root.chmod(0o700)

            profile = scripts / "qa_integration_profile.sh"
            shutil.copy2(INTEGRATION_PROFILE_SCRIPT, profile)
            shutil.copy2(
                MAVEN_EVIDENCE_LOCK_SCRIPT,
                scripts / "qa_maven_evidence_lock.sh",
            )
            shutil.copy2(
                MAVEN_EVIDENCE_LOCK_KEEPER,
                scripts / "qa_maven_evidence_lock.py",
            )
            with (scripts / "qa_maven_evidence_lock.sh").open(
                "a", encoding="utf-8"
            ) as lock_script:
                lock_script.write(
                    "\nif [[ -n \"${FAKE_LOCK_RELEASE_EXIT:-}\" ]]; then\n"
                    "  qa_maven_evidence_lock_release() {\n"
                    "    return \"$FAKE_LOCK_RELEASE_EXIT\"\n"
                    "  }\n"
                    "fi\n"
                )

            maven_capture = temp / "maven-args.txt"
            validator_capture = temp / "validator-args.txt"
            docker_capture = temp / "docker-calls.txt"
            fake_maven = project / "mvnw"
            fake_maven.write_text(
                "#!/usr/bin/env bash\n"
                "if [[ \"${MAVEN_SKIP_RC:-}\" != '1' ]]; then\n"
                "  echo 'MAVEN_SKIP_RC must be 1' >&2\n"
                "  exit 86\n"
                "fi\n"
                "printf '%s\\n' \"$@\" > \"$FAKE_MAVEN_ARGS_CAPTURE\"\n"
                "if [[ -n \"${FAKE_MAVEN_HOLD_AFTER_SIGNAL:-}\" ]]; then\n"
                "  sleeper_pid=''\n"
                "  trap 'kill \"$sleeper_pid\" 2>/dev/null || true; "
                "echo MAVEN_RECEIVED_INT >&2; exit 130' INT\n"
                "  trap 'kill \"$sleeper_pid\" 2>/dev/null || true; "
                "echo MAVEN_RECEIVED_TERM >&2; exit 143' TERM\n"
                "  sleep 2 &\n"
                "  sleeper_pid=$!\n"
                "  profile_pid=$PPID\n"
                "  (sleep 0.2; kill -\"$FAKE_MAVEN_HOLD_AFTER_SIGNAL\" "
                "\"$profile_pid\") &\n"
                "  wait \"$sleeper_pid\"\n"
                "  exit 91\n"
                "fi\n"
                "exit \"${FAKE_MAVEN_EXIT:-0}\"\n",
                encoding="utf-8",
            )
            fake_maven.chmod(0o755)

            fake_validator = scripts / "qa_report_evidence.py"
            fake_validator.write_text(
                "#!/usr/bin/env python3\n"
                "import os\n"
                "import sys\n"
                "from pathlib import Path\n"
                "capture = Path(os.environ['VALIDATOR_ARGS_CAPTURE'])\n"
                "with capture.open('a', encoding='utf-8') as handle:\n"
                "    handle.write('__CALL__\\n')\n"
                "    handle.write('\\n'.join(sys.argv[1:]))\n"
                "    handle.write('\\n')\n"
                "if '--phase' in sys.argv and "
                "sys.argv[sys.argv.index('--phase') + 1] == 'finalize' and "
                "os.environ.get('FAKE_FINALIZE_PREPARED_EXIT'):\n"
                "    evidence = Path(sys.argv[sys.argv.index('--evidence-dir') + 1])\n"
                "    evidence.mkdir(parents=True, exist_ok=True)\n"
                "    (evidence / 'manifest.json').write_text("
                "'{\"status\":\"prepared\"}\\n', encoding='utf-8')\n"
                "    raise SystemExit(int(os.environ['FAKE_FINALIZE_PREPARED_EXIT']))\n"
                "exit_code = int(os.environ.get('FAKE_VALIDATOR_EXIT', '0'))\n"
                "if exit_code:\n"
                "    print('suite is not green: skipped=1', file=sys.stderr)\n"
                "raise SystemExit(exit_code)\n",
                encoding="utf-8",
            )

            fake_docker = fake_bin / "docker"
            fake_docker.write_text(
                "#!/usr/bin/env bash\n"
                "printf '%s\\n' \"$*\" >> \"$DOCKER_CALLS_CAPTURE\"\n"
                "if [[ \"${1:-} ${2:-}\" == 'context show' ]]; then\n"
                "  printf '%s\\n' \"${FAKE_DOCKER_CONTEXT_NAME-local-test}\"\n"
                "  exit 0\n"
                "fi\n"
                "if [[ \"${1:-} ${2:-}\" == 'context inspect' ]]; then\n"
                "  printf '%s\\n' \"${FAKE_DOCKER_ENDPOINT-unix:///tmp/docker.sock}\"\n"
                "  exit 0\n"
                "fi\n"
                "if [[ \"${1:-}\" == 'info' ]]; then exit 0; fi\n"
                "if [[ \"${1:-}\" == 'version' ]]; then\n"
                "  case \"$*\" in\n"
                "    *Server.MinAPIVersion*) printf '%s\\n' \"${FAKE_DOCKER_MIN_API-1.44}\" ;;\n"
                "    *Server.APIVersion*) printf '%s\\n' \"${FAKE_DOCKER_MAX_API-1.53}\" ;;\n"
                "    *) exit 87 ;;\n"
                "  esac\n"
                "  exit 0\n"
                "fi\n"
                "exit 88\n",
                encoding="utf-8",
            )
            fake_docker.chmod(0o755)

            fake_git = fake_bin / "git"
            fake_git.write_text(
                "#!/usr/bin/env bash\n"
                "case \"$*\" in\n"
                f"  *'rev-parse --show-toplevel') printf '%s\\n' '{repository}' ;;\n"
                "  *'rev-parse HEAD') printf '%040d\\n' 1 ;;\n"
                "  *'rev-parse HEAD^{tree}') printf '%040d\\n' 2 ;;\n"
                + ("  *'status --porcelain --untracked-files=all') printf ' M elsewhere.txt\\n' ;;\n"
                 if fake_dirty else "  *'status --porcelain --untracked-files=all') exit 0 ;;\n")
                +
                "  *) exit 89 ;;\n"
                "esac\n",
                encoding="utf-8",
            )
            fake_git.chmod(0o755)

            environment = os.environ.copy()
            for variable in (
                "TESTCONTAINERS_API_VERSION",
                "DOCKER_HOST",
                "DOCKER_CONTEXT",
                "DOCKER_TLS_VERIFY",
                "DOCKER_CERT_PATH",
                "DOCKER_API_VERSION",
                "TESTCONTAINERS_HOST_OVERRIDE",
                "TESTCONTAINERS_RYUK_DISABLED",
                "JAVA_TOOL_OPTIONS",
                "_JAVA_OPTIONS",
                "JDK_JAVA_OPTIONS",
                "MAVEN_OPTS",
                "MAVEN_ARGS",
                "MAVEN_USER_HOME",
                "MAVEN_SKIP_RC",
            ):
                environment.pop(variable, None)
            for variable in tuple(environment):
                if variable.startswith(("MAVEN_", "MVNW_")):
                    environment.pop(variable, None)
            original_home = temp / "original-home"
            (original_home / ".m2/repository").mkdir(parents=True)
            (original_home / ".testcontainers.properties").write_text(
                "docker.host=tcp://remote.example:2376\n",
                encoding="utf-8",
            )
            environment.update(
                {
                    "PATH": f"{fake_bin}{os.pathsep}{environment['PATH']}",
                    "TMPDIR": str(temp / "locks"),
                    "FAKE_MAVEN_ARGS_CAPTURE": str(maven_capture),
                    "VALIDATOR_ARGS_CAPTURE": str(validator_capture),
                    "DOCKER_CALLS_CAPTURE": str(docker_capture),
                    "FAKE_VALIDATOR_EXIT": str(validator_exit),
                    "FAKE_DOCKER_MIN_API": docker_min_api,
                    "FAKE_DOCKER_MAX_API": docker_max_api,
                    "VANRIDES_QA_EVIDENCE_ROOT": str(temp / "evidence"),
                    "VANRIDES_QA_LOCK_ROOT": str(lock_root),
                    "HOME": str(original_home),
                }
            )
            (temp / "locks").mkdir()
            if environment_overrides:
                environment.update(environment_overrides)
            if lock_release_exit is not None:
                environment["FAKE_LOCK_RELEASE_EXIT"] = str(lock_release_exit)

            command = [str(profile), *arguments]
            if shadow_compgen:
                command = [
                    "bash",
                    "-c",
                    "compgen() { :; }\n"
                    "export -f compgen\n"
                    'exec "$@"',
                    "profile-with-shadowed-compgen",
                    str(profile),
                    *arguments,
                ]
            result = subprocess.run(
                command,
                cwd=project,
                env=environment,
                capture_output=True,
                text=True,
                check=False,
            )
            maven_args = (
                maven_capture.read_text(encoding="utf-8").splitlines()
                if maven_capture.exists()
                else []
            )
            validator_args = (
                validator_capture.read_text(encoding="utf-8").splitlines()
                if validator_capture.exists()
                else []
            )
            docker_calls = (
                docker_capture.read_text(encoding="utf-8").splitlines()
                if docker_capture.exists()
                else []
            )
            if lock_observations is not None:
                lock_observations.append(
                    self._observe_released_maven_evidence_lock(lock_root)
                )

        return result, maven_args, validator_args, docker_calls

    def _run_rbac_with_maven_signal(
        self, signal_name: str
    ) -> tuple[
        subprocess.CompletedProcess[str],
        list[str],
        tuple[str, ...],
        tuple[str, ...],
        bool,
    ]:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            project = temp / "project"
            scripts = project / "scripts"
            fake_bin = temp / "bin"
            lock_root = temp / "maven-evidence-lock"
            scripts.mkdir(parents=True)
            fake_bin.mkdir()
            lock_root.mkdir(mode=0o700)
            lock_root.chmod(0o700)
            shutil.copy2(RBAC_SCRIPT, scripts / "qa_rbac_suite.sh")
            shutil.copy2(
                MAVEN_EVIDENCE_LOCK_SCRIPT,
                scripts / "qa_maven_evidence_lock.sh",
            )
            shutil.copy2(
                MAVEN_EVIDENCE_LOCK_KEEPER,
                scripts / "qa_maven_evidence_lock.py",
            )

            (project / "mvnw").write_text(
                "#!/usr/bin/env bash\n"
                "sleeper_pid=''\n"
                "trap 'kill \"$sleeper_pid\" 2>/dev/null || true; "
                "echo MAVEN_RECEIVED_INT >&2; exit 130' INT\n"
                "trap 'kill \"$sleeper_pid\" 2>/dev/null || true; "
                "echo MAVEN_RECEIVED_TERM >&2; exit 143' TERM\n"
                "sleep 2 &\n"
                "sleeper_pid=$!\n"
                "profile_pid=$PPID\n"
                "(sleep 0.2; kill -\"$FAKE_MAVEN_SIGNAL_PARENT\" "
                "\"$profile_pid\") &\n"
                "wait \"$sleeper_pid\"\n"
                "exit 91\n",
                encoding="utf-8",
            )
            (project / "mvnw").chmod(0o755)
            validator_capture = temp / "validator-args.txt"
            (scripts / "qa_report_evidence.py").write_text(
                "#!/usr/bin/env python3\n"
                "import os\n"
                "import sys\n"
                "from pathlib import Path\n"
                "Path(os.environ['VALIDATOR_ARGS_CAPTURE']).write_text("
                "'\\n'.join(sys.argv[1:]), encoding='utf-8')\n",
                encoding="utf-8",
            )
            fake_git = fake_bin / "git"
            fake_git.write_text(
                "#!/usr/bin/env bash\n"
                "case \"$*\" in\n"
                "  *'rev-parse --show-toplevel') pwd -P ;;\n"
                "  *'rev-parse HEAD') printf '%040d\\n' 1 ;;\n"
                "  *'rev-parse HEAD^{tree}') printf '%040d\\n' 2 ;;\n"
                "  *) exit 89 ;;\n"
                "esac\n",
                encoding="utf-8",
            )
            fake_git.chmod(0o755)

            environment = os.environ.copy()
            environment.update(
                {
                    "PATH": f"{fake_bin}{os.pathsep}{environment['PATH']}",
                    "FAKE_MAVEN_SIGNAL_PARENT": signal_name,
                    "VALIDATOR_ARGS_CAPTURE": str(validator_capture),
                    "VANRIDES_QA_EVIDENCE_ROOT": str(temp / "evidence"),
                    "VANRIDES_QA_LOCK_ROOT": str(lock_root),
                }
            )
            result = subprocess.run(
                [str(scripts / "qa_rbac_suite.sh")],
                cwd=project,
                env=environment,
                capture_output=True,
                text=True,
                check=False,
            )
            validator_args = (
                validator_capture.read_text(encoding="utf-8").splitlines()
                if validator_capture.exists()
                else []
            )
            entries, transient_entries, lock_is_reacquirable = (
                self._observe_released_maven_evidence_lock(lock_root)
            )

        return (
            result,
            validator_args,
            entries,
            transient_entries,
            lock_is_reacquirable,
        )

    def test_workflow_requires_quick_checks_before_the_full_postgres_matrix(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        quick = workflow.split("\n  quick:\n", 1)[1].split("\n  test:\n", 1)[0]
        canonical = workflow.split("\n  test:\n", 1)[1].split("\n  integration:\n", 1)[0]
        self.assertIn("run: bash scripts/check_backend.sh --light", quick)
        self.assertNotIn("setup-java", quick)
        self.assertIn("runs-on: ubuntu-latest", canonical)
        self.assertIn("needs: quick", canonical)
        self.assertEqual(1, canonical.count("run: ./scripts/qa_integration_profile.sh --all-tests"))
        self.assertIn("working-directory: src/backend", canonical)
        self.assertIn('VANRIDES_QA_REQUIRE_CLEAN: "1"', canonical)
        self.assertIn('VANRIDES_QA_RETENTION_CLASS: ci', canonical)
        self.assertNotIn("skipTests", canonical)
        for forbidden in ("continue-on-error", "target/surefire-reports", "*.txt", "*.dump",
                          "run: ./mvnw -B test", "pull_request_target", "secrets.", "self-hosted"):
            self.assertNotIn(forbidden, workflow)

    def test_workflow_has_read_only_permissions_and_no_external_publication(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("permissions:\n  contents: read", workflow)
        for forbidden in ("packages: write", "contents: write", "build_and_push:", "deploy_swarm:",
                          "docker push", "docker/login-action", "upload-artifact", "ssh "):
            self.assertNotIn(forbidden, workflow)
        self.assertEqual(3, workflow.count("persist-credentials: false"))
        self.assertEqual(3, workflow.count("ref: ${{ github.sha }}"))
        self.assertIn("  push:\n    branches: [main]", workflow)
        self.assertNotIn("cache: maven", workflow)
        self.assertNotIn("cache-dependency-path:", workflow)

    def test_parallel_integration_is_required_and_builds_the_same_clean_revision(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        canonical, integration = workflow.split("\n  test:\n", 1)[1].split("\n  integration:\n", 1)
        integration = integration.split("\n  result:\n", 1)[0]
        for job in (canonical, integration):
            self.assertNotRegex(job, r"(?m)^    (?:if|continue-on-error):")
            self.assertIn("needs: quick", job)
            self.assertIn("runs-on: ubuntu-latest", job)
        self.assertIn("VANRIDES_EXPECTED_SOURCE_SHA: ${{ github.sha }}", integration)
        self.assertEqual(1, integration.count("run: bash scripts/check_backend.sh --integration-only"))
        self.assertNotIn("ci_quality_gate.sh", integration)
        self.assertLess(integration.index("run: bash scripts/check_backend.sh --integration-only"),
                        integration.index("docker build -t vanrides-backend:ci src/backend"))

    def test_local_team_gate_preserves_full_sequence_and_explicit_partial_mode(self) -> None:
        wrapper = ROOT.parents[1] / "scripts/check_backend.sh"
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            scripts = root / "scripts"
            module = root / "src/backend"
            fake_bin = root / "bin"
            scripts.mkdir()
            (module / "scripts").mkdir(parents=True)
            fake_bin.mkdir()
            shutil.copy2(wrapper, scripts / wrapper.name)
            for target, event in ((module / "scripts/ci_quality_gate.sh", "canonical"),
                                  (module / "mvnw", "package"), (fake_bin / "node", "smoke")):
                target.write_text('#!/usr/bin/env bash\nprintf "%s\\n" "' + event + '" >> "$VANRIDES_TEST_TRACE"\n')
                target.chmod(0o755)
            trace = root / "events"
            environment = {**os.environ, "PATH": str(fake_bin) + os.pathsep + os.environ["PATH"],
                           "VANRIDES_TEST_TRACE": str(trace)}
            for arguments, expected in (([], ["canonical", "package", "smoke"]),
                                        (["--integration-only"], ["package", "smoke"])):
                trace.unlink(missing_ok=True)
                result = subprocess.run([str(scripts / wrapper.name), *arguments], env=environment,
                                        capture_output=True, text=True, check=False)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(expected, trace.read_text().splitlines())

    def test_monorepo_profile_passes_the_real_git_root_to_every_evidence_phase(self) -> None:
        result, _, validator_args, _ = self._run_profile_with_fake_runtime(
            "--all-tests", module_relative="src/backend")
        self.assertEqual(0, result.returncode, result.stderr)
        calls = self._validator_invocations(validator_args)
        self.assertEqual(2, len(calls))
        for arguments in calls:
            self.assertTrue(arguments[arguments.index("--repo-root") + 1].endswith("/project"))
            self.assertIn("--expected-git-sha", arguments)
            self.assertIn("--expected-git-tree", arguments)

    def test_monorepo_profile_rejects_an_unexpected_module_path_before_maven(self) -> None:
        result, maven_args, _, docker_calls = self._run_profile_with_fake_runtime(
            module_relative="unexpected/backend")
        self.assertEqual(1, result.returncode)
        self.assertIn("modulo backend esperado", result.stderr)
        self.assertEqual([], maven_args)
        self.assertEqual([], docker_calls)

    def test_monorepo_profile_rejects_dirty_paths_outside_backend_before_maven(self) -> None:
        result, maven_args, _, _ = self._run_profile_with_fake_runtime(
            module_relative="src/backend", fake_dirty=True,
            environment_overrides={"VANRIDES_QA_REQUIRE_CLEAN": "1"})
        self.assertEqual(1, result.returncode)
        self.assertIn("Clean source required", result.stderr)
        self.assertEqual([], maven_args)

    def test_ci_gate_stops_early_and_preserves_all_checks(self) -> None:
        # Execute the real orchestration with process doubles, without Java/Docker.
        quick_checks = [
            "quality_gate.sh", "ci_quality_gate_test.py", "qa_report_evidence_test.py",
            "qa_evidence_retention_test.py", "qa_maven_evidence_lock_test.py",
            "qa_location_read_smoke_test.py", "qa_rate_limit_trust_load_test.py",
            "qa_rate_limit_trust_smoke_test.py", "qa_email_confirmation_intent_smoke_test.py",
            "quality_metrics_test.py", "dev_fixture_preflight_test.py",
            "seed_operational_service_day_fixture_test.py", "bootstrap_fixture_contract_test.py",
            "vanrides_dev_ops_test.py",
        ]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            scripts = root / "scripts"
            scripts.mkdir()
            fake_bin = root / "bin"
            fake_bin.mkdir()
            gate = scripts / CI_SCRIPT.name
            shutil.copy2(CI_SCRIPT, gate)
            stub = ('#!/usr/bin/env bash\n'
                    'event="$(basename "$0")"\n'
                    '[[ "$event" != python3 ]] || event="$(basename "$1")"\n'
                    'printf "%s\n" "$event" >> "$TRACE"\n'
                    '[[ "$event" != "$FAIL_AT" ]] || exit 7\n')
            for target in (fake_bin / "python3", scripts / "quality_gate.sh",
                           scripts / "qa_integration_profile.sh"):
                target.write_text(stub)
                target.chmod(0o755)
            trace = root / "trace"
            cases = [
                (["--quick"], "", 0, quick_checks),
                ([], "", 0, quick_checks + ["qa_integration_profile.sh"]),
                ([], "quality_gate.sh", 7, quick_checks[:1]),
                ([], "qa_report_evidence_test.py", 7, quick_checks[:3]),
                ([], "qa_integration_profile.sh", 7, quick_checks + ["qa_integration_profile.sh"]),
                (["--unknown"], "", 2, []),
                (["--quick", "extra"], "", 2, []),
            ]
            for args, fail_at, expected_exit, events in cases:
                with self.subTest(args=args, fail_at=fail_at):
                    trace.unlink(missing_ok=True)
                    env = {**os.environ, "PATH": str(fake_bin) + os.pathsep + os.environ["PATH"],
                           "TRACE": str(trace), "FAIL_AT": fail_at}
                    result = subprocess.run([str(gate), *args], env=env,
                                            capture_output=True, text=True)
                    self.assertEqual(expected_exit, result.returncode, result.stderr)
                    self.assertEqual(events, trace.read_text().splitlines() if trace.exists() else [])
                    if args or expected_exit:
                        self.assertNotIn("Gate backend CI finalizado com sucesso", result.stdout)
                    else:
                        self.assertIn("Gate backend CI finalizado com sucesso", result.stdout)

    def test_ci_summary_does_not_turn_failed_or_skipped_dependencies_green(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        result_job = workflow.split("\n  result:\n", 1)[1]
        self.assertIn("needs: [quick, test, integration]", result_job)
        self.assertIn("if: ${{ always() }}", result_job)
        command = textwrap.dedent(result_job.split("run: |\n", 1)[1])
        with tempfile.TemporaryDirectory() as temporary:
            summary = Path(temporary) / "summary.md"
            for quick, tests, integration in (("success", "success", "success"),
                                              ("failure", "skipped", "skipped"),
                                              ("success", "failure", "success"),
                                              ("success", "success", "cancelled")):
                with self.subTest(quick=quick, tests=tests, integration=integration):
                    summary.unlink(missing_ok=True)
                    env = {**os.environ, "GITHUB_STEP_SUMMARY": str(summary),
                           "SOURCE_SHA": "a" * 40, "QUICK": quick,
                           "TESTS": tests, "INTEGRATION": integration}
                    result = subprocess.run(["bash", "-e", "-c", command], env=env,
                                            capture_output=True, text=True)
                    self.assertEqual(0 if quick == tests == integration == "success" else 1,
                                     result.returncode, result.stderr)
                    self.assertIn("a" * 40, summary.read_text())
                    self.assertIn(f"| PostgreSQL | {tests} |", summary.read_text())

    def test_card_gate_composes_canonical_gate_before_http_smoke(self) -> None:
        script = CARD_GATE_SCRIPT.read_text(encoding="utf-8")

        canonical_call = '"$ROOT_DIR/scripts/ci_quality_gate.sh"'
        self.assertEqual(1, script.splitlines().count(canonical_call))
        self.assertNotIn("./mvnw -B test", script)
        self.assertNotIn("qa_integration_profile.sh", script)
        self.assertLess(script.index(canonical_call), script.index("Smoke HTTP básico"))

    def test_ci_gate_is_executable(self) -> None:
        mode = CI_SCRIPT.stat().st_mode

        self.assertTrue(mode & stat.S_IXUSR, "scripts/ci_quality_gate.sh is not executable")

    def test_location_read_smoke_runner_and_contract_test_are_versioned_surfaces(self) -> None:
        runner = LOCATION_READ_SMOKE.read_text(encoding="utf-8")
        tests = LOCATION_READ_SMOKE_TEST.read_text(encoding="utf-8")

        self.assertTrue(LOCATION_READ_SMOKE.stat().st_mode & stat.S_IXUSR)
        self.assertIn("VANRIDES_EXPECTED_BACKEND_SHA", runner)
        self.assertIn("VANRIDES_APP_CONSUMER_PROBE", runner)
        self.assertIn("postgres_image_repo_digest", runner)
        self.assertIn("test_runner_is_repo_relative", tests)
        self.assertIn("test_cleanup_is_fail_closed", tests)

    def test_rate_limit_smoke_and_load_runners_are_versioned_surfaces(self) -> None:
        smoke = RATE_LIMIT_SMOKE.read_text(encoding="utf-8")
        smoke_test = RATE_LIMIT_SMOKE_TEST.read_text(encoding="utf-8")
        load = RATE_LIMIT_LOAD.read_text(encoding="utf-8")
        load_test = RATE_LIMIT_LOAD_TEST.read_text(encoding="utf-8")

        for path in (RATE_LIMIT_SMOKE, RATE_LIMIT_LOAD):
            self.assertTrue(path.stat().st_mode & stat.S_IXUSR)
        self.assertIn("VANRIDES_EXPECTED_BACKEND_SHA", smoke)
        self.assertIn("trusted_proxy_identity", smoke)
        self.assertIn("test_runner_is_repo_relative", smoke_test)
        self.assertIn("HTTP loopback", load)
        self.assertIn("test_runner_rejects_non_loopback", load_test)

    def test_email_confirmation_intent_smoke_is_a_versioned_static_surface(self) -> None:
        smoke = EMAIL_CONFIRMATION_INTENT_SMOKE.read_text(encoding="utf-8")
        smoke_test = EMAIL_CONFIRMATION_INTENT_SMOKE_TEST.read_text(encoding="utf-8")

        self.assertTrue(EMAIL_CONFIRMATION_INTENT_SMOKE.stat().st_mode & stat.S_IXUSR)
        self.assertIn("VANRIDES_EXPECTED_BACKEND_SHA", smoke)
        self.assertIn("VANRIDES_EXPECTED_BACKEND_TREE", smoke)
        self.assertIn("VANRIDES_PRESERVE_RUNTIME_JAR", smoke)
        self.assertIn("test_runner_is_executable_bash_with_valid_syntax", smoke_test)
        self.assertIn(
            "test_renderer_static_contract_fails_closed_on_redirect_or_unexpected_2xx",
            smoke_test,
        )

    def test_integration_profile_is_executable_and_docker_required(self) -> None:
        script = INTEGRATION_PROFILE_SCRIPT.read_text(encoding="utf-8")
        mode = INTEGRATION_PROFILE_SCRIPT.stat().st_mode

        self.assertTrue(
            mode & stat.S_IXUSR,
            "scripts/qa_integration_profile.sh is not executable",
        )
        self.assertIn("docker info", script)
        self.assertIn("DOCKER_HOST \\", script)
        self.assertIn("DOCKER_CONTEXT \\", script)
        self.assertIn("DOCKER_API_VERSION \\", script)
        self.assertIn("TESTCONTAINERS_*", script)
        self.assertIn("MAVEN_USER_HOME", script)
        self.assertIn("export MAVEN_SKIP_RC=1", script)
        self.assertIn("docker context inspect", script)
        self.assertIn("docker version --format '{{.Server.MinAPIVersion}}'", script)
        self.assertIn("docker version --format '{{.Server.APIVersion}}'", script)
        self.assertIn("-Dapi.version=\"$TESTCONTAINERS_API_VERSION\"", script)
        self.assertIn('-Duser.home="$MAVEN_ISOLATED_HOME"', script)
        self.assertIn('-Dmaven.repo.local="$MAVEN_REPOSITORY"', script)
        self.assertIn('mavenHomeIsolated=true', script)
        self.assertIn('mavenRcSkipped=true', script)
        self.assertIn('--expected-user-home "$MAVEN_ISOLATED_HOME"', script)
        self.assertIn("--all-tests", script)
        self.assertIn("VanridesApplicationContextIntegrationTest", script)
        self.assertIn("RepositoryIntegrationTest", script)
        self.assertIn("CompanyAdminBootstrapConcurrencyIntegrationTest", script)
        self.assertIn("RefreshTokenRepositoryIntegrationTest", script)
        self.assertIn("LegacyCompanyMigrationIntegrationTest", script)
        self.assertIn("LoginIdentityMigrationIntegrationTest", script)
        self.assertIn("LoginIdentityTriggerIntegrationTest", script)
        self.assertIn("LoginIdentityConcurrencyIntegrationTest", script)
        self.assertIn("LoginIdentityDeviceTokenConcurrencyIntegrationTest", script)
        self.assertIn("EmailVerificationConcurrencyIntegrationTest", script)
        self.assertIn("EmailVerificationConfirmationConcurrencyIntegrationTest", script)
        self.assertIn("PassengerTenantDeleteConcurrencyIntegrationTest", script)
        self.assertIn('for expectation in "${EXPECTED_TEST_CLASSES[@]}"', script)
        self.assertIn('./mvnw -B "${maven_args[@]}"', script)
        self.assertIn(
            '"com.vanrides.vanrides.VanridesApplicationContextIntegrationTest=3"',
            script,
        )
        self.assertIn('"com.vanrides.vanrides.repository.RepositoryIntegrationTest=9"', script)
        self.assertIn(
            '"com.vanrides.vanrides.repository.DeviceTokenRepositoryIntegrationTest=1"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.repository.CompanyAdminBootstrapConcurrencyIntegrationTest=1"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.repository.RefreshTokenRepositoryIntegrationTest=1"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.repository.LegacyCompanyMigrationIntegrationTest=2"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.repository.LoginIdentityMigrationIntegrationTest=3"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.repository.LoginIdentityMigrationPreflightIntegrationTest=4"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.repository.LoginIdentityTriggerIntegrationTest=4"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.repository.LoginIdentityConcurrencyIntegrationTest=4"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.repository.LoginIdentityDeviceTokenConcurrencyIntegrationTest=4"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.repository.DeviceTokenRegistrationConcurrencyIntegrationTest=2"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.repository.EmailVerificationConcurrencyIntegrationTest=5"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.repository.EmailVerificationConfirmationConcurrencyIntegrationTest=3"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.service.PassengerTenantDeleteConcurrencyIntegrationTest=3"',
            script,
        )
        self.assertIn('-Dvanrides.qa.run-id="$RUN_ID"', script)
        self.assertIn("qa_report_evidence.py", script)
        self.assertIn('output/qa-evidence', script)
        self.assertIn(
            'source "$ROOT_DIR/scripts/qa_maven_evidence_lock.sh"', script
        )
        self.assertIn(
            'qa_maven_evidence_lock_acquire integration "$RUN_ID" "$RUN_GIT_SHA"',
            script,
        )
        self.assertIn('--expected-git-sha "$RUN_GIT_SHA"', script)
        self.assertIn("RUN_GIT_TREE=\"$(canonical_git rev-parse 'HEAD^{tree}')\"", script)
        self.assertIn('--expected-git-tree "$RUN_GIT_TREE"', script)
        self.assertIn(
            'REQUIRE_CLEAN="${VANRIDES_QA_REQUIRE_CLEAN:-0}"', script
        )
        self.assertIn('[[ "$REQUIRE_CLEAN" == "1" ]]', script)
        self.assertIn("canonical_git status --porcelain --untracked-files=all", script)
        self.assertIn("reject_git_environment_overrides", script)
        self.assertIn("canonical_git rev-parse --show-toplevel", script)
        self.assertIn("env -i", script)
        self.assertIn("common_evidence_args+=(--require-clean)", script)
        self.assertIn("prepare_evidence_args+=(--require-all-reports-green)", script)
        self.assertIn("trap cleanup_runtime EXIT", script)
        self.assertIn("forward_signal_to_maven TERM 143", script)
        self.assertIn("run_maven_and_wait ./mvnw", script)
        self.assertIn('-Dvanrides.qa.git-sha="$RUN_GIT_SHA"', script)
        self.assertLess(
            script.index("canonical_git status --porcelain --untracked-files=all"),
            script.index("./mvnw -B"),
        )
        self.assertLess(
            script.index("./mvnw -B"),
            script.index("python3 scripts/qa_report_evidence.py"),
        )
        prepare_index = script.index("--phase prepare")
        explicit_release_index = script.index(
            "qa_maven_evidence_lock_release", prepare_index
        )
        finalize_index = script.index("--phase finalize", explicit_release_index)
        archived_index = script.index("EVIDENCE_ARCHIVED=1", finalize_index)
        self.assertLess(prepare_index, explicit_release_index)
        self.assertLess(explicit_release_index, finalize_index)
        self.assertLess(finalize_index, archived_index)
        cleanup_body = script.split("cleanup_runtime() {", maxsplit=1)[1].split(
            "forward_signal_to_maven() {", maxsplit=1
        )[0]
        self.assertLess(
            cleanup_body.index("qa_maven_evidence_lock_release"),
            cleanup_body.index('rm -rf -- "$MAVEN_ISOLATED_HOME"'),
        )
        self.assertIn("if (( LOCK_ACQUIRED == 0 )); then", cleanup_body)

    def test_integration_matrix_matches_every_docker_optional_test_class(self) -> None:
        profile = INTEGRATION_PROFILE_SCRIPT.read_text(encoding="utf-8")
        expected = {
            class_name: int(count)
            for class_name, count in re.findall(
                r'"(com\.vanrides\.vanrides\.[A-Za-z0-9_.]+)=(\d+)"',
                profile,
            )
        }
        discovered = _discover_docker_optional_test_classes(ROOT / "src/test/java")

        self.assertEqual(discovered, set(expected))
        self.assertEqual(584, sum(expected.values()))

    def test_docker_optional_annotation_scanner_accepts_equivalent_formatting(self) -> None:
        variants = (
            "@Testcontainers(disabledWithoutDocker=true)",
            "@Testcontainers(\n    disabledWithoutDocker = true\n)",
            "@Testcontainers(parallel = false, disabledWithoutDocker = true)",
            "@org.testcontainers.junit.jupiter.Testcontainers("
            "disabledWithoutDocker = true)",
            "@Testcontainers(/* required */ disabledWithoutDocker /* = */ = true)",
            "@Testcontainers(disabledWithoutDocker = !false)",
            "@Testcontainers(disabledWithoutDocker = DOCKER_OPTIONAL)",
        )

        for variant in variants:
            with self.subTest(variant=variant):
                self.assertTrue(_declares_docker_optional_testcontainers(variant))
        self.assertFalse(_declares_docker_optional_testcontainers("@Testcontainers"))
        self.assertFalse(
            _declares_docker_optional_testcontainers(
                "@Testcontainers(disabledWithoutDocker = false)"
            )
        )
        self.assertFalse(
            _declares_docker_optional_testcontainers(
                "// @Testcontainers(disabledWithoutDocker = true)\nclass Example {}"
            )
        )
        self.assertFalse(
            _declares_docker_optional_testcontainers(
                "@Testcontainers(disabledWithoutDocker = /* explicit */ false)"
            )
        )

    def test_docker_optional_matrix_discovery_follows_inherited_annotation(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source_root = Path(temp_dir)
            package = source_root / "example"
            package.mkdir()
            (package / "DockerParent.java").write_text(
                "package example;\n"
                "@org.testcontainers.junit.jupiter.Testcontainers(\n"
                "    disabledWithoutDocker = !false\n"
                ")\n"
                "abstract class DockerParent {}\n",
                encoding="utf-8",
            )
            (package / "InheritedIntegrationTest.java").write_text(
                "package example;\n"
                "class InheritedIntegrationTest extends DockerParent {}\n",
                encoding="utf-8",
            )

            self.assertEqual(
                {"example.InheritedIntegrationTest"},
                _discover_docker_optional_test_classes(source_root),
            )

    def test_docker_optional_matrix_discovery_fails_closed_for_unknown_type(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir) / "UnknownIntegrationTest.java"
            source.write_text(
                "package example;\n"
                "@Testcontainers(disabledWithoutDocker = true)\n"
                "record UnknownIntegrationTest(String value) {}\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(AssertionError, "unsupported top-level type"):
                _discover_docker_optional_test_classes(Path(temp_dir))

    def test_docker_optional_matrix_discovery_fails_closed_for_unknown_parent(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir) / "InheritedIntegrationTest.java"
            source.write_text(
                "package example;\n"
                "class InheritedIntegrationTest extends external.DockerParent {}\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(AssertionError, "cannot be proven"):
                _discover_docker_optional_test_classes(Path(temp_dir))

    def test_all_tests_mode_omits_focused_selector_and_records_scope(self) -> None:
        result, maven_args, validator_args, _ = self._run_profile_with_fake_runtime(
            "--all-tests"
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(any(argument.startswith("-Dtest=") for argument in maven_args))
        self.assertIn("-ntp", maven_args)
        self.assertIn("clean", maven_args)
        self.assertIn("test", maven_args)
        isolated_home_args = [
            argument for argument in maven_args if argument.startswith("-Duser.home=")
        ]
        self.assertEqual(1, len(isolated_home_args))
        isolated_home = Path(isolated_home_args[0].partition("=")[2])
        self.assertNotIn("original-home", str(isolated_home))
        self.assertFalse(isolated_home.exists())
        self.assertTrue(
            any(argument.startswith("-Dmaven.repo.local=") for argument in maven_args)
        )
        self.assertIn("scope=all-tests", validator_args)
        self.assertIn("--require-all-reports-green", validator_args)
        self.assertIn("mavenHomeIsolated=true", validator_args)
        self.assertIn("mavenRcSkipped=true", validator_args)
        expected_home_index = validator_args.index("--expected-user-home")
        self.assertEqual(str(isolated_home), validator_args[expected_home_index + 1])
        invocations = self._validator_invocations(validator_args)
        self.assertEqual(2, len(invocations))
        self.assertEqual("prepare", invocations[0][invocations[0].index("--phase") + 1])
        self.assertEqual("finalize", invocations[1][invocations[1].index("--phase") + 1])
        self.assertNotIn("--reports-dir", invocations[1])
        self.assertNotIn("--command", invocations[1])

    def test_focused_mode_keeps_exact_selector_and_records_scope(self) -> None:
        result, maven_args, validator_args, _ = self._run_profile_with_fake_runtime()

        self.assertEqual(0, result.returncode, result.stderr)
        selectors = [argument for argument in maven_args if argument.startswith("-Dtest=")]
        self.assertEqual(1, len(selectors))
        self.assertIn("RepositoryIntegrationTest", selectors[0])
        self.assertIn("LegacyCompanyMigrationIntegrationTest", selectors[0])
        self.assertIn("LoginIdentityMigrationIntegrationTest", selectors[0])
        self.assertIn("LoginIdentityTriggerIntegrationTest", selectors[0])
        self.assertIn("LoginIdentityConcurrencyIntegrationTest", selectors[0])
        self.assertIn("LoginIdentityDeviceTokenConcurrencyIntegrationTest", selectors[0])
        self.assertIn("EmailVerificationConcurrencyIntegrationTest", selectors[0])
        self.assertIn(
            "EmailVerificationConfirmationConcurrencyIntegrationTest",
            selectors[0],
        )
        self.assertIn("scope=focused", validator_args)
        self.assertNotIn("--require-all-reports-green", validator_args)
        invocations = self._validator_invocations(validator_args)
        self.assertEqual(2, len(invocations))
        self.assertEqual("prepare", invocations[0][invocations[0].index("--phase") + 1])
        self.assertEqual("finalize", invocations[1][invocations[1].index("--phase") + 1])

    def test_integration_profile_rejects_unknown_argument_before_maven(self) -> None:
        result, maven_args, _, _ = self._run_profile_with_fake_runtime("--unknown")

        self.assertEqual(1, result.returncode)
        self.assertIn("Argumento desconhecido", result.stderr)
        self.assertEqual([], maven_args)

    def test_integration_profile_rejects_repeated_mode_before_maven(self) -> None:
        result, maven_args, _, _ = self._run_profile_with_fake_runtime(
            "--all-tests", "--all-tests"
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("no maximo uma vez", result.stderr)
        self.assertEqual([], maven_args)

    def test_integration_profile_fails_if_docker_api_bounds_are_unavailable(self) -> None:
        cases = (
            ({"docker_min_api": ""}, "Docker Server.MinAPIVersion"),
            ({"docker_max_api": ""}, "Docker Server.APIVersion"),
        )
        for runtime, diagnostic in cases:
            with self.subTest(diagnostic=diagnostic):
                result, maven_args, _, _ = self._run_profile_with_fake_runtime(
                    **runtime
                )

                self.assertEqual(1, result.returncode)
                self.assertIn(f"Nao foi possivel obter {diagnostic}", result.stderr)
                self.assertEqual([], maven_args)

    def test_integration_profile_rejects_malformed_api_override_before_maven(self) -> None:
        for override in ("latest", "0.1000044", "1.000044", "01.44", "1.044"):
            with self.subTest(override=override):
                result, maven_args, _, _ = self._run_profile_with_fake_runtime(
                    environment_overrides={"TESTCONTAINERS_API_VERSION": override}
                )

                self.assertEqual(1, result.returncode)
                self.assertIn("TESTCONTAINERS_API_VERSION invalida", result.stderr)
                self.assertEqual([], maven_args)

    def test_integration_profile_rejects_api_override_outside_docker_range(self) -> None:
        for override in ("1.32", "1.54"):
            with self.subTest(override=override):
                result, maven_args, _, _ = self._run_profile_with_fake_runtime(
                    environment_overrides={"TESTCONTAINERS_API_VERSION": override}
                )

                self.assertEqual(1, result.returncode)
                self.assertIn("fora da faixa Docker [1.44, 1.53]", result.stderr)
                self.assertEqual([], maven_args)

    def test_integration_profile_accepts_canonical_api_override_inside_range(self) -> None:
        result, maven_args, validator_args, _ = self._run_profile_with_fake_runtime(
            environment_overrides={"TESTCONTAINERS_API_VERSION": "1.50"}
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("-Dapi.version=1.50", maven_args)
        self.assertIn("testcontainersApiVersion=1.50", validator_args)

    def test_integration_profile_rejects_inverted_docker_api_range(self) -> None:
        result, maven_args, _, _ = self._run_profile_with_fake_runtime(
            docker_min_api="1.53",
            docker_max_api="1.44",
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("Faixa Docker invalida", result.stderr)
        self.assertEqual([], maven_args)

    def test_integration_profile_rejects_docker_transport_overrides_before_daemon(self) -> None:
        cases = {
            "DOCKER_HOST": "tcp://prod.example:2376",
            "DOCKER_CONTEXT": "production",
        }
        for variable, value in cases.items():
            with self.subTest(variable=variable):
                result, maven_args, _, docker_calls = self._run_profile_with_fake_runtime(
                    environment_overrides={variable: value}
                )

                self.assertEqual(1, result.returncode)
                self.assertIn(f"{variable} override nao permitido", result.stderr)
                self.assertEqual([], maven_args)
                self.assertFalse(any(call.startswith("info") for call in docker_calls))
                self.assertFalse(any(call.startswith("version") for call in docker_calls))

    def test_integration_profile_rejects_hidden_runtime_overrides_before_daemon(self) -> None:
        cases = {
            "DOCKER_API_VERSION": "1.32",
            "DOCKER_TLS_VERIFY": "1",
            "DOCKER_CERT_PATH": "/tmp/remote-cert",
            "TESTCONTAINERS_HOST_OVERRIDE": "prod.example",
            "JAVA_TOOL_OPTIONS": "-Ddocker.host=tcp://prod.example:2376",
            "_JAVA_OPTIONS": "-Duser.home=/tmp/unsafe",
            "JDK_JAVA_OPTIONS": "-Ddocker.host=tcp://prod.example:2376",
            "MAVEN_OPTS": "-Ddocker.host=tcp://prod.example:2376",
            "MAVEN_ARGS": "-Ddocker.host=tcp://prod.example:2376",
            "MAVEN_USER_HOME": "/tmp/unsafe-maven-home",
            "MAVEN_SKIP_RC": "0",
            "MAVEN_DEBUG_OPTS": "-Ddocker.host=tcp://prod.example:2376",
            "MAVEN_BASEDIR": "/tmp/unsafe-project",
            "MVNW_REPOURL": "https://repo.example.invalid",
            "TESTCONTAINERS_RYUK_DISABLED": "true",
            "GIT_DIR": "/tmp/alternate.git",
            "GIT_WORK_TREE": "/tmp/alternate-worktree",
            "GIT_INDEX_FILE": "/tmp/alternate-index",
            "GIT_OBJECT_DIRECTORY": "/tmp/alternate-objects",
            "GIT_ALTERNATE_OBJECT_DIRECTORIES": "/tmp/alternate-objects",
            "GIT_CONFIG_GLOBAL": "/tmp/alternate-gitconfig",
            "GIT_CONFIG_COUNT": "1",
        }
        for variable, value in cases.items():
            with self.subTest(variable=variable):
                result, maven_args, _, docker_calls = self._run_profile_with_fake_runtime(
                    environment_overrides={variable: value}
                )

                self.assertEqual(1, result.returncode)
                self.assertIn(variable, result.stderr)
                self.assertEqual([], maven_args)
                self.assertFalse(any(call.startswith("info") for call in docker_calls))
                self.assertFalse(any(call.startswith("version") for call in docker_calls))

    def test_integration_profile_uses_builtin_compgen_against_function_shadow(self) -> None:
        result, maven_args, _, docker_calls = self._run_profile_with_fake_runtime(
            environment_overrides={
                "MAVEN_DEBUG_OPTS": "-Ddocker.host=tcp://prod.example:2376",
            },
            shadow_compgen=True,
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("MAVEN_DEBUG_OPTS", result.stderr)
        self.assertEqual([], maven_args)
        self.assertEqual([], docker_calls)

    def test_integration_profile_rejects_remote_context_endpoint_before_daemon(self) -> None:
        result, maven_args, _, docker_calls = self._run_profile_with_fake_runtime(
            environment_overrides={
                "FAKE_DOCKER_ENDPOINT": "tcp://prod.example:2376",
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("Docker endpoint remoto nao permitido", result.stderr)
        self.assertNotIn("tcp://prod.example:2376", result.stderr)
        self.assertEqual([], maven_args)
        self.assertIn("context show", docker_calls)
        self.assertTrue(any(call.startswith("context inspect") for call in docker_calls))
        self.assertFalse(any(call.startswith("info") for call in docker_calls))
        self.assertFalse(any(call.startswith("version") for call in docker_calls))

    def test_integration_profile_preserves_signal_and_releases_shared_lock(self) -> None:
        for signal_name, exit_code in (("INT", 130), ("TERM", 143)):
            with self.subTest(signal_name=signal_name):
                lock_observations: list[
                    tuple[tuple[str, ...], tuple[str, ...], bool]
                ] = []
                result, _, validator_args, _ = self._run_profile_with_fake_runtime(
                    environment_overrides={
                        "FAKE_MAVEN_HOLD_AFTER_SIGNAL": signal_name,
                    },
                    lock_observations=lock_observations,
                )

                self.assertEqual(exit_code, result.returncode)
                self.assertIn("MAVEN_RECEIVED_TERM", result.stderr)
                self.assertEqual(
                    "signal",
                    validator_args[validator_args.index("--failure-stage") + 1],
                )
                self.assertEqual(
                    str(exit_code),
                    validator_args[validator_args.index("--exit-code") + 1],
                )
                self.assertEqual(
                    "integration",
                    validator_args[validator_args.index("--suite") + 1],
                )
                for success_only_argument in (
                    "--reports-dir",
                    "--command",
                    "--expected",
                    "--metadata",
                    "--expected-user-home",
                    "--require-all-reports-green",
                ):
                    self.assertNotIn(success_only_argument, validator_args)
                self.assertEqual(1, len(lock_observations))
                entries, transient_entries, lock_is_reacquirable = (
                    lock_observations[0]
                )
                self.assertEqual(("maven-evidence.lock", "owner"), entries)
                self.assertEqual((), transient_entries)
                self.assertTrue(lock_is_reacquirable)

    def test_rbac_suite_terminates_on_signal_and_cleans_shared_lock(self) -> None:
        for signal_name, exit_code in (("INT", 130), ("TERM", 143)):
            with self.subTest(signal_name=signal_name):
                (
                    result,
                    validator_args,
                    entries,
                    transient_entries,
                    lock_is_reacquirable,
                ) = self._run_rbac_with_maven_signal(signal_name)

                self.assertEqual(exit_code, result.returncode)
                self.assertIn("MAVEN_RECEIVED_TERM", result.stderr)
                self.assertEqual(
                    "signal",
                    validator_args[validator_args.index("--failure-stage") + 1],
                )
                self.assertEqual(
                    str(exit_code),
                    validator_args[validator_args.index("--exit-code") + 1],
                )
                self.assertEqual(
                    "rbac",
                    validator_args[validator_args.index("--suite") + 1],
                )
                for success_only_argument in (
                    "--reports-dir",
                    "--command",
                    "--expected",
                    "--metadata",
                    "--expected-user-home",
                    "--require-all-reports-green",
                ):
                    self.assertNotIn(success_only_argument, validator_args)
                self.assertEqual(("maven-evidence.lock", "owner"), entries)
                self.assertEqual((), transient_entries)
                self.assertTrue(lock_is_reacquirable)

    def test_integration_profile_propagates_validator_skip_failure(self) -> None:
        result, _, _, _ = self._run_profile_with_fake_runtime(
            "--all-tests", validator_exit=1
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("suite is not green: skipped=1", result.stderr)
        self.assertNotIn("Backend integration profile finalizado com sucesso", result.stdout)

    def test_integration_profile_never_finalizes_when_lock_release_fails(self) -> None:
        lock_observations: list[
            tuple[tuple[str, ...], tuple[str, ...], bool]
        ] = []
        result, _, validator_args, _ = self._run_profile_with_fake_runtime(
            lock_release_exit=73,
            lock_observations=lock_observations,
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("Nao foi possivel liberar o lock Maven", result.stderr)
        self.assertIn("user.home isolado preservado", result.stderr)
        self.assertNotIn("Backend integration profile finalizado com sucesso", result.stdout)
        invocations = self._validator_invocations(validator_args)
        self.assertEqual(2, len(invocations))
        self.assertEqual("prepare", invocations[0][invocations[0].index("--phase") + 1])
        self.assertNotIn("--phase", invocations[1])
        self.assertEqual(
            "validation",
            invocations[1][invocations[1].index("--failure-stage") + 1],
        )
        self.assertEqual("1", invocations[1][invocations[1].index("--exit-code") + 1])
        self.assertEqual(1, len(lock_observations))
        _, transient_entries, lock_is_reacquirable = lock_observations[0]
        self.assertEqual((), transient_entries)
        self.assertTrue(lock_is_reacquirable)

    def test_integration_profile_converts_canonical_prepared_after_finalize_failure(
        self,
    ) -> None:
        result, _, validator_args, _ = self._run_profile_with_fake_runtime(
            environment_overrides={"FAKE_FINALIZE_PREPARED_EXIT": "91"},
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("Finalizacao pos-lock da evidencia falhou", result.stderr)
        self.assertNotIn("Backend integration profile finalizado com sucesso", result.stdout)
        invocations = self._validator_invocations(validator_args)
        self.assertEqual(3, len(invocations))
        self.assertEqual("prepare", invocations[0][invocations[0].index("--phase") + 1])
        self.assertEqual("finalize", invocations[1][invocations[1].index("--phase") + 1])
        self.assertNotIn("--phase", invocations[2])
        self.assertEqual(
            "validation",
            invocations[2][invocations[2].index("--failure-stage") + 1],
        )
        self.assertEqual("1", invocations[2][invocations[2].index("--exit-code") + 1])

    def test_rbac_suite_uses_exact_fresh_provenance_checked_matrix(self) -> None:
        script = RBAC_SCRIPT.read_text(encoding="utf-8")

        self.assertIn('-Dvanrides.qa.run-id="$RUN_ID"', script)
        self.assertIn("qa_report_evidence.py", script)
        self.assertIn('output/qa-evidence', script)
        self.assertIn(
            'source "$ROOT_DIR/scripts/qa_maven_evidence_lock.sh"', script
        )
        self.assertIn(
            'qa_maven_evidence_lock_acquire rbac "$RUN_ID" "$RUN_GIT_SHA"',
            script,
        )
        self.assertIn("clean test", script)
        self.assertIn('--expected-git-sha "$RUN_GIT_SHA"', script)
        self.assertIn("RUN_GIT_TREE=\"$(canonical_git rev-parse 'HEAD^{tree}')\"", script)
        self.assertIn('--expected-git-tree "$RUN_GIT_TREE"', script)
        self.assertIn(
            'REQUIRE_CLEAN="${VANRIDES_QA_REQUIRE_CLEAN:-0}"', script
        )
        self.assertIn('[[ "$REQUIRE_CLEAN" == "1" ]]', script)
        self.assertIn("canonical_git status --porcelain --untracked-files=all", script)
        self.assertIn("reject_git_environment_overrides", script)
        self.assertIn("canonical_git rev-parse --show-toplevel", script)
        self.assertIn("env -i", script)
        self.assertIn("common_evidence_args+=(--require-clean)", script)
        self.assertLess(
            script.index("canonical_git status --porcelain --untracked-files=all"),
            script.index("./mvnw -B"),
        )
        self.assertLess(
            script.index("./mvnw -B"),
            script.index("python3 scripts/qa_report_evidence.py"),
        )
        prepare_index = script.index("--phase prepare")
        explicit_release_index = script.index(
            "qa_maven_evidence_lock_release", prepare_index
        )
        finalize_index = script.index("--phase finalize", explicit_release_index)
        archived_index = script.index("EVIDENCE_ARCHIVED=1", finalize_index)
        self.assertLess(prepare_index, explicit_release_index)
        self.assertLess(explicit_release_index, finalize_index)
        self.assertLess(finalize_index, archived_index)
        self.assertIn('"com.vanrides.vanrides.controller.RbacWebMvcTest=10"', script)
        self.assertIn('"com.vanrides.vanrides.controller.TenantIsolationWebMvcTest=8"', script)
        self.assertIn(
            '"com.vanrides.vanrides.controller.LocalizacaoMotoristaControllerWebMvcTest=17"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.service.OperationalDateProviderTest=4"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.service.GuardianAccessServiceTest=4"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.service.PassageiroServiceTest=6"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.service.PassageiroServiceCredentialPolicyTest=9"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.service.PassageiroServiceIdentityLifecycleTest=6"',
            script,
        )
        self.assertIn(
            '"com.vanrides.vanrides.controller.PassageiroControllerWebMvcTest=8"',
            script,
        )
        self.assertIn('"com.vanrides.vanrides.service.MotoristaServiceTest=10"', script)
        self.assertIn(
            '"com.vanrides.vanrides.service.MotoristaPublicRegistrationServiceTest=4"',
            script,
        )
        self.assertIn('"com.vanrides.vanrides.controller.MotoristaControllerWebMvcTest=5"', script)
        self.assertIn('"com.vanrides.vanrides.service.CompanyServiceTest=4"', script)
        self.assertIn('"com.vanrides.vanrides.service.CompanyAdminServiceTest=9"', script)
        self.assertIn(
            '"com.vanrides.vanrides.service.CompanyAdminIdentityMaintenanceServiceTest=2"',
            script,
        )
        self.assertIn('"com.vanrides.vanrides.controller.CompanyAdminControllerWebMvcTest=6"', script)
        self.assertIn(
            '"com.vanrides.vanrides.controller.ViagemOperationalDateWebMvcTest=2"',
            script,
        )
        self.assertIn("trap cleanup_runtime EXIT", script)
        self.assertIn("forward_signal_to_maven TERM 143", script)
        self.assertIn("run_maven_and_wait ./mvnw", script)

    def test_report_evidence_validator_owns_provenance_and_atomic_archive(self) -> None:
        validator = REPORT_EVIDENCE_SCRIPT.read_text(encoding="utf-8")
        tests = REPORT_EVIDENCE_TEST.read_text(encoding="utf-8")

        self.assertIn("critical_properties = {", validator)
        self.assertIn('"vanrides.qa.run-id": []', validator)
        self.assertIn('"vanrides.qa.git-sha": []', validator)
        self.assertIn('"user.home": []', validator)
        self.assertIn("if len(values) != 1:", validator)
        self.assertIn(
            'actual_run_id = critical_properties["vanrides.qa.run-id"][0]',
            validator,
        )
        self.assertIn(
            'actual_git_sha = critical_properties["vanrides.qa.git-sha"][0]',
            validator,
        )
        self.assertIn("report changed while being read", validator)
        self.assertIn("git tree changed during evidence run", validator)
        self.assertIn("archived_content=_sanitized_report_bytes", validator)
        self.assertIn(
            "os.replace(passed_candidate, evidence_dir / PREPARED_MANIFEST_NAME)",
            validator,
        )
        self.assertNotIn('parser.add_argument("--command"', validator)
        self.assertIn("test_capture_source_state_ignores_alternate_repository_git_variables", tests)
        self.assertIn("test_dirty_fingerprint_covers_staged_worktree_and_untracked_bytes", tests)
        self.assertIn("test_canonical_bundle_contains_no_surefire_properties_or_output_secrets", tests)
        self.assertIn("test_duplicate_critical_provenance_properties_fail_closed", tests)
        self.assertIn("test_prepare_is_nonterminal_private_and_contains_no_pass_payload", tests)
        self.assertIn("test_finalize_commits_passed_manifest_and_uses_only_prepared_report_bytes", tests)
        self.assertIn(
            "test_sigkill_equivalent_between_canonical_move_and_commit_exposes_no_pass",
            tests,
        )
        self.assertIn("test_rejects_report_from_another_run", tests)
        self.assertIn("test_rejects_stale_report", tests)
        self.assertIn("test_rejects_mixed_user_home_values", tests)
        self.assertIn("test_rejects_wrong_attribute_or_testcase_count", tests)
        self.assertIn("test_require_clean_rejects_dirty_source", tests)
        self.assertIn("test_require_clean_accepts_clean_source_and_records_requirement", tests)
        self.assertIn("test_rejects_report_built_for_another_git_sha", tests)
        self.assertIn("test_rejects_source_change_between_run_start_and_archive", tests)
        self.assertIn("test_rejects_tree_change_between_run_start_and_archive", tests)
        self.assertIn("test_records_only_isolation_boolean_after_matching_expected_home", tests)
        self.assertIn("test_freeform_or_reserved_request_metadata_is_rejected", tests)
        self.assertIn("test_cli_metadata_rejects_reserved_user_home_key", tests)
        self.assertIn("--require-clean", validator)
        self.assertIn("--require-all-reports-green", validator)
        self.assertIn("_validate_all_reports_green", validator)
        self.assertIn("test_all_report_scan_rejects_skipped_unexpected_class", tests)
        self.assertIn("test_all_report_scan_rejects_stale_unexpected_class", tests)

    def test_integration_profile_fails_when_docker_cli_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            isolated_path = Path(temp_dir)
            lock_root = isolated_path / "maven-evidence-lock"
            lock_root.mkdir(mode=0o700)
            lock_root.chmod(0o700)
            for command in ("bash", "date", "dirname", "env", "git", "python3"):
                resolved = shutil.which(command)
                self.assertIsNotNone(resolved, f"required test command not found: {command}")
                (isolated_path / command).symlink_to(resolved)

            environment = os.environ.copy()
            environment.update(
                {
                    "PATH": str(isolated_path),
                    "VANRIDES_QA_EVIDENCE_ROOT": str(isolated_path / "evidence"),
                    "VANRIDES_QA_LOCK_ROOT": str(lock_root),
                    "VANRIDES_QA_REQUIRE_CLEAN": "0",
                    "VANRIDES_QA_RETENTION_CLASS": "local",
                }
            )
            result = subprocess.run(
                [str(INTEGRATION_PROFILE_SCRIPT)],
                cwd=ROOT,
                env=environment,
                capture_output=True,
                text=True,
                check=False,
            )

        self.assertEqual(1, result.returncode)
        self.assertIn("Docker CLI nao encontrado", result.stderr)

    def test_integration_profile_fails_when_docker_daemon_is_unavailable(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            isolated_path = Path(temp_dir)
            lock_root = isolated_path / "maven-evidence-lock"
            lock_root.mkdir(mode=0o700)
            lock_root.chmod(0o700)
            for command in ("bash", "date", "dirname", "env", "git", "python3"):
                resolved = shutil.which(command)
                self.assertIsNotNone(resolved, f"required test command not found: {command}")
                (isolated_path / command).symlink_to(resolved)

            fake_docker = isolated_path / "docker"
            fake_docker.write_text(
                "#!/usr/bin/env bash\n"
                "if [[ \"${1:-} ${2:-}\" == 'context show' ]]; then\n"
                "  echo local-test\n"
                "  exit 0\n"
                "fi\n"
                "if [[ \"${1:-} ${2:-}\" == 'context inspect' ]]; then\n"
                "  echo unix:///tmp/docker.sock\n"
                "  exit 0\n"
                "fi\n"
                "exit 86\n",
                encoding="utf-8",
            )
            fake_docker.chmod(0o755)

            environment = os.environ.copy()
            environment.pop("DOCKER_HOST", None)
            environment.pop("DOCKER_CONTEXT", None)
            environment.update(
                {
                    "PATH": str(isolated_path),
                    "VANRIDES_QA_EVIDENCE_ROOT": str(isolated_path / "evidence"),
                    "VANRIDES_QA_LOCK_ROOT": str(lock_root),
                    "VANRIDES_QA_REQUIRE_CLEAN": "0",
                    "VANRIDES_QA_RETENTION_CLASS": "local",
                }
            )
            result = subprocess.run(
                [str(INTEGRATION_PROFILE_SCRIPT)],
                cwd=ROOT,
                env=environment,
                capture_output=True,
                text=True,
                check=False,
            )

        self.assertEqual(1, result.returncode)
        self.assertIn("Docker nao esta disponivel", result.stderr)

    def test_codex_cloud_docs_recommend_dedicated_ci_gate(self) -> None:
        docs = CODEX_CLOUD_DOC.read_text(encoding="utf-8")

        self.assertIn(
            "Default gate for normal backend cards:\n\n```bash\n./scripts/ci_quality_gate.sh\n```",
            docs,
        )
        self.assertIn("The CI entrypoint validates the workflow wiring", docs)
        self.assertIn("Do not make the CI/CD `test` job depend on the published DEV API", docs)
        self.assertIn("Project gate with HTTP smoke against DEV", docs)

    def test_codex_cloud_setup_recommends_dedicated_ci_gate(self) -> None:
        setup = CODEX_CLOUD_SETUP.read_text(encoding="utf-8")

        self.assertIn("Recommended agent check: ./scripts/ci_quality_gate.sh", setup)
        self.assertNotIn("Recommended agent check: ./mvnw -B test", setup)
        self.assertNotIn("Recommended agent check: ./scripts/qa_gate_card.sh", setup)
        self.assertNotIn("docker info", setup)
        self.assertNotIn("docker pull", setup)


if __name__ == "__main__":
    unittest.main()

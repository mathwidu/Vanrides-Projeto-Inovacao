#!/usr/bin/env python3
"""Testes stdlib puros: não iniciam Docker, n8n, rede ou outro runtime."""
import base64
from contextlib import redirect_stderr, redirect_stdout
import copy
import importlib.util
import io
import json
from pathlib import Path
import shutil
import sqlite3
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("wa070_smoke", Path(__file__).with_name("070-n8n-smoke.py"))
smoke = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(smoke)
SENTINEL = "SYNTHETIC_CONTROL_SENTINEL_NO_REAL_SECRET"


def output(flow="W01", failed=False, summary=None):
    node = "Estado sanitizado" if flow == "W07" else "Executar operação privada"
    result = {"lastNodeExecuted": node, "runData": {node: [{"data": {"main": [[{
        "json": copy.deepcopy(smoke.EXPECTED.get(flow, {})) if summary is None else summary}]]}}]}}
    if failed:
        result["error"] = {"message": "connect ECONNREFUSED"}
    return b"Some CLI log line\n" + json.dumps({"data": {"resultData": result}}, ensure_ascii=False).encode() + b"\nExtra CLI log line\n"


class PreflightTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.bundle = self.root / "workflows"
        shutil.copytree(smoke.BUNDLE, self.bundle)

    def manifest(self, change):
        path = self.bundle / "manifest.json"
        manifest = json.loads(path.read_bytes())
        change(manifest)
        path.write_text(json.dumps(manifest))

    def workflow(self, flow, change):
        path = self.bundle / (flow+".json")
        value = json.loads(path.read_bytes())
        change(value)
        raw = json.dumps(value).encode()
        path.write_bytes(raw)
        self.manifest(lambda m: next(r for r in m["workflows"] if r["logicalId"] == flow).update(sha256=smoke.digest(raw)))

    def test_current_bundle_has_eight_immutable_inactive_exports(self):
        manifest, hashes, workflows = smoke.load_bundle(self.bundle)
        self.assertEqual(len(workflows), 8)
        self.assertEqual(len(hashes), 9)
        self.assertTrue(all(not w["active"] for w in workflows.values()))
        self.assertEqual(manifest["n8nLocalImage"], smoke.IMAGE)
        self.assertEqual(workflows["W03"]["nodes"][1]["parameters"]["url"], smoke.CONTROL+"/v1/process-inbox")
        self.assertEqual(next(r for r in manifest["workflows"] if r["logicalId"] == "W03")["mode"], "IMPLEMENTED_CONTROL")

    def test_missing_or_malformed_preflight_does_not_call_docker_or_create_runtime(self):
        for content in (None, SENTINEL, json.dumps({"secret": SENTINEL}), "[]", "null"):
            with self.subTest(content=content):
                path = self.bundle / "manifest.json"
                if content is None:
                    path.unlink(missing_ok=True)
                else:
                    path.write_text(content)
                before = sorted(str(p.relative_to(self.root)) for p in self.root.rglob("*"))
                stdout, stderr = io.StringIO(), io.StringIO()
                with patch.object(smoke, "BUNDLE", self.bundle), patch.object(smoke.subprocess, "run", side_effect=AssertionError("Docker forbidden")), redirect_stdout(stdout), redirect_stderr(stderr):
                    status = smoke.main(["--runtime-parent", str(self.root/"never-created")])
                self.assertEqual(status, 1)
                self.assertEqual(json.loads(stdout.getvalue())["code"], "BUNDLE_INVALID")
                self.assertNotIn(SENTINEL, stdout.getvalue()+stderr.getvalue())
                self.assertEqual(stderr.getvalue(), "")
                self.assertEqual(sorted(str(p.relative_to(self.root)) for p in self.root.rglob("*")), before)

    def test_export_tampering_fails_before_runtime(self):
        path = self.bundle / "W01.json"
        path.write_bytes(path.read_bytes()+b"\n")
        with self.assertRaisesRegex(smoke.SmokeError, "^WORKFLOW_HASH$"):
            smoke.load_bundle(self.bundle)

    def test_manifest_cannot_select_different_image(self):
        self.manifest(lambda m: m.update(n8nLocalImage="n8nio/n8n:latest"))
        with self.assertRaisesRegex(smoke.SmokeError, "^MANIFEST_RUNTIME$"):
            smoke.load_bundle(self.bundle)

    def test_manifest_cannot_import_file_outside_bundle(self):
        self.manifest(lambda m: m["workflows"][0].update(file="../private.json"))
        with self.assertRaisesRegex(smoke.SmokeError, "^WORKFLOW_PATH$"):
            smoke.load_bundle(self.bundle)

    def test_manifest_cannot_silently_omit_workflow(self):
        self.manifest(lambda m: m["workflows"].pop())
        with self.assertRaisesRegex(smoke.SmokeError, "^MANIFEST_WORKFLOWS$"):
            smoke.load_bundle(self.bundle)

    def test_changed_hash_does_not_allow_domain_endpoint(self):
        self.workflow("W01", lambda w: w["nodes"][1]["parameters"].update(url="http://backend.invalid/commands"))
        with self.assertRaisesRegex(smoke.SmokeError, "^WORKFLOW_CONTROL$"):
            smoke.load_bundle(self.bundle)

    def test_changed_hash_does_not_allow_conversation_body(self):
        self.workflow("W06", lambda w: w["nodes"][1]["parameters"].update(jsonBody='{"text":"synthetic"}'))
        with self.assertRaisesRegex(smoke.SmokeError, "^WORKFLOW_CONTROL$"):
            smoke.load_bundle(self.bundle)

    def test_w08_reconciles_with_an_empty_private_post_and_rejects_health_or_cursor_input(self):
        _, _, workflows = smoke.load_bundle(self.bundle)
        parameters = workflows["W08"]["nodes"][1]["parameters"]
        self.assertEqual(parameters["url"], smoke.CONTROL+"/v1/reconcile-delivery")
        self.assertEqual(parameters["method"], "POST")
        self.assertEqual(parameters["jsonBody"], "{}")
        for change in ({"url": smoke.CONTROL+"/healthz", "method": "GET"},
                       {"jsonBody": '{"cursor":"synthetic"}'}, {"method": "GET"}):
            with self.subTest(change=change):
                shutil.rmtree(self.bundle)
                shutil.copytree(smoke.BUNDLE, self.bundle)
                self.workflow("W08", lambda w: w["nodes"][1]["parameters"].update(change))
                with self.assertRaisesRegex(smoke.SmokeError, "^WORKFLOW_CONTROL$"):
                    smoke.load_bundle(self.bundle)

    def test_w05_cannot_forward_inbox_or_choose_a_domain_endpoint(self):
        for parameters in ({"jsonBody": '{"inboxEventId":"synthetic"}'},
                           {"url": smoke.CONTROL+"/driver-app/v1/runs/synthetic/resume"}):
            with self.subTest(parameters=parameters):
                shutil.rmtree(self.bundle)
                shutil.copytree(smoke.BUNDLE, self.bundle)
                self.workflow("W05", lambda w: w["nodes"][1]["parameters"].update(parameters))
                with self.assertRaisesRegex(smoke.SmokeError, "^WORKFLOW_CONTROL$"):
                    smoke.load_bundle(self.bundle)

    def test_changed_hash_does_not_allow_active_workflow_or_retention(self):
        for field, value, error in (("active", True, "WORKFLOW_IDENTITY"), ("saveManualExecutions", True, "WORKFLOW_RETENTION")):
            with self.subTest(field=field):
                if field == "active":
                    self.workflow("W01", lambda w: w.update(active=value))
                else:
                    self.workflow("W01", lambda w: (w.update(active=False), w["settings"].update({field: value})))
                with self.assertRaisesRegex(smoke.SmokeError, "^"+error+"$"):
                    smoke.load_bundle(self.bundle)

    def test_safe_preflight_only_creates_no_runtime_and_calls_no_docker(self):
        stdout = io.StringIO()
        with patch.object(smoke, "BUNDLE", self.bundle), patch.object(smoke.subprocess, "run", side_effect=AssertionError("Docker forbidden")), redirect_stdout(stdout):
            self.assertEqual(smoke.main(["--preflight-only", "--runtime-parent", str(self.root/"never-created")]), 0)
        self.assertEqual(json.loads(stdout.getvalue())["scope"], "PREFLIGHT_ONLY_NO_DOCKER")
        self.assertFalse((self.root/"never-created").exists())

    def reject_repository_runtime(self, candidate, repository):
        stdout = io.StringIO()
        before = sorted(str(p.relative_to(repository)) for p in repository.rglob("*"))
        with patch.object(smoke, "BUNDLE", self.bundle), patch.object(smoke, "ROOT", repository), \
                patch.object(smoke.subprocess, "run", side_effect=AssertionError("Docker forbidden")), redirect_stdout(stdout):
            self.assertEqual(smoke.main(["--runtime-parent", str(candidate)]), 1)
        self.assertEqual(json.loads(stdout.getvalue())["code"], "RUNTIME_LOCATION")
        self.assertEqual(sorted(str(p.relative_to(repository)) for p in repository.rglob("*")), before)

    def test_runtime_cannot_enter_repository_through_dot_dot(self):
        repository = self.root / "repository"
        repository.mkdir()
        self.reject_repository_runtime(self.root / ".." / self.root.name / "repository" / "private-smoke", repository)

    def test_runtime_cannot_enter_repository_through_symlink_ancestor(self):
        repository = self.root / "repository"
        repository.mkdir()
        alias = self.root / "outside-alias"
        alias.symlink_to(repository, target_is_directory=True)
        self.reject_repository_runtime(alias / "private-smoke", repository)


class EvidenceTest(unittest.TestCase):
    def test_lost_creation_ack_reconciles_and_stops_owned_container(self):
        manifest, hashes, workflows = smoke.load_bundle()
        for failed_step in ("start-fake", "start-n8n"):
            with self.subTest(step=failed_step), tempfile.TemporaryDirectory() as tmp:
                created = {}
                receipt = {"steps": []}
                def call(_runner, step, args, *unused, **kwargs):
                    if step == "context": return b"desktop-linux"
                    if step == "endpoint": return b"unix:///synthetic/docker.sock"
                    if step == "image-platform": return b"linux/amd64"
                    if step == "volume-owner": return receipt["runId"].encode()
                    if step.startswith("start-"):
                        kind = step.removeprefix("start-")
                        created[kind] = {"id": ("a" if kind == "fake" else "b")*64,
                            "name": receipt["resources"][kind], "owner": receipt["runId"], "running": True}
                        if step == failed_step: raise smoke.SmokeError("COMMAND_TIMEOUT")
                        return created[kind]["id"].encode()
                    kind = step.rsplit("-", 1)[-1]
                    if step.startswith("cleanup-find-"):
                        return json.dumps({k:v for k,v in created[kind].items() if k != "running"}).encode() if kind in created else b""
                    if step.startswith("cleanup-stop-"):
                        self.assertEqual(args[-1], created[kind]["id"])
                        created[kind]["running"] = False
                    if step.startswith("cleanup-state-"): return b"false"
                    return b""
                with patch.object(smoke.Runner, "call", call), self.assertRaisesRegex(smoke.SmokeError, "^COMMAND_TIMEOUT$"):
                    smoke.smoke(Path(tmp), receipt, manifest, hashes, workflows)
                self.assertTrue(created)
                self.assertTrue(all(not value["running"] for value in created.values()))
                self.assertEqual(receipt["cleanup"], "OWNED_CONTAINERS_STOPPED_RESOURCES_PRESERVED")

    def test_cleanup_never_stops_a_foreign_container(self):
        names = {"fake": "expected-fake", "n8n": "expected-n8n"}
        with tempfile.TemporaryDirectory() as tmp:
            receipt = {}
            runner = smoke.Runner(Path(tmp), receipt)
            with patch.object(runner, "call", return_value=json.dumps({"id":"a"*64,"name":names["n8n"],"owner":"another-run"}).encode()) as call:
                smoke.cleanup_containers(runner, names, "expected-run")
            self.assertTrue(all(c.args[0].startswith("cleanup-find-") for c in call.call_args_list))
            self.assertEqual(receipt["cleanup"], "MANUAL_OWNED_CLEANUP_REQUIRED")

    def test_cleanup_inspection_failure_is_not_reported_as_complete(self):
        with tempfile.TemporaryDirectory() as tmp:
            receipt = {}
            runner = smoke.Runner(Path(tmp), receipt)
            with patch.object(runner, "call", side_effect=smoke.SmokeError("COMMAND_TIMEOUT")):
                smoke.cleanup_containers(runner, {"fake":"expected-fake","n8n":"expected-n8n"}, "expected-run")
            self.assertEqual(receipt["code"], "CLEANUP_FAILED")
            self.assertEqual(receipt["cleanup"], "MANUAL_OWNED_CLEANUP_REQUIRED")

    def test_unacknowledged_creation_not_yet_visible_stays_indeterminate(self):
        names = {"fake":"expected-fake", "n8n":"expected-n8n"}
        with tempfile.TemporaryDirectory() as tmp:
            receipt = {}
            runner = smoke.Runner(Path(tmp), receipt)
            with patch.object(runner, "call", return_value=b""):
                smoke.cleanup_containers(runner, names, "expected-run", {names["fake"]})
            self.assertEqual(receipt["cleanup"], "MANUAL_OWNED_CLEANUP_REQUIRED")

    def test_cli_success_and_expected_errors_are_checked_from_result_not_exit_code(self):
        for flow in ("W01", "W02", "W03", "W04", "W05", "W06", "W07", "W08"):
            smoke.check_execution(output(flow), flow)
        smoke.check_execution(output("W06", True), "W06", True)
        with self.assertRaisesRegex(smoke.SmokeError, "EXECUTION_STATUS"):
            smoke.check_execution(output("W06"), "W06", True)

    def test_cli_rejects_invalid_unexpected_or_private_output(self):
        with self.assertRaisesRegex(smoke.SmokeError, "EXECUTION_OUTPUT_INVALID"):
            smoke.execution(("not JSON "+SENTINEL).encode())
        with self.assertRaisesRegex(smoke.SmokeError, "EXECUTION_SUMMARY"):
            smoke.check_execution(output("W07", summary={"status": "NEEDS_REVIEW", "private": SENTINEL}), "W07")
        with self.assertRaisesRegex(smoke.SmokeError, "EXECUTION_NODE"):
            smoke.check_execution(output("W06").replace("Executar operação privada".encode(), b"Wrong node"), "W06")

    def test_restart_export_must_keep_all_nodes_refs_settings_and_inactive_state(self):
        _, _, workflows = smoke.load_bundle()
        exported = copy.deepcopy(list(workflows.values()))
        smoke.verify_exports(exported, workflows)
        exported[0]["active"] = True
        with self.assertRaisesRegex(smoke.SmokeError, "EXPORT_CHANGED"):
            smoke.verify_exports(exported, workflows)
        with self.assertRaisesRegex(smoke.SmokeError, "EXPORT_COUNT"):
            smoke.verify_exports(exported[:-1], workflows)

    def test_secret_log_scan_fails_without_redacting_original(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/"private.stdout"
            smoke.private_write(path, "private: "+SENTINEL)
            with self.assertRaisesRegex(smoke.SmokeError, "^LEAK$"):
                smoke.check_no_token([path], SENTINEL, "LEAK")
            self.assertIn(SENTINEL, path.read_text())
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_database_bytes_execution_data_and_journal_are_scanned(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            db_path = root/"source.sqlite"
            with sqlite3.connect(db_path) as db:
                db.execute("CREATE TABLE credentials_entity(id TEXT,type TEXT,data TEXT)")
                db.execute("CREATE TABLE execution_data(data TEXT)")
                db.execute("INSERT INTO credentials_entity VALUES(?,?,?)", ("vrWa070Control01", "httpHeaderAuth", "synthetic-encrypted-credential-not-a-secret-0000"))
            payload = {"database.sqlite": base64.b64encode(db_path.read_bytes()).decode()}
            safe = root/"safe"
            safe.mkdir()
            result = smoke.inspect_database(safe, json.dumps(payload), SENTINEL)
            self.assertEqual(result["credentialRows"], 1)
            self.assertEqual(result["executionDataRows"], 0)
            payload["database.sqlite-journal"] = base64.b64encode(SENTINEL.encode()).decode()
            leaked = root/"leaked"
            leaked.mkdir()
            with self.assertRaisesRegex(smoke.SmokeError, "^CONTROL_TOKEN_DATABASE_LEAK$"):
                smoke.inspect_database(leaked, json.dumps(payload), SENTINEL)

    def test_checkpointed_and_pending_wal_records_are_both_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            path = root / "database.sqlite"
            db = sqlite3.connect(path)
            try:
                db.execute("PRAGMA journal_mode=WAL")
                db.execute("CREATE TABLE credentials_entity(id TEXT,type TEXT,data TEXT)")
                db.execute("CREATE TABLE execution_data(data TEXT)")
                db.execute("INSERT INTO credentials_entity VALUES(?,?,?)", ("vrWa070Control01", "httpHeaderAuth", "e"*40))
                db.commit()
                db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
                for phase, count in (("checkpoint", 0), ("pending", 1)):
                    with self.subTest(phase=phase):
                        files = [path]
                        if phase == "pending":
                            db.execute("INSERT INTO execution_data VALUES('synthetic')")
                            db.commit()
                            files.append(root / "database.sqlite-wal")
                        payload = {f.name: base64.b64encode(f.read_bytes()).decode() for f in files}
                        snapshot = root / phase
                        snapshot.mkdir()
                        result = smoke.inspect_database(snapshot, json.dumps(payload), SENTINEL)
                        self.assertEqual(result["credentialRows"], 1)
                        self.assertEqual(result["executionDataRows"], count)
            finally:
                db.close()

    def test_docker_runner_discards_inherited_secrets_and_has_bounded_timeouts(self):
        with tempfile.TemporaryDirectory() as tmp, patch.dict(smoke.os.environ, {"OPENAI_API_KEY": SENTINEL, "DOCKER_HOST": "tcp://remote.invalid"}):
            receipt = {"steps": []}
            runner = smoke.Runner(Path(tmp), receipt)
            with patch.object(smoke.subprocess, "run") as run:
                run.return_value.stdout, run.return_value.stderr, run.return_value.returncode = b"ok", b"", 0
                self.assertEqual(runner.call("read-only-mocked", ["context", "show"]), b"ok")
                kwargs = run.call_args.kwargs
                self.assertLessEqual(kwargs["timeout"], 45)
                self.assertEqual(set(kwargs["env"]), {"PATH", "HOME", "LANG"})
                self.assertNotIn(SENTINEL, json.dumps(kwargs["env"]))
                self.assertFalse(kwargs.get("shell", False))

    def test_image_inspect_uses_supported_arguments_and_still_rejects_wrong_platform(self):
        manifest, hashes, workflows = smoke.load_bundle()
        for platform, code in ((b"linux/amd64", "STOP_BEFORE_MUTATION"), (b"linux/arm64", "IMAGE_PLATFORM")):
            with self.subTest(platform=platform), tempfile.TemporaryDirectory() as tmp:
                calls = []
                receipt = {"steps": []}
                def call(_runner, step, args, *unused, **kwargs):
                    calls.append((step, args))
                    if step == "context": return b"local-test"
                    if step == "endpoint": return b"unix:///synthetic/docker.sock"
                    if step == "image-platform": return platform
                    raise smoke.SmokeError("STOP_BEFORE_MUTATION")
                with patch.object(smoke.Runner, "call", call), self.assertRaisesRegex(smoke.SmokeError, "^" + code + "$"):
                    smoke.smoke(Path(tmp), receipt, manifest, hashes, workflows)
                image_call = next(args for step, args in calls if step == "image-platform")
                self.assertEqual(image_call, ["image", "inspect", smoke.IMAGE, "--format", "{{.Os}}/{{.Architecture}}"])
                self.assertEqual(any(step == "network" for step, _ in calls), platform == b"linux/amd64")
                if platform != b"linux/amd64":
                    self.assertFalse((Path(tmp) / "n8n.env").exists())
                    self.assertFalse((Path(tmp) / "fake.env").exists())

    def test_remote_docker_is_rejected_before_any_mutation_or_secret_file(self):
        manifest, hashes, workflows = smoke.load_bundle()
        with tempfile.TemporaryDirectory() as tmp, patch.object(smoke.subprocess, "run") as run:
            root = Path(tmp)
            run.side_effect = [SimpleNamespace(stdout=b"remote\n", stderr=b"", returncode=0),
                               SimpleNamespace(stdout=b"tcp://remote.invalid:2376\n", stderr=b"", returncode=0)]
            with self.assertRaisesRegex(smoke.SmokeError, "^DOCKER_NOT_LOCAL_UNIX$"):
                smoke.smoke(root, {"steps": []}, manifest, hashes, workflows)
            self.assertEqual(run.call_count, 2)
            self.assertEqual([c.args[0][1:3] for c in run.call_args_list], [["context", "show"], ["context", "inspect"]])
            self.assertFalse((root/"n8n.env").exists())
            self.assertFalse((root/"fake.env").exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)

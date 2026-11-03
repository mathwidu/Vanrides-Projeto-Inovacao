#!/usr/bin/env python3
"""Smoke sintético SQLite/CLI, sem queue, worker real, WhatsApp ou OpenAI.

Uso: python3 src/whatsapp/scripts/070-n8n-smoke.py [--preflight-only]
Recibo seguro no stdout; logs/segredos apenas no runtime privado. Containers,
rede e volume exclusivos ficam preservados e os containers são parados.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import secrets
import signal
import sqlite3
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[3]
BUNDLE = ROOT / "src/whatsapp/workflows"
IMAGE = "docker.n8n.io/n8nio/n8n@sha256:80e23ced901f0e22597d4f3464e3aaf9d835372262ea6f08ef760d8ca35df25c"
LABEL = "io.vanrides.wa080.smoke-run"
FLOWS = [f"W0{i}" for i in range(1, 9)]
CONTROL = "http://vanrides-wa070-worker:7080"
EXPECTED = {"W01": {"status": "IDLE", "claimed": 0, "processed": 0, "retried": 0, "paused": 0},
            "W02": {"status": "IDLE", "claimed": 0, "processed": 0, "retried": 0, "paused": 0},
            "W03": {"status": "IDLE", "claimed": 0, "processed": 0, "retried": 0, "paused": 0},
            "W05": {"status": "IDLE", "claimed": 0, "processed": 0, "retried": 0, "paused": 0},
            "W04": {"status": "IDLE", "calendarProcessed": 0, "reminderProcessed": 0},
            "W06": {"status": "IDLE", "claimed": 0, "submitted": 0, "unknown": 0, "failed": 0},
            "W07": {"status": "NEEDS_REVIEW"}, "W08": {"status": "IDLE", "processed": 0}}
FAKE = r"""
const http = require('node:http'), fs = require('node:fs');
const stats = {process:0, attendance:0, dispatch:0, reconcile:0, rejected:0, domain:0};
const save = () => fs.writeFileSync('/tmp/counts.json', JSON.stringify(stats), {mode:0o600}); save();
http.createServer((req,res) => {
 const reply = (code, body) => {save(); res.writeHead(code, {'content-type':'application/json'}); res.end(JSON.stringify(body));};
 const path = req.url, known = ['/v1/process-inbox','/v1/dispatch-one','/v1/tick-attendance','/v1/reconcile-delivery'].includes(path);
 if (!known) {stats.domain++; return reply(404,{status:'NOT_FOUND'});}
 if (req.headers.authorization !== 'Bearer '+process.env.CONTROL_TOKEN) {stats.rejected++; return reply(401,{status:'UNAUTHORIZED'});}
 let body = ''; req.on('data', c => {body += c; if (body.length > 32) req.destroy();});
 req.on('end', () => {
  if (req.method !== 'POST' || body !== '{}') {stats.rejected++; return reply(400,{status:'INVALID_REQUEST'});}
  if (path === '/v1/process-inbox') {stats.process++; return reply(200,{status:'IDLE',claimed:0,processed:0,retried:0,paused:0});}
  if (path === '/v1/tick-attendance') {stats.attendance++; return reply(200,{status:'IDLE',calendarProcessed:0,reminderProcessed:0});}
  if (path === '/v1/dispatch-one') {stats.dispatch++; return reply(200,{status:'IDLE',claimed:0,submitted:0,unknown:0,failed:0});}
  if (path === '/v1/reconcile-delivery') {stats.reconcile++; return reply(200,{status:'IDLE',processed:0});}
  stats.rejected++; reply(405,{status:'INVALID_REQUEST'});
 });
}).listen(7080,'0.0.0.0');
"""


class SmokeError(Exception):
    pass


def require(ok, code):
    if not ok:
        raise SmokeError(code)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def load_bundle(directory=None):
    """Preflight puro: nenhum subprocesso, arquivo criado ou conteúdo em erros."""
    directory = BUNDLE if directory is None else directory
    try:
        raw = (directory / "manifest.json").read_bytes()
        m = json.loads(raw)
        require(m["schemaVersion"] == 1 and m["card"] == "VR-WA-120-DELIVERY-RECOVERY", "MANIFEST_SCHEMA")
        require(m["n8nLocalImage"] == IMAGE and m["imagePlatform"] == "linux/amd64"
                and m["n8nVersion"] == "2.45.4", "MANIFEST_RUNTIME")
        require(m["credentials"] == [{"id": "vrWa070Control01", "name": "VanRides Controle070",
                "type": "httpHeaderAuth", "headerName": "Authorization", "valueSource": "PRIVATE_CONTROL_TOKEN",
                "containsNoExportedSecret": True}], "MANIFEST_CREDENTIAL")
        require(len(m["workflows"]) == 8 and {r["logicalId"] for r in m["workflows"]} == set(FLOWS)
                and sorted(m["importOrder"]) == FLOWS, "MANIFEST_WORKFLOWS")
        hashes, workflows = {"manifest.json": digest(raw)}, {}
        for row in m["workflows"]:
            flow = row["logicalId"]
            require(row["file"] == flow + ".json", "WORKFLOW_PATH")
            blob = (directory / row["file"]).read_bytes()
            require(digest(blob) == row["sha256"], "WORKFLOW_HASH")
            w = json.loads(blob)
            require(w["id"] == row["id"] == "vrWa070Flow0000" + flow[-1]
                    and w["active"] is False and row["active"] is False and w["pinData"] == {}, "WORKFLOW_IDENTITY")
            s = w["settings"]
            require(s["saveDataErrorExecution"] == s["saveDataSuccessExecution"] == "none"
                    and s["saveManualExecutions"] is False and s["saveExecutionProgress"] is False, "WORKFLOW_RETENTION")
            require(s.get("errorWorkflow") == (None if flow == "W07" else "vrWa070Flow00007")
                    and s["callerIds"] == "vrWa070Flow00001,vrWa070Flow00008", "WORKFLOW_REFERENCES")
            http_nodes = [n for n in w["nodes"] if n["type"] == "n8n-nodes-base.httpRequest"]
            require(len(http_nodes) == (1 if flow in ("W01", "W02", "W03", "W04", "W05", "W06", "W08") else 0), "WORKFLOW_HTTP_COUNT")
            require(row["mode"] == "IMPLEMENTED_CONTROL", "WORKFLOW_MODE")
            require(row["prerequisite"] is None, "WORKFLOW_PREREQUISITE")
            for n in w["nodes"]:
                kind = n["type"].removeprefix("n8n-nodes-base.")
                require(n["type"] == "n8n-nodes-base." + kind and n["typeVersion"] == m["nodeVersions"][kind]
                        and kind in {"scheduleTrigger", "httpRequest", "executeWorkflowTrigger", "errorTrigger", "set", "stopAndError"}, "WORKFLOW_NODE")
            for n in http_nodes:
                p = n["parameters"]
                path = {"W01": "/v1/process-inbox", "W02": "/v1/process-inbox", "W03": "/v1/process-inbox", "W04": "/v1/tick-attendance", "W05": "/v1/process-inbox", "W06": "/v1/dispatch-one", "W08": "/v1/reconcile-delivery"}[flow]
                require(p["url"] == CONTROL + path and p["method"] == "POST"
                        and p["jsonBody"] == "{}" and n["retryOnFail"] is False
                        and n["credentials"]["httpHeaderAuth"]["id"] == "vrWa070Control01"
                        and p["authentication"] == "genericCredentialType" and p["genericAuthType"] == "httpHeaderAuth", "WORKFLOW_CONTROL")
            hashes[row["file"]], workflows[flow] = digest(blob), w
        return m, hashes, workflows
    except SmokeError:
        raise
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        raise SmokeError("BUNDLE_INVALID") from None


def private_write(path, content, mode=0o600):
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode), "wb") as f:
        f.write(content if isinstance(content, bytes) else content.encode())


def check_no_token(paths, token, code):
    require(all(token.encode() not in p.read_bytes() for p in paths), code)


def execution(stdout):
    text = stdout.decode("utf-8", "replace")
    decoder = json.JSONDecoder()
    for match in re.finditer(r"\{", text):
        try:
            value, _ = decoder.raw_decode(text[match.start():])
            if isinstance(value, dict) and isinstance(value.get("data", {}).get("resultData", {}).get("runData"), dict):
                return value["data"]["resultData"]
        except (ValueError, AttributeError, TypeError):
            continue
    raise SmokeError("EXECUTION_OUTPUT_INVALID")


def check_execution(stdout, flow, failed=False):
    result = execution(stdout)
    node = "Estado sanitizado" if flow == "W07" else "Executar operação privada"
    require(result.get("lastNodeExecuted") == node, "EXECUTION_NODE")
    require(bool(result.get("error")) == failed, "EXECUTION_STATUS")
    if failed:
        require(node in result["runData"], "EXECUTION_ERROR")
    else:
        try:
            items = result["runData"][node][-1]["data"]["main"][0]
            require(len(items) == 1 and items[0]["json"] == EXPECTED[flow], "EXECUTION_SUMMARY")
        except (KeyError, TypeError, IndexError):
            raise SmokeError("EXECUTION_SUMMARY") from None


class Runner:
    def __init__(self, runtime, receipt):
        self.runtime, self.receipt, self.context = runtime, receipt, None
        # A cold Linux CLI import took 26s and each independent execution 10-14s
        # on the VPS with the fixed one-CPU limit. Keep a bounded whole run,
        # without relaxing the 45s command timeout or container resource limits.
        self.deadline = time.monotonic() + 240
        self.env = {"PATH": os.environ.get("PATH", "/usr/local/bin:/usr/bin:/bin"), "HOME": str(Path.home()), "LANG": "C.UTF-8"}

    def call(self, step, args, data=None, check=True):
        started = time.monotonic()
        require(self.deadline > started, "TOTAL_TIMEOUT")
        command = ["docker"] + (["--context", self.context] if self.context else []) + args
        try:
            p = subprocess.run(command, input=data, capture_output=True, env=self.env, timeout=min(45, self.deadline-started))
        except subprocess.TimeoutExpired as e:
            private_write(self.runtime / (step + ".stdout"), e.stdout or b"")
            private_write(self.runtime / (step + ".stderr"), e.stderr or b"")
            self.receipt["steps"].append({"step": step, "seconds": round(time.monotonic()-started, 3), "outcome": "TIMEOUT"})
            raise SmokeError("COMMAND_TIMEOUT") from None
        private_write(self.runtime / (step + ".stdout"), p.stdout)
        private_write(self.runtime / (step + ".stderr"), p.stderr)
        self.receipt["steps"].append({"step": step, "seconds": round(time.monotonic()-started, 3), "exitCode": p.returncode})
        require(not check or p.returncode == 0, "COMMAND_FAILED_" + step.upper())
        return p.stdout

    def node_file(self, name, content):
        code = "require('node:fs').writeFileSync('/tmp/" + name + "',require('node:fs').readFileSync(0),{mode:0o600})"
        self.call("write-" + name, ["exec", "-i", "--user", "node", self.n8n, "node", "-e", code], content)

    def cli(self, step, *args, check=True):
        return self.call(step, ["exec", "--user", "node", self.n8n, "n8n", *args], check=check)


def verify_exports(exported, workflows):
    require(isinstance(exported, list) and len(exported) == 8, "EXPORT_COUNT")
    actual = {w["id"]: w for w in exported}
    for original in workflows.values():
        require(original["id"] in actual, "EXPORT_ID")
        w = actual[original["id"]]
        require(w["active"] is False and all(w[k] == original[k] for k in ("nodes", "connections", "settings")), "EXPORT_CHANGED")


def inspect_database(runtime, payload, token):
    import base64
    files = json.loads(payload)
    require("database.sqlite" in files, "DATABASE_MISSING")
    directory = runtime / "database"
    directory.mkdir(mode=0o700)
    for name, encoded in files.items():
        require(re.fullmatch(r"database\.sqlite(?:-(?:wal|shm|journal))?", name), "DATABASE_FILENAME")
        raw = base64.b64decode(encoded, validate=True)
        private_write(directory / name, raw)
        require(token.encode() not in raw, "CONTROL_TOKEN_DATABASE_LEAK")
    # This is a private copy of the captured bytes. SQLite may need to create a
    # WAL index even to read a checkpointed database with no sidecars.
    from contextlib import closing
    with closing(sqlite3.connect((directory / "database.sqlite").as_uri() + "?mode=rw", uri=True)) as db:
        db.execute("PRAGMA query_only=ON")
        credentials = db.execute('SELECT id,type,data FROM credentials_entity').fetchall()
        require(len(credentials) == 1 and credentials[0][:2] == ("vrWa070Control01", "httpHeaderAuth")
                and len(credentials[0][2]) > 32 and not credentials[0][2].lstrip().startswith("{"), "CREDENTIAL_ENCRYPTION")
        rows = db.execute('SELECT data FROM execution_data').fetchall()
        require(all(token not in str(row[0]) for row in rows), "CONTROL_TOKEN_EXECUTION_DATA_LEAK")
        return {"credentialRows": len(credentials), "executionDataRows": len(rows), "rawFilesScanned": len(files)}


def cleanup_containers(runner, names, run_id, unacknowledged=()):
    """Reconcile by exact name + run label, including creation with a lost ACK."""
    runner.deadline = time.monotonic() + 10
    failed = False
    for kind in ("n8n", "fake"):
        try:
            template = '{"id":"{{.ID}}","name":"{{.Names}}","owner":"{{.Label "' + LABEL + '"}}"}'
            raw = runner.call("cleanup-find-"+kind, ["container", "ls", "--all", "--no-trunc",
                "--filter", "name=^/"+names[kind]+"$", "--format", template])
            found = [json.loads(line) for line in raw.decode().splitlines() if line.strip()]
            require(len(found) <= 1, "CLEANUP_AMBIGUOUS")
            if not found:
                # A daemon may still complete creation after the client timed out.
                require(names[kind] not in unacknowledged, "CLEANUP_CREATION_UNKNOWN")
                continue
            item = found[0]
            require(item.get("name") == names[kind] and item.get("owner") == run_id
                    and re.fullmatch(r"[0-9a-f]{64}", item.get("id", "")), "CLEANUP_OWNER")
            container_id = item["id"]
            runner.call("cleanup-stop-"+kind, ["stop", "--time=2", container_id])
            stopped = runner.call("cleanup-state-"+kind, ["inspect", container_id, "--format", "{{.State.Running}}"])
            require(stopped.decode().strip() == "false", "CLEANUP_NOT_STOPPED")
        except (Exception, KeyboardInterrupt):
            failed = True
    if failed:
        runner.receipt.update(status="FAIL", code="CLEANUP_FAILED", cleanup="MANUAL_OWNED_CLEANUP_REQUIRED")
    else:
        runner.receipt["cleanup"] = "OWNED_CONTAINERS_STOPPED_RESOURCES_PRESERVED"


def smoke(runtime, receipt, manifest, hashes, workflows):
    r = Runner(runtime, receipt)
    run_id = uuid.uuid4().hex
    prefix = "wa080-smoke-" + run_id[:8]
    names = {k: prefix + "-" + k for k in ("n8n", "fake", "net", "data")}
    receipt["resources"] = names
    receipt["runId"] = run_id
    token = secrets.token_urlsafe(32)
    key = secrets.token_hex(32)
    launch_attempted = False
    unacknowledged = set()
    try:
        context = r.call("context", ["context", "show"]).decode().strip()
        require(re.fullmatch(r"[\w.-]{1,80}", context), "DOCKER_CONTEXT")
        endpoint = r.call("endpoint", ["context", "inspect", context, "--format", "{{.Endpoints.docker.Host}}"]).decode().strip()
        require(endpoint.startswith("unix:///") and "\n" not in endpoint, "DOCKER_NOT_LOCAL_UNIX")
        r.context = context
        image_platform = r.call("image-platform", ["image", "inspect", IMAGE, "--format", "{{.Os}}/{{.Architecture}}"]).decode().strip()
        require(image_platform == manifest["imagePlatform"], "IMAGE_PLATFORM")
        receipt.update({"image": IMAGE, "imagePlatform": image_platform, "hostPlatform": platform.system()+"/"+platform.machine()})
        env = {"N8N_ENCRYPTION_KEY": key, "N8N_DIAGNOSTICS_ENABLED": "false", "N8N_VERSION_NOTIFICATIONS_ENABLED": "false",
               "N8N_TEMPLATES_ENABLED": "false", "N8N_COMMUNITY_PACKAGES_ENABLED": "false", "N8N_RUNNERS_ENABLED": "false",
               "N8N_LOG_LEVEL": "info", "N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS": "true", "EXECUTIONS_MODE": "regular",
               "EXECUTIONS_DATA_SAVE_ON_ERROR": "none", "EXECUTIONS_DATA_SAVE_ON_SUCCESS": "none",
               "EXECUTIONS_DATA_SAVE_ON_PROGRESS": "false", "EXECUTIONS_DATA_SAVE_MANUAL_EXECUTIONS": "false"}
        private_write(runtime / "n8n.env", "".join(f"{k}={v}\n" for k, v in env.items()))
        private_write(runtime / "fake.env", "CONTROL_TOKEN=" + token + "\n")
        snapshots = runtime / "input"
        snapshots.mkdir(mode=0o700)
        for flow in FLOWS:
            blob = (BUNDLE / (flow+".json")).read_bytes()
            require(digest(blob) == hashes[flow+".json"], "SOURCE_CHANGED_DURING_SMOKE")
            private_write(snapshots / (flow+".json"), blob, 0o400)
        r.call("network", ["network", "create", "--internal", "--label", LABEL+"="+run_id, names["net"]])
        r.call("volume", ["volume", "create", "--label", LABEL+"="+run_id, names["data"]])
        require(r.call("volume-owner", ["volume", "inspect", names["data"], "--format", '{{index .Labels "'+LABEL+'"}}']).decode().strip() == run_id, "VOLUME_OWNER")
        common = ["run", "-d", "--pull=never", "--platform=linux/amd64", "--cpus=1", "--pids-limit=128", "--cap-drop=ALL", "--security-opt=no-new-privileges:true",
                  "--label", LABEL+"="+run_id, "--network", names["net"], "--entrypoint", "node"]
        for kind, options, code in [("fake", ["--memory=128m", "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=8m", "--network-alias", "vanrides-wa070-worker"], FAKE),
                                    ("n8n", ["--memory=640m", "--mount", "type=volume,source="+names["data"]+",target=/home/node/.n8n"], "setInterval(()=>{},1000)")]:
            launch_attempted = True
            unacknowledged.add(names[kind])
            container_id = r.call("start-"+kind, common+["--name", names[kind], "--env-file", str(runtime/(kind+".env"))]+options+[IMAGE, "-e", code]).decode().strip()
            require(re.fullmatch(r"[0-9a-f]{64}", container_id), "CONTAINER_ID")
            unacknowledged.remove(names[kind])
        r.n8n = names["n8n"]
        version = r.cli("version", "--version").decode().strip()
        require(version == manifest["n8nVersion"], "N8N_VERSION")
        receipt["n8nVersion"] = version
        r.node_file("workflows.json", json.dumps([workflows[f] for f in manifest["importOrder"]]).encode())
        r.cli("import-workflows", "import:workflow", "--input=/tmp/workflows.json")
        credential = [{"id": "vrWa070Control01", "name": "VanRides Controle070", "type": "httpHeaderAuth",
                       "data": {"name": "Authorization", "value": "Bearer " + token}}]
        r.node_file("credential.json", json.dumps(credential).encode())
        try:
            r.cli("import-credential", "import:credentials", "--input=/tmp/credential.json")
        finally:
            r.call("delete-plaintext", ["exec", r.n8n, "node", "-e", "require('node:fs').unlinkSync('/tmp/credential.json')"])
        for flow in ("W01", "W02", "W03", "W06", "W08", "W07", "W04", "W05"):
            check_execution(r.cli("execute-"+flow, "execute", "--id="+workflows[flow]["id"], "--rawOutput", check=False), flow)
            receipt.setdefault("scenarios", {})[flow] = "PASS"
        stats = json.loads(r.call("fake-counts", ["exec", names["fake"], "cat", "/tmp/counts.json"]))
        require(stats == {"process": 4, "attendance": 1, "dispatch": 1, "reconcile": 1, "rejected": 0, "domain": 0}, "CONTROL_REQUESTS")
        receipt["controlRequests"] = stats
        r.call("restart", ["restart", "--time=2", r.n8n])
        r.cli("export", "export:workflow", "--all", "--output=/tmp/export.json")
        exported = r.call("export-read", ["exec", r.n8n, "cat", "/tmp/export.json"])
        verify_exports(json.loads(exported), workflows)
        r.call("fake-stop", ["stop", "--time=2", names["fake"]])
        check_execution(r.cli("execute-unavailable", "execute", "--id="+workflows["W06"]["id"], "--rawOutput", check=False), "W06", True)
        receipt["scenarios"]["W06_CONTROL_UNAVAILABLE"] = "EXPECTED_ERROR"
        for kind in ("n8n", "fake"):
            r.call("logs-"+kind, ["logs", names[kind]])
        db_code = "const f=require('node:fs'),p='/home/node/.n8n/'; const o={}; for(const n of f.readdirSync(p)) if(/^database\\.sqlite(?:-(?:wal|shm|journal))?$/.test(n))o[n]=f.readFileSync(p+n).toString('base64'); process.stdout.write(JSON.stringify(o));"
        receipt["database"] = inspect_database(runtime, r.call("database-snapshot", ["exec", r.n8n, "node", "-e", db_code]), token)
        check_no_token((p for p in runtime.iterdir() if p.suffix in (".stdout", ".stderr")), token, "CONTROL_TOKEN_LOG_OR_EXPORT_LEAK")
        require(load_bundle()[1] == hashes, "SOURCE_CHANGED_DURING_SMOKE")
        receipt["status"] = "PASS"
    finally:
        if launch_attempted:
            cleanup_containers(r, names, run_id, unacknowledged)
        else:
            receipt["cleanup"] = "NO_CONTAINER_LAUNCH_ATTEMPTED"


def main(argv=None):
    started = time.monotonic()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preflight-only", action="store_true")
    parser.add_argument("--runtime-parent", type=Path, default=Path.home()/".local/share/vanrides/wa080/n8n-smoke")
    args = parser.parse_args(argv)
    receipt = {"status": "FAIL", "scope": "LOCAL_SQLITE_CLI_SYNTHETIC", "queueWorkerProof": False,
               "realWhatsApp": False, "physicalDevices": False, "openAI": False, "steps": []}
    runtime = None
    try:
        receipt["scriptSha256"] = digest(Path(__file__).read_bytes())
        manifest, hashes, workflows = load_bundle()
        receipt["workflowHashes"] = hashes
        if args.preflight_only:
            receipt.update(status="PASS", scope="PREFLIGHT_ONLY_NO_DOCKER")
        else:
            os.umask(0o077)
            supplied = args.runtime_parent.expanduser()
            parent = supplied.resolve()
            require(not supplied.is_symlink() and not parent.is_relative_to(ROOT.resolve()), "RUNTIME_LOCATION")
            parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            require(parent.stat().st_uid == os.getuid() and parent.stat().st_mode & 0o077 == 0, "RUNTIME_PERMISSIONS")
            runtime = Path(tempfile.mkdtemp(prefix="smoke-", dir=parent))
            smoke(runtime, receipt, manifest, hashes, workflows)
        require(digest(Path(__file__).read_bytes()) == receipt["scriptSha256"], "SCRIPT_CHANGED_DURING_SMOKE")
    except (Exception, KeyboardInterrupt) as e:
        code = "CLEANUP_FAILED" if receipt.get("cleanup") == "MANUAL_OWNED_CLEANUP_REQUIRED" else str(e) if isinstance(e, SmokeError) else "INTERRUPTED" if isinstance(e, KeyboardInterrupt) else "UNEXPECTED_ERROR"
        receipt.update(status="FAIL", code=code)
    receipt["totalSeconds"] = round(time.monotonic()-started, 3)
    if runtime:
        receipt["privateRuntime"] = str(runtime)
        private_write(runtime/"receipt.json", json.dumps(receipt, indent=2)+"\n")
    print(json.dumps(receipt, ensure_ascii=True))
    return 0 if receipt["status"] == "PASS" else 1


if __name__ == "__main__":
    def interrupted(_signum, _frame):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())

#!/usr/bin/env python3
"""One local-VPS nonweb operator job. Never SSHs, updates or restarts the API."""
import argparse
import base64
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import socket
import stat
import subprocess
import sys
import time
import uuid
from urllib.parse import urlsplit

ENV = "vanrides-mvp-hml"
API = "vanrides-hml-api_api"
NETWORK = "vanrides-hml-api_private"
OWNER = "dev.vanrides.operator-owner"
SCOPE = "vanrides-hml-nonweb-operator"
OPS = {
    "create-test-cohort": {"synthetic"},
    "provision-service-credential": {"principal", "allowedInstanceId", "expiresAt"},
    "create-authorized-cohort": {"reasonCode"},
    "admit-participant": {"identityId", "allowedInstanceId", "phoneNumber", "phoneVerification"},
    "driver-bootstrap": {"identityId", "inviteId", "expiresAt"},
    "revoke-service-credential": {"credentialId", "reasonCode"},
}
ROLES = {"CHANNEL", "CONVERSATION_WORKER", "DELIVERY_WORKER", "SYSTEM_JOB"}
SECRET_ENV = ("SPRING_DATASOURCE_PASSWORD", "VANRIDES_JWT_SECRET", "WA_SECRET_HASH_KEY",
              "WA_RESPONSE_ENCRYPTION_KEY", "WA_OPERATOR_CREDENTIAL")


class Blocked(Exception):
    pass


class Cancelled(BaseException):
    pass


def require(value, code):
    if not value:
        raise Blocked(code)


def encode(value):
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True) + "\n").encode()


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def hash_value(value):
    require(isinstance(value, str) and re.fullmatch("[a-f0-9]{64}", value), "HASH_INVALID")
    return value


def uuid_value(value):
    try:
        require(isinstance(value, str) and str(uuid.UUID(value)) == value, "UUID_INVALID")
    except (ValueError, AttributeError):
        raise Blocked("UUID_INVALID") from None
    return value


def stamp(value):
    try:
        require(isinstance(value, str) and re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?Z", value),
                "UTC_TIMESTAMP_REQUIRED")
        # Python 3.9 accepts microseconds; Java Instant may emit nanoseconds.
        parsed = value[:26] + "Z" if value[19:20] == "." and len(value) > 27 else value
        return dt.datetime.fromisoformat(parsed.replace("Z", "+00:00"))
    except ValueError:
        raise Blocked("UTC_TIMESTAMP_REQUIRED") from None


def instant_key(value):
    stamp(value)
    fraction = value[20:-1] if value[19:20] == "." else ""
    return value[:19] + "." + fraction.ljust(9, "0") + "Z"


def token(value):
    try:
        return (isinstance(value, str) and re.fullmatch("[A-Za-z0-9_-]{43}", value)
                and base64.urlsafe_b64encode(base64.urlsafe_b64decode(value + "=")).decode().rstrip("=") == value)
    except (ValueError, TypeError):
        return False


def strict_pairs(pairs):
    out = {}
    for key, value in pairs:
        require(key not in out, "JSON_DUPLICATE_KEY")
        out[key] = value
    return out


def parse(raw):
    try:
        value = json.loads(raw, object_pairs_hook=strict_pairs,
                           parse_constant=lambda _: (_ for _ in ()).throw(Blocked("JSON_INVALID")))
        require(isinstance(value, dict), "JSON_OBJECT_REQUIRED")
        return value
    except (ValueError, UnicodeError):
        raise Blocked("JSON_INVALID") from None


def read(file, maximum=2 * 1024**2, private=False):
    file = Path(file)
    require(file.is_absolute() and file.parent.resolve() == file.parent, "ABSOLUTE_CANONICAL_INPUT_REQUIRED")
    fd = os.open(file, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        before = os.fstat(fd)
        require(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= maximum, "INPUT_INVALID")
        if private:
            require(before.st_uid == os.getuid() and stat.S_IMODE(before.st_mode) == 0o600, "PRIVATE_INPUT_REQUIRED")
        with os.fdopen(fd, "rb", closefd=False) as stream:
            raw = stream.read(maximum + 1)
        after = os.fstat(fd)
        require(len(raw) == before.st_size and all(getattr(before, k) == getattr(after, k)
                for k in ("st_dev", "st_ino", "st_size", "st_mtime_ns", "st_ctime_ns")), "INPUT_CHANGED")
        return raw
    finally:
        os.close(fd)


def digest_file(file):
    return sha(read(file, 128 * 1024**2))


def write_new(file, raw, mode=0o600):
    fd = os.open(file, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, mode)
    with os.fdopen(fd, "wb") as stream:
        # umask 077 must not turn public, non-secret bind assets 0444 into 0400.
        os.fchmod(stream.fileno(), mode)
        stream.write(raw); stream.flush(); os.fsync(stream.fileno())


def atomic_state(job, state):
    target = job / ("journal-" + uuid.uuid4().hex + ".tmp")
    write_new(target, encode(state))
    os.replace(target, job / "journal.json")
    fd = os.open(job, os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def private_parent(file):
    file = Path(file)
    require(file.is_absolute() and file.parent.resolve() == file.parent and file.parent.is_dir()
            and not file.exists() and not file.is_symlink(), "NEW_PRIVATE_PATH_REQUIRED")
    info = file.parent.stat()
    require(info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == 0o700
            and not any((p / ".git").exists() for p in (file.parent, *file.parent.parents)), "PRIVATE_PARENT_REQUIRED")
    require(re.fullmatch("[A-Za-z0-9_.-]{1,100}", file.name), "PRIVATE_NAME_INVALID")
    return file


def request_spec(raw, require_future=True):
    require(isinstance(raw, dict) and raw.get("operation") in OPS, "OPERATION_NOT_SUPPORTED")
    operation = raw["operation"]
    required = {"operation", "batchId"} | OPS[operation]
    require(set(raw) in (required, required | {"environment"}) and raw.get("environment", ENV) == ENV,
            "REQUEST_FIELDS_INVALID")
    uuid_value(raw["batchId"])
    if "identityId" in raw: uuid_value(raw["identityId"])
    if "inviteId" in raw: uuid_value(raw["inviteId"])
    if "credentialId" in raw: uuid_value(raw["credentialId"])
    if "allowedInstanceId" in raw:
        require(isinstance(raw["allowedInstanceId"], str) and re.fullmatch("[A-Za-z0-9_-]{1,80}", raw["allowedInstanceId"]),
                "INSTANCE_INVALID")
    if "expiresAt" in raw:
        duration = stamp(raw["expiresAt"]) - dt.datetime.now(dt.timezone.utc)
        maximum = dt.timedelta(hours=24) if operation == "driver-bootstrap" else dt.timedelta(days=30)
        require(not require_future or dt.timedelta() < duration <= maximum, "EXPIRY_INVALID")
    if operation == "create-test-cohort": require(raw["synthetic"] is True, "SYNTHETIC_REQUIRED")
    if operation == "create-authorized-cohort":
        require(raw["reasonCode"] == "AUTHORIZED_HOMOLOGATION_GROUP", "AUTHORIZATION_REASON_REQUIRED")
    if operation == "provision-service-credential": require(raw["principal"] in ROLES, "PRINCIPAL_INVALID")
    if operation == "admit-participant":
        require(isinstance(raw["phoneNumber"], str) and re.fullmatch(r"\+[1-9][0-9]{7,14}", raw["phoneNumber"])
                and raw["phoneVerification"] == "PARTICIPANT_CONFIRMED", "PARTICIPANT_CONFIRMATION_REQUIRED")
    if operation == "revoke-service-credential":
        require(isinstance(raw["reasonCode"], str) and re.fullmatch("[A-Z][A-Z0-9_]{0,63}", raw["reasonCode"]),
                "REASON_INVALID")
    return {**raw, "environment": ENV}


def validate_result(value, spec, exit_code):
    require(exit_code == 0 and value.get("status") == "COMMITTED"
            and value.get("operation") == spec["operation"] and value.get("environment") == ENV
            and value.get("batchId") == spec["batchId"], "OPERATION_NOT_CONFIRMED")
    stamp(value["serverTime"]); uuid_value(value["resourceId"])
    common = {"status", "operation", "environment", "batchId", "serverTime", "resourceId"}
    operation = spec["operation"]
    if operation == "create-test-cohort":
        fields = common; require(value["resourceId"] == spec["batchId"], "COHORT_RESULT_MISMATCH")
    elif operation == "create-authorized-cohort":
        fields = common | {"synthetic"}
        require(value["resourceId"] == spec["batchId"] and value["synthetic"] is False, "COHORT_RESULT_MISMATCH")
    elif operation == "provision-service-credential":
        fields = common | {"credentialId", "principal", "allowedInstanceId", "expiresAt", "credential"}
        require(value["resourceId"] == value["credentialId"] and value["principal"] == spec["principal"]
                and value["allowedInstanceId"] == spec["allowedInstanceId"] and instant_key(value["expiresAt"]) == instant_key(spec["expiresAt"])
                and token(value["credential"]), "CREDENTIAL_RESULT_MISMATCH")
    elif operation == "admit-participant":
        fields = common | {"identityId", "allowedInstanceId", "state"}
        require(value["resourceId"] == value["identityId"] == spec["identityId"]
                and value["allowedInstanceId"] == spec["allowedInstanceId"] and value["state"] == "ADMITTED",
                "PARTICIPANT_RESULT_MISMATCH")
    elif operation == "driver-bootstrap":
        fields = common | {"identityId", "driverInviteCode", "expiresAt"}
        require(value["resourceId"] == spec["inviteId"] and value["identityId"] == spec["identityId"]
                and instant_key(value["expiresAt"]) == instant_key(spec["expiresAt"]) and token(value["driverInviteCode"]),
                "INVITE_RESULT_MISMATCH")
    else:
        fields = common | {"state"}
        require(value["resourceId"] == spec["credentialId"] and value["state"] == "REVOKED", "REVOCATION_RESULT_MISMATCH")
    require(set(value) == fields, "RESULT_FIELDS_INVALID")


class Docker:
    def __init__(self):
        require(not os.environ.get("DOCKER_HOST") and not os.environ.get("DOCKER_CONTEXT"), "LOCAL_DOCKER_ONLY")
        self.env = {k: v for k, v in os.environ.items() if k not in
                    ("DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_TLS_VERIFY", "DOCKER_CERT_PATH")}
        result = subprocess.run(["docker", "context", "inspect", "--format", "{{json .Endpoints.docker}}"],
                                capture_output=True, timeout=20, env=self.env)
        require(result.returncode == 0 and len(result.stdout) < 16000, "DOCKER_CONTEXT_FAILED")
        self.endpoint = parse(result.stdout)["Host"]
        uri = urlsplit(self.endpoint)
        require(uri.scheme == "unix" and not uri.netloc and uri.path.startswith("/")
                and not uri.query and not uri.fragment, "LOCAL_DOCKER_ONLY")

    def call(self, args, data=None, allow_failure=False, maximum=1000000):
        result = subprocess.run(["docker", "--host", self.endpoint, *args], input=data,
                                capture_output=True, timeout=40, env=self.env)
        require(len(result.stdout) <= maximum, "DOCKER_OUTPUT_LIMIT")
        if result.returncode and not allow_failure:
            action = "_".join(re.sub("[^A-Z0-9]", "_", part.upper()) for part in args[:2])
            raise Blocked("DOCKER_" + action + "_FAILED")
        return result if allow_failure else result.stdout


def selected_json(docker, args):
    return parse(docker.call(args))


def preflight(args, docker):
    require(os.getuid() == 1000 and socket.gethostname().split(".")[0] == "srv1397065", "VPS_OPERATOR_IDENTITY_REQUIRED")
    require(docker.call(["info", "--format", "{{.Name}}|{{.Swarm.LocalNodeState}}|{{.Swarm.ControlAvailable}}"]).decode().strip()
            == "srv1397065|active|true", "TARGET_MANAGER_REQUIRED")
    require(len(docker.call(["node", "ls", "--format", "{{.ID}}"]).decode().splitlines()) == 1, "SINGLE_NODE_REQUIRED")
    require(b"--limit-pids" in docker.call(["service", "create", "--help"]), "DOCKER_SERVICE_PIDS_LIMIT_UNSUPPORTED")
    online_raw, operator_raw = read(args.online_manifest), read(args.operator_manifest)
    require(sha(online_raw) == hash_value(args.online_manifest_sha256)
            and sha(operator_raw) == hash_value(args.operator_manifest_sha256), "MANIFEST_CHANGED")
    online, operator = parse(online_raw), parse(operator_raw)
    require(online.get("profile") == "ONLINE_PREVIEW_FROM_LOCAL_IMAGE" and online.get("environment") == ENV
            and online.get("stack") == "vanrides-hml-api", "ONLINE_MANIFEST_INVALID")
    require(operator.get("schemaVersion") == 1 and operator.get("status") == "BUILT_FOCUSED_TESTS_ONLY"
            and operator.get("javaClassMajor") == 61 and operator.get("schemaMigration") == "UNCHANGED_V38"
            and operator.get("fullGate") == "NOT_RUN", "OPERATOR_MANIFEST_INVALID")
    require(sha(json.dumps(operator["sourceFiles"], sort_keys=True, separators=(",", ":")).encode())
            == operator["sourceSha256"], "OPERATOR_SOURCE_FINGERPRINT_CHANGED")
    jar = read(args.jar, 128 * 1024**2)
    require(sha(jar) == hash_value(operator["jarSha256"]) and len(jar) == operator["jarBytes"], "OPERATOR_JAR_CHANGED")
    stack_file = Path(args.online_manifest).parent / "stack.online.json"
    stack_raw = read(stack_file)
    require(sha(stack_raw) == online["files"]["stack.online.json"]["sha256"], "APPROVED_STACK_CHANGED")
    api_spec = parse(stack_raw)["services"]["api"]
    env = api_spec["environment"]
    require(env["WA_ENVIRONMENT"] == ENV and env["SPRING_PROFILES_ACTIVE"] == "homolog"
            and env["WA_MVP_ONLINE"] == "true" and env["WA_TEAM_API_ONLY"] == "false"
            and env["VANRIDES_EMAIL_PROVIDER"] == "disabled", "ONLINE_ENVIRONMENT_INVALID")
    require(all(name not in env or not env[name] for name in SECRET_ENV), "PLAINTEXT_SECRET_ENV_FORBIDDEN")
    actual = selected_json(docker, ["service", "inspect", "--format",
        '{"image":{{json .Spec.TaskTemplate.ContainerSpec.Image}},"secrets":{{json .Spec.TaskTemplate.ContainerSpec.Secrets}}}', API])
    image = actual["image"]
    require(":" in image and not image.startswith("sha256:"), "NOMINAL_IMAGE_REQUIRED")
    image_info = selected_json(docker, ["image", "inspect", "--format",
        '{"id":{{json .Id}},"os":{{json .Os}},"arch":{{json .Architecture}}}', image])
    require(image_info["id"] == online["apiImage"]["imageId"] and image_info["os"] == "linux"
            and image_info["arch"] == "amd64", "PUBLISHED_RUNTIME_IMAGE_CHANGED")
    containers = docker.call(["ps", "--no-trunc", "--filter", "label=com.docker.swarm.service.name=" + API,
                              "--format", "{{.ID}}"]).decode().splitlines()
    require(len(containers) == 1 and re.fullmatch("[a-f0-9]{64}", containers[0]), "EXACT_API_CONTAINER_REQUIRED")
    require(docker.call(["inspect", "--format", "{{.Image}}", containers[0]]).decode().strip()
            == image_info["id"], "API_RUNNING_IMAGE_CHANGED")
    network = selected_json(docker, ["network", "inspect", "--format",
        '{"id":{{json .Id}},"name":{{json .Name}},"scope":{{json .Scope}}}', NETWORK])
    require(network["name"] == NETWORK and network["scope"] == "swarm", "PRIVATE_NETWORK_REQUIRED")
    secrets = []
    for variable in SECRET_ENV:
        target = env.get(variable + "_FILE", "")
        require(re.fullmatch("/run/secrets/[a-z0-9_]+", target), "SECRET_FILE_MAPPING_INVALID")
        basename = target.rsplit("/", 1)[1]
        rows = [row for row in actual["secrets"] if row.get("File", {}).get("Name") in (basename, target)]
        require(len(rows) == 1, "API_SECRET_MAPPING_CHANGED")
        row = rows[0]
        require(re.fullmatch("vanrides-hml-api-[a-z0-9-]+-v[1-9][0-9]*", row["SecretName"]), "UNRELATED_SECRET_FORBIDDEN")
        require(docker.call(["secret", "inspect", "--format", "{{.ID}}", row["SecretName"]]).decode().strip()
                == row["SecretID"], "API_SECRET_ID_CHANGED")
        secrets.append({"name": row["SecretName"], "id": row["SecretID"], "target": basename})
    return online, operator, jar, env, image, image_info["id"], network["id"], secrets


def launcher(tmp):
    # Only source code/paths enter argv; request and credentials enter through files/stdin.
    return r"""
set -eu
umask 077
mkdir -m 700 __TMP__
cd __TMP__
MAIN_PID=$$
JAVA_PID=
stop_owned() {
  if [ -n "$JAVA_PID" ]; then kill -TERM "$JAVA_PID" 2>/dev/null || true; fi
  exit 124
}
trap stop_owned TERM INT
(sleep "$OPERATOR_DEADLINE_SECONDS"; kill -TERM "$MAIN_PID" 2>/dev/null || true; sleep 10; kill -KILL "$MAIN_PID" 2>/dev/null || true) &
printf '%s  /run/operator/backend.jar\n' "$OPERATOR_JAR_SHA256" | sha256sum -c - >/dev/null 2>&1
for key in SPRING_DATASOURCE_PASSWORD VANRIDES_JWT_SECRET WA_SECRET_HASH_KEY WA_RESPONSE_ENCRYPTION_KEY WA_OPERATOR_CREDENTIAL; do
  file_key=$key"_FILE"
  file=$(printenv "$file_key")
  [ -f "$file" ] && [ ! -L "$file" ] && [ -r "$file" ]
  value=$(cat "$file")
  [ -n "$value" ]
  export "$key=$value"
done
printf READY > ready
while [ ! -f request-tail.json ]; do sleep 1; done
value=$WA_OPERATOR_CREDENTIAL
case "$value" in *[!A-Za-z0-9_-]*|'') exit 3 ;; esac
[ "$(printf %s "$value" | wc -c)" -eq 43 ]
{ printf '{"operatorCredential":"%s",' "$value"; cat request-tail.json; } > request.json
chmod 0600 request.json
set +e
java -Xmx384m -XX:MaxMetaspaceSize=256m -Duser.name=vanridesop -Duser.home=/tmp -jar /run/operator/backend.jar \
  --spring.main.web-application-type=none --spring.flyway.enabled=false \
  --vanrides.wa.operator.request-file=__TMP__/request.json \
  --vanrides.wa.operator.output-file=__TMP__/result.json &
JAVA_PID=$!
wait "$JAVA_PID"
code=$?
JAVA_PID=
set -e
printf '%s\n' "$code" > exit-code
while :; do sleep 1; done
""".replace("__TMP__", tmp)


def service_args(state, env, secrets):
    values = dict(env)
    values.update(SPRING_MAIN_WEB_APPLICATION_TYPE="none", SPRING_FLYWAY_ENABLED="false",
                  OPERATOR_JAR_SHA256=state["operatorJarSha256"],
                  OPERATOR_DEADLINE_SECONDS=str(state["timeoutSeconds"] + 60))
    values.pop("JAVA_OPTS", None)
    args = ["service", "create", "--detach", "--name", state["serviceName"], "--replicas", "1",
            "--restart-condition", "none", "--no-resolve-image", "--user", "10001:10001",
            "--read-only", "--no-healthcheck", "--network", state["networkId"], "--constraint", "node.hostname==srv1397065",
            "--limit-memory", "768M", "--limit-cpu", "1", "--limit-pids", "128",
            "--stop-grace-period", "10s", "--log-driver", "none", "--cap-drop", "ALL",
            "--label", OWNER + "=" + state["nonce"], "--label", "dev.vanrides.scope=" + SCOPE,
            "--label", "traefik.enable=false", "--container-label", OWNER + "=" + state["nonce"],
            "--container-label", "dev.vanrides.scope=" + SCOPE,
            "--mount", "type=tmpfs,destination=/tmp,tmpfs-size=67108864,tmpfs-mode=1777",
            "--mount", "type=bind,source=" + state["jarCopy"] + ",destination=/run/operator/backend.jar,readonly",
            "--mount", "type=bind,source=" + state["passwd"] + ",destination=/etc/passwd,readonly",
            "--mount", "type=bind,source=" + state["group"] + ",destination=/etc/group,readonly"]
    for key, value in sorted(values.items()):
        require(isinstance(value, str) and "\0" not in value and "\n" not in value, "ENVIRONMENT_INVALID")
        args += ["--env", key + "=" + value]
    for row in secrets:
        args += ["--secret", "source=" + row["name"] + ",target=" + row["target"] + ",uid=10001,gid=10001,mode=0400"]
    return [*args, "--entrypoint", "/bin/sh", state["image"], "-c", launcher(state["tmp"])]


def owned_service(docker, state, service):
    row = selected_json(docker, ["service", "inspect", "--format",
        '{"id":{{json .ID}},"name":{{json .Spec.Name}},"labels":{{json .Spec.Labels}},"image":{{json .Spec.TaskTemplate.ContainerSpec.Image}}}',
        service])
    require(re.fullmatch("[a-z0-9]{25}", row["id"]) and row["name"] == state["serviceName"] and row["labels"].get(OWNER) == state["nonce"]
            and row["labels"].get("dev.vanrides.scope") == SCOPE and row["image"] == state["image"],
            "SERVICE_NOT_OWNED")
    return row["id"]


def locate(docker, state):
    names = docker.call(["service", "ls", "--filter", "label=" + OWNER + "=" + state["nonce"],
                         "--format", "{{.Name}}"]).decode().splitlines()
    require(len(names) <= 1 and all(name == state["serviceName"] for name in names), "SERVICE_OWNER_QUERY_INVALID")
    # Docker service ls may shorten IDs even in quiet mode. Inspect by the
    # exact owned name, then validate its full ID and nonce before any removal.
    return [owned_service(docker, state, name) for name in names]


def container(docker, state):
    rows = docker.call(["ps", "--no-trunc", "--filter", "label=" + OWNER + "=" + state["nonce"],
                        "--filter", "label=com.docker.swarm.service.name=" + state["serviceName"],
                        "--format", "{{.ID}}"]).decode().splitlines()
    require(len(rows) <= 1, "OWNED_CONTAINER_NOT_UNIQUE")
    if not rows: return None
    require(re.fullmatch("[a-f0-9]{64}", rows[0]), "CONTAINER_ID_INVALID")
    value = selected_json(docker, ["inspect", "--format",
        '{"image":{{json .Image}},"user":{{json .Config.User}},"labels":{{json .Config.Labels}},"running":{{json .State.Running}}}', rows[0]])
    require(value["image"] == state["imageId"] and value["user"] == "10001:10001" and value["running"] is True
            and value["labels"].get(OWNER) == state["nonce"] and value["labels"].get("dev.vanrides.scope") == SCOPE,
            "CONTAINER_NOT_OWNED")
    return rows[0]


def cleanup(docker, state):
    previous = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGTERM, signal.SIGINT})
    try:
        for service in locate(docker, state):
            docker.call(["service", "rm", service])
        deadline = time.monotonic() + 35
        while time.monotonic() < deadline:
            if not locate(docker, state) and container(docker, state) is None:
                return
            time.sleep(1)
        raise Blocked("OWNED_SERVICE_CLEANUP_UNCONFIRMED")
    finally:
        signal.pthread_sigmask(signal.SIG_SETMASK, previous)


def capture(docker, state, job):
    ident = container(docker, state)
    if not ident: return None, None
    status = docker.call(["exec", "--user", "10001:10001", ident, "/bin/sh", "-c",
                          "if [ -f " + state["tmp"] + "/exit-code ]; then cat " + state["tmp"] + "/exit-code; fi"],
                         allow_failure=True, maximum=100)
    exit_code = int(status.stdout.strip()) if status.returncode == 0 and re.fullmatch(b"[0-9]+\n?", status.stdout) else None
    raw = docker.call(["exec", "--user", "10001:10001", ident, "/bin/sh", "-c",
                      "if [ -f " + state["tmp"] + "/result.json ]; then cat " + state["tmp"] + "/result.json; fi"],
                     allow_failure=True, maximum=32768)
    if raw.returncode == 0 and raw.stdout:
        # Private raw evidence survives PREPARED/failed/unknown outcomes, never printed.
        changed = state.get("capturedSha256") != sha(raw.stdout)
        if changed:
            destination = job / ("captured-" + uuid.uuid4().hex + ".json")
            write_new(destination, raw.stdout)
            state["capturedFile"] = destination.name
            state["capturedSha256"] = sha(raw.stdout)
        if changed or state.get("exitCode") != exit_code:
            state["exitCode"] = exit_code
            atomic_state(job, state)
        return raw.stdout, exit_code
    return None, exit_code


def execute(args):
    require(args.execute_local_vps, "EXPLICIT_VPS_EXECUTION_REQUIRED")
    require(30 <= args.timeout_seconds <= 300, "TIMEOUT_INVALID")
    result_file = private_parent(args.result)
    job = private_parent(args.job_directory)
    spec_raw = read(args.request, 16384, private=True)
    spec = request_spec(parse(spec_raw))
    docker = Docker()
    online, operator, jar, env, image, image_id, network, secrets = preflight(args, docker)
    job.mkdir(mode=0o700)
    nonce = uuid.uuid4().hex
    state = {"schemaVersion": 1, "scope": SCOPE, "nonce": nonce, "serviceName": "vanrides-hml-operator-" + nonce,
             "image": image, "imageId": image_id, "networkId": network, "tmp": "/tmp/vanrides-operator-" + nonce,
             "operatorJarSha256": operator["jarSha256"], "operatorManifestSha256": args.operator_manifest_sha256,
             "onlineManifestSha256": args.online_manifest_sha256, "timeoutSeconds": args.timeout_seconds,
             "jarCopy": str(job / "backend.jar"), "passwd": str(job / "passwd"), "group": str(job / "group"),
             "requestSha256": sha(spec_raw), "phase": "PREPARED", "resultFile": str(result_file)}
    for file in (state["jarCopy"], state["passwd"], state["group"]):
        require(re.fullmatch("[A-Za-z0-9_./-]+", file), "MOUNT_PATH_INVALID")
    write_new(job / "request.json", spec_raw)
    write_new(job / "operator-manifest.json", read(args.operator_manifest))
    write_new(job / "backend.jar", jar, 0o444)
    write_new(job / "passwd", b"root:x:0:0:root:/root:/bin/false\nvanridesop:x:10001:10001:VanRides operator:/tmp:/bin/false\n", 0o444)
    write_new(job / "group", b"root:x:0:\nvanridesop:x:10001:\n", 0o444)
    atomic_state(job, state)
    attempted = False
    confirmed = False
    raw_result = None
    old_term, old_int = signal.getsignal(signal.SIGTERM), signal.getsignal(signal.SIGINT)
    def cancelled(*_):
        raise Cancelled()
    signal.signal(signal.SIGTERM, cancelled); signal.signal(signal.SIGINT, cancelled)
    try:
        collisions = docker.call(["service", "ls", "--filter", "name=" + state["serviceName"], "--format", "{{.ID}}"])
        require(not collisions.strip(), "SERVICE_NAME_COLLISION")
        state["phase"] = "CREATING"; atomic_state(job, state); attempted = True
        docker.call(service_args(state, env, secrets))
        deadline = time.monotonic() + args.timeout_seconds
        ident = None
        while time.monotonic() < deadline:
            ids = locate(docker, state)
            if ids: ident = container(docker, state)
            if ident:
                ready = docker.call(["exec", "--user", "10001:10001", ident, "test", "-f", state["tmp"] + "/ready"],
                                    allow_failure=True, maximum=100)
                if ready.returncode == 0: break
            time.sleep(1)
        require(ident is not None and time.monotonic() < deadline, "OPERATOR_START_TIMEOUT")
        # The body is stdin only. No phone/operator token enters Docker argv or env.
        tail = json.dumps(spec, sort_keys=True, separators=(",", ":"), ensure_ascii=True)[1:].encode()
        upload = "umask 077; set -C; cat > " + state["tmp"] + "/request.new && mv " + state["tmp"] + "/request.new " + state["tmp"] + "/request-tail.json"
        state["phase"] = "SENDING_REQUEST"; atomic_state(job, state)
        docker.call(["exec", "-i", "--user", "10001:10001", ident, "/bin/sh", "-c", upload], data=tail)
        state["phase"] = "WAITING"; atomic_state(job, state)
        while time.monotonic() < deadline:
            raw_result, exit_code = capture(docker, state, job)
            if exit_code is not None:
                require(raw_result is not None, "OPERATOR_RESULT_MISSING")
                validate_result(parse(raw_result), spec, exit_code)
                confirmed = True
                break
            time.sleep(1)
        require(confirmed, "OPERATOR_RESULT_TIMEOUT_OR_UNCERTAIN")
    finally:
        try:
            if attempted and raw_result is None:
                try: capture(docker, state, job)
                except (Blocked, OSError, ValueError, subprocess.SubprocessError): pass
            if attempted: cleanup(docker, state)
            state["phase"] = "COMMITTED_AND_SERVICE_REMOVED" if confirmed else "UNCONFIRMED_SERVICE_REMOVED"
            atomic_state(job, state)
        finally:
            signal.signal(signal.SIGTERM, old_term); signal.signal(signal.SIGINT, old_int)
    require(confirmed and raw_result is not None, "OPERATOR_NOT_CONFIRMED")
    write_new(result_file, raw_result)
    return "OPERATOR_COMMITTED_SERVICE_REMOVED"


def recover(args):
    require(args.execute_local_vps and os.getuid() == 1000
            and socket.gethostname().split(".")[0] == "srv1397065", "EXPLICIT_LOCAL_VPS_RECOVERY_REQUIRED")
    job = Path(args.job_directory)
    state = parse(read(job / "journal.json", private=True))
    require(state["scope"] == SCOPE and re.fullmatch("[a-f0-9]{32}", state["nonce"])
            and state["serviceName"] == "vanrides-hml-operator-" + state["nonce"]
            and state["tmp"] == "/tmp/vanrides-operator-" + state["nonce"], "OWNERSHIP_JOURNAL_INVALID")
    request_raw = read(job / "request.json", 16384, private=True)
    require(sha(request_raw) == state["requestSha256"], "RECOVERY_REQUEST_CHANGED")
    # Recovery never re-executes a request; an expired original input is valid evidence.
    spec = request_spec(parse(request_raw), require_future=False)
    docker = Docker()
    raw, exit_code = None, None
    try:
        if locate(docker, state): raw, exit_code = capture(docker, state, job)
    finally:
        cleanup(docker, state)
    if raw is None and state.get("capturedFile"):
        require(re.fullmatch(r"captured-[a-f0-9]{32}\.json", state["capturedFile"]), "CAPTURE_FILE_INVALID")
        raw = read(job / state["capturedFile"], 32768, private=True)
        require(sha(raw) == state["capturedSha256"], "CAPTURE_CHANGED")
        exit_code = state.get("exitCode")
    if raw is not None:
        validate_result(parse(raw), spec, exit_code)
        result_file = private_parent(state["resultFile"])
        write_new(result_file, raw)
        state["phase"] = "RECOVERED_COMMITTED_SERVICE_REMOVED"; atomic_state(job, state)
        return "OPERATOR_RECOVERED_COMMITTED"
    state["phase"] = "RECOVERED_WITHOUT_COMMIT_PROOF"; atomic_state(job, state)
    return "OPERATOR_RECOVERED_WITHOUT_COMMIT_PROOF"


def self_test():
    import tempfile
    import unittest
    class ContractTests(unittest.TestCase):
        def test_bind_permissions_survive_private_umask(self):
            with tempfile.TemporaryDirectory() as directory:
                for mode in (0o444, 0o600):
                    target = Path(directory) / str(mode)
                    write_new(target, b"synthetic", mode)
                    self.assertEqual(stat.S_IMODE(target.stat().st_mode), mode)
        def test_java_nanoseconds_are_preserved_in_result_comparison(self):
            self.assertEqual(instant_key("2026-11-04T00:00:00.123456789Z"), "2026-11-04T00:00:00.123456789Z")
            self.assertEqual(instant_key("2026-11-04T00:00:00Z"), instant_key("2026-11-04T00:00:00.000Z"))
            self.assertNotEqual(instant_key("2026-11-04T00:00:00.123456788Z"), instant_key("2026-11-04T00:00:00.123456789Z"))
        def test_expired_request_can_be_reconciled_but_never_sent(self):
            request = {"operation": "driver-bootstrap", "batchId": str(uuid.uuid4()),
                       "identityId": str(uuid.uuid4()), "inviteId": str(uuid.uuid4()), "expiresAt": "2000-01-01T00:00:00Z"}
            with self.assertRaises(Blocked): request_spec(request)
            self.assertEqual(request_spec(request, require_future=False)["environment"], ENV)
        def test_request_never_accepts_operator_secret_or_unconfirmed_phone(self):
            request = {"operation": "admit-participant", "batchId": str(uuid.uuid4()),
                       "identityId": str(uuid.uuid4()), "allowedInstanceId": "vanrides-hml",
                       "phoneNumber": "+15550001111", "phoneVerification": "PARTICIPANT_CONFIRMED"}
            self.assertEqual(request_spec(request)["environment"], ENV)
            for patch in ({"operatorCredential": "SENTINEL"}, {"phoneNumber": "15550001111"},
                          {"phoneVerification": "UNVERIFIED"}, {"extra": True}):
                with self.assertRaises(Blocked): request_spec({**request, **patch})
        def test_result_requires_exit_zero_committed_and_same_ids(self):
            request = request_spec({"operation": "create-authorized-cohort", "batchId": str(uuid.uuid4()),
                                    "reasonCode": "AUTHORIZED_HOMOLOGATION_GROUP"})
            value = {"status": "COMMITTED", "operation": request["operation"], "environment": ENV,
                     "batchId": request["batchId"], "serverTime": "2026-11-04T00:00:00Z",
                     "resourceId": request["batchId"], "synthetic": False}
            validate_result(value, request, 0)
            for changed, code in (({**value, "status": "PREPARED"}, 0), (value, 1),
                                  ({**value, "resourceId": str(uuid.uuid4())}, 0)):
                with self.assertRaises(Blocked): validate_result(changed, request, code)
        def test_service_argv_has_no_request_phone_secret_or_public_port(self):
            state = {"nonce": "a"*32, "serviceName": "vanrides-hml-operator-"+"a"*32, "networkId": "private-id",
                     "operatorJarSha256": "b"*64, "timeoutSeconds": 180, "jarCopy": "/private/job/backend.jar",
                     "passwd": "/private/job/passwd", "group": "/private/job/group",
                     "image": "local/approved:version", "tmp": "/tmp/vanrides-operator-"+"a"*32}
            args = service_args(state, {"WA_ENVIRONMENT": ENV, "WA_OPERATOR_CREDENTIAL_FILE": "/run/secrets/wa_operator"},
                                [{"name": "vanrides-hml-api-wa-operator-v1", "target": "wa_operator"}])
            self.assertNotIn("--publish", args); self.assertNotIn("--with-registry-auth", args)
            self.assertIn("10001:10001", args); self.assertIn("none", args)
            self.assertNotIn("+15550001111", " ".join(args)); self.assertNotIn("SENTINEL", " ".join(args))
            self.assertIn("--spring.main.web-application-type=none", " ".join(args))
    result = unittest.TextTestRunner(verbosity=1).run(unittest.defaultTestLoader.loadTestsFromTestCase(ContractTests))
    return 0 if result.wasSuccessful() else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("self-test")
    p = commands.add_parser("run")
    p.add_argument("--execute-local-vps", action="store_true")
    for name in ("online-manifest", "online-manifest-sha256", "operator-manifest", "operator-manifest-sha256",
                 "jar", "request", "result", "job-directory"):
        p.add_argument("--" + name, required=True)
    p.add_argument("--timeout-seconds", type=int, default=180)
    p = commands.add_parser("recover")
    p.add_argument("--execute-local-vps", action="store_true")
    p.add_argument("--job-directory", required=True)
    args = parser.parse_args()
    os.umask(0o077)
    if args.command == "self-test": return self_test()
    try:
        result = execute(args) if args.command == "run" else recover(args)
        print(result)
        return 0
    except (Cancelled, KeyboardInterrupt):
        print("OPERATOR_CANCELLED_COMMIT_NOT_ASSUMED", file=sys.stderr)
        return 130
    except Blocked as failure:
        code = str(failure)
        if not re.fullmatch("[A-Z][A-Z0-9_]{0,119}", code): code = "BLOCKED"
        print("OPERATOR_NOT_CONFIRMED:" + code + ";KEEP_PRIVATE_JOB_FOR_RECONCILIATION", file=sys.stderr)
        return 2
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as failure:
        code = "DOCKER_TIMEOUT" if isinstance(failure, subprocess.TimeoutExpired) else type(failure).__name__.upper()
        print("OPERATOR_NOT_CONFIRMED:" + code + ";KEEP_PRIVATE_JOB_FOR_RECONCILIATION", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

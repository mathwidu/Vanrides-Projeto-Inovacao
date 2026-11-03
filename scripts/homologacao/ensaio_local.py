#!/usr/bin/env python3
"""Exercita o runner e o perfil homolog com um JAR conferido e um PostgreSQL descartável."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import subprocess
import tempfile
import time
import uuid

from testar_api import CheckFailed, ENVIRONMENT, HOST, PREFIX, check_destination, require, run_scenario

ROOT = Path(__file__).resolve().parents[2]
PG_IMAGE = "postgres@sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28"
DB = "vanrides_hml_ensaio"


def output(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.DEVNULL, timeout=120).strip()


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def source_check(jar, expected_sha, expected_digest):
    require(output("git", "-C", str(ROOT), "rev-parse", "HEAD") == expected_sha and
            not output("git", "-C", str(ROOT), "status", "--porcelain"), "Checkout divergente.")
    require(not jar.is_symlink() and jar.is_file() and digest(jar) == expected_digest, "JAR divergente.")


def utc(seconds=0):
    return (datetime.now(timezone.utc) + timedelta(seconds=seconds)).isoformat()


@contextmanager
def database(work):
    # Nunca usar contexto TCP/SSH nem aceitar um banco indicado pelo usuário.
    require(not any(os.environ.get(k) for k in ("DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_TLS_VERIFY",
                                                 "DOCKER_CERT_PATH")), "Override Docker não permitido.")
    context = output("docker", "context", "show")
    endpoint = output("docker", "context", "inspect", context, "--format", "{{.Endpoints.docker.Host}}")
    require(endpoint.startswith("unix:///"), "Docker local via socket Unix é obrigatório.")
    docker = ["docker", "--host", endpoint]
    owner = str(uuid.uuid4())
    name = "vanrides-hml-" + owner
    cidfile = work / "pg.cid"
    password = secrets.token_urlsafe(32)
    file = work / "postgres.env"
    file.write_text("POSTGRES_USER=%s\nPOSTGRES_DB=%s\nPOSTGRES_PASSWORD=%s\n" % (DB, DB, password))
    def assert_owner(cid):
        require(re.fullmatch("[a-f0-9]{64}", cid) and
                output(*docker, "inspect", "--format", '{{index .Config.Labels "vanrides.hml.owner"}}', cid) == owner,
                "Contêiner sem a identificação desta rodada.")
    try:
        output(*docker, "run", "--detach", "--cidfile", str(cidfile), "--platform", "linux/amd64",
               "--name", name, "--memory", "512m", "--cpus", "1.5", "--label", "vanrides.hml.owner=" + owner, "--env-file", str(file),
               "--publish", "127.0.0.1::5432", PG_IMAGE)
        cid = cidfile.read_text().strip()
        assert_owner(cid)
        binding = output(*docker, "port", cid, "5432/tcp")
        require(re.fullmatch(r"127\.0\.0\.1:[1-9][0-9]{0,4}", binding), "Banco fora de loopback.")
        for _ in range(90):
            try:
                output(*docker, "exec", cid, "pg_isready", "-h", "127.0.0.1", "-U", DB, "-d", DB)
                break
            except subprocess.CalledProcessError:
                time.sleep(0.5)
        else:
            raise CheckFailed("O banco de ensaio não iniciou.")
        yield int(binding.split(":")[1]), password, [*docker, "exec", cid, "psql", "-v", "ON_ERROR_STOP=1", "-U", DB, "-d", DB, "-Atc"]
    finally:
        owned = output(*docker, "ps", "-aq", "--no-trunc", "--filter", "label=vanrides.hml.owner=" + owner).splitlines()
        require(len(owned) <= 1, "Mais de um recurso reivindicou o identificador do ensaio.")
        for cid in owned:
            assert_owner(cid)
            output(*docker, "rm", "--force", "--volumes", cid)


def configuration(work, port, password):
    settings = {
        "spring.profiles.active": "homolog", "server.address": "127.0.0.1", "server.port": "0",
        "spring.datasource.url": "jdbc:postgresql://127.0.0.1:%d/%s" % (port, DB),
        "spring.datasource.username": DB, "spring.datasource.password": password,
        "spring.jpa.hibernate.ddl-auto": "validate", "spring.jpa.show-sql": "false",
        "vanrides.wa.enabled": "true", "vanrides.wa.team-api-only": "true",
        "vanrides.wa.new-starts-enabled": "false", "vanrides.wa.approach-enabled": "false",
        "vanrides.wa.test-cohort-only": "true", "vanrides.wa.environment": ENVIRONMENT,
        "vanrides.wa.public-base-url": "https://" + HOST, "vanrides.wa.hash-key": secrets.token_urlsafe(32),
        "vanrides.wa.response-key": secrets.token_urlsafe(32), "vanrides.wa.operator-credential": secrets.token_urlsafe(32),
        "vanrides.wa.operator-expires-at": utc(3600), "vanrides.auth.basic-enabled": "false",
        "vanrides.auth.jwt-secret": secrets.token_urlsafe(32), "vanrides.email.provider": "disabled",
        "vanrides.email.verification.public-base-url": "", "management.endpoints.web.exposure.include": "health",
        "vanrides.rate-limit.enabled": "true", "vanrides.routing.provider": "static",
    }
    config = work / "application.properties"
    config.write_text("".join(k + "=" + v + "\n" for k, v in settings.items()))
    return config, settings["vanrides.wa.operator-credential"]


def operator(work, command, env, token, batch, operation, **fields):
    ref = str(uuid.uuid4())
    req, res = work / (ref + ".request"), work / (ref + ".result")
    req.write_text(json.dumps(dict(fields, environment=ENVIRONMENT, operatorCredential=token,
                                   batchId=batch, operation=operation)))
    try:
        with (work / "operator.log").open("ab") as log:
            proc = subprocess.run([*command, "--spring.main.web-application-type=none",
                                   "--vanrides.wa.operator.request-file=" + str(req),
                                   "--vanrides.wa.operator.output-file=" + str(res)],
                                  env=env, cwd=work, stdout=log, stderr=log, timeout=65)
        require(proc.returncode == 0 and res.exists(), "Operador recusou " + operation + ".")
        result = json.loads(res.read_text())
        require(result.get("status") == "COMMITTED" and result.get("batchId") == batch and
                result.get("operation") == operation, "Recibo de operador inválido.")
        return result
    finally:
        req.unlink(missing_ok=True)
        res.unlink(missing_ok=True)


@contextmanager
def server(work, command, env):
    path = work / ("api-" + str(uuid.uuid4()) + ".log")
    with path.open("wb") as log:
        proc = subprocess.Popen(command, env=env, cwd=work, stdout=log, stderr=log)
    try:
        for _ in range(240):
            require(proc.poll() is None, "A API encerrou durante o startup da homologação.")
            match = re.search(r"Tomcat started on port (\d+) \(http\)", path.read_text(errors="replace"))
            if match:
                port = int(match.group(1))
                listeners = output("lsof", "-nP", "-a", "-p", str(proc.pid), "-iTCP:%d" % port,
                                   "-sTCP:LISTEN", "-Fpn").splitlines()
                require("p%d" % proc.pid in listeners and "n127.0.0.1:%d" % port in listeners,
                        "O processo do teste não possui o listener esperado.")
                break
            time.sleep(0.5)
        else:
            raise CheckFailed("Timeout de inicialização.")
        def send(method, path, body=None, headers=None, timeout=7):
            require(proc.poll() is None, "Processo da API interrompido.")
            conn = http.client.HTTPConnection("127.0.0.1", port, timeout=timeout)
            try:
                conn.request(method, path, body=json.dumps(body).encode() if body is not None else None,
                             headers={"Content-Type": "application/json", **(headers or {})})
                rsp = conn.getresponse()
                raw = rsp.read(65537)
                require(len(raw) <= 65536, "Resposta de ensaio excedeu o limite.")
                return rsp.status, json.loads(raw) if raw else {}, rsp.getheader("X-VanRides-Environment")
            finally:
                conn.close()
        yield send
    finally:
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=30)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()
                raise CheckFailed("Foi necessário forçar a parada da API do ensaio.")


def exercise(work, jar, db):
    port, password, psql = db
    config, operator_token = configuration(work, port, password)
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else shutil.which("java")
    base = [java, "-Xms24m", "-XX:+UseSerialGC", "-XX:ActiveProcessorCount=2"]
    server_cmd = [*base, "-Xmx512m", "-jar", str(jar)]
    op_cmd = [*base, "-Xmx192m", "-jar", str(jar)]
    env = {k: os.environ[k] for k in ("PATH", "HOME", "JAVA_HOME", "LANG", "TMPDIR") if k in os.environ}
    env["SPRING_CONFIG_IMPORT"] = "file:" + str(config) + "[.properties]"
    def op(batch, operation, **fields):
        return operator(work, op_cmd, env, operator_token, batch, operation, **fields)
    with server(work, server_cmd, env) as send:
        check_destination(send)
        for method, path in [("GET", "/motoristas"), ("GET", "/driver-app/v1/status"),
                             ("POST", PREFIX + "/events"), ("GET", "/actuator/env")]:
            require(send(method, path)[0] in (403, 404), "Rota externa à finalidade está exposta.")
        members = []
        for _ in range(2):
            batch = str(uuid.uuid4())
            op(batch, "create-test-cohort", synthetic=True)
            member = op(batch, "provision-service-credential", principal="CONVERSATION_WORKER",
                        allowedInstanceId="team_hml_" + batch.replace("-", ""), expiresAt=utc(1800))
            members.append((batch, member))
        (batch, a), (_, b) = members
        event, actor = str(uuid.uuid4()), str(uuid.uuid4())
        seed = op(batch, "seed-test-data", identityId=actor, inboxEventId=event, messageText="oi",
                  workerCredentialId=a["credentialId"], leaseExpiresAt=utc(45))
        b_auth = {"Authorization": "Bearer " + b["credential"]}
        status, _, _ = send("GET", PREFIX + "/context?inboxEventId=" + event,
                            headers={**b_auth, "X-Inbox-Lease": seed["leaseToken"]})
        require(status in (401, 403, 404, 409), "Credencial de outra instância acessou o contexto.")
        expiry = datetime.fromisoformat(seed["leaseExpiresAt"].replace("Z", "+00:00")).timestamp()
        time.sleep(max(0, expiry - time.time() + 0.2))
        status, claim, _ = send("POST", PREFIX + "/inbox/claim", {"batchLimit": 1, "leaseSeconds": 60}, b_auth)
        require(status == 200 and claim.get("items") == [], "A fixture ficou visível na outra instância.")
        run_scenario(send, a["credential"], event)
        op(batch, "revoke-service-credential", credentialId=a["credentialId"], reasonCode="TESTE_CONCLUIDO")
        status, _, _ = send("POST", PREFIX + "/inbox/claim", {"batchLimit": 1, "leaseSeconds": 60},
                            {"Authorization": "Bearer " + a["credential"]})
        require(status == 401, "A credencial revogada ainda tem acesso.")
    with server(work, server_cmd, env) as send:
        check_destination(send)
        # Identificador sintético gerado neste processo, nunca lido de entrada externa.
        require(output(*psql, "SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id='%s' AND command_type='START_ONBOARDING'" % event) == "1",
                "O comando repetido não deixou um único recibo.")
        require(output(*psql, "SELECT count(*) FROM wa.profile WHERE identity_id='%s' AND status='ONBOARDING'" % actor) == "1",
                "O perfil do teste não persistiu após reiniciar a API.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--sha-fonte", required=True)
    parser.add_argument("--sha256-jar", required=True)
    args = parser.parse_args()
    os.umask(0o077)
    jar = args.jar.absolute()
    def interrupted(_signal, _frame):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, interrupted)
    source_check(jar, args.sha_fonte, args.sha256_jar)
    with tempfile.TemporaryDirectory(prefix="vanrides-hml-") as temp:
        work = Path(temp).resolve()
        with database(work) as db:
            exercise(work, jar, db)
    source_check(jar, args.sha_fonte, args.sha256_jar)
    print(json.dumps({"status": "PASS", "sourceSha": args.sha_fonte, "jarSha256": args.sha256_jar,
                      "transport": "loopback HTTP", "realWhatsApp": False, "cleanup": "completed"}))


if __name__ == "__main__":
    try:
        main()
    except (CheckFailed, KeyboardInterrupt, subprocess.SubprocessError, OSError, ValueError, KeyError) as exc:
        print("FAIL —", str(exc) if isinstance(exc, CheckFailed) else "ensaio incompleto: " + type(exc).__name__)
        raise SystemExit(1)

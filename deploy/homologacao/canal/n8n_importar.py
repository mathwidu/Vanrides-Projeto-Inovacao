#!/usr/bin/env python3
"""Operador HML no host da VPS. Padrão só leitura; --apply executa uma etapa explícita."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import stat
import subprocess
import time

IMAGE = "docker.n8n.io/n8nio/n8n:2.45.4@sha256:80e23ced901f0e22597d4f3464e3aaf9d835372262ea6f08ef760d8ca35df25c"
SCHEDULES = {"W01", "W04", "W06", "W08"}
ORDER = ["W07", "W02", "W03", "W04", "W05", "W08", "W06", "W01"]


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()


def require(ok, code):
    if not ok:
        raise RuntimeError(code)


def run(command, data=None):
    result = subprocess.run(command, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=180)
    require(result.returncode == 0, "COMMAND_FAILED_" + command[0].upper())
    return result.stdout


def container(service):
    values = run(["docker", "ps", "-q", "--filter", "label=com.docker.swarm.service.name=" + service]).decode().split()
    require(len(values) == 1 and re.fullmatch(r"[a-f0-9]{12,64}", values[0]), "EXPECTED_ONE_SERVICE_CONTAINER")
    return values[0]


def metadata(pg, project, credential):
    # Read metadata only: no email, password, credential data, cookies or API keys.
    sql = """BEGIN READ ONLY;
    SELECT json_build_object(
      'owner', (SELECT json_build_object('userId',u.id,'projectId',p.id) FROM "user" u
        JOIN project_relation r ON r."userId"=u.id JOIN project p ON p.id=r."projectId"
        WHERE u."roleSlug"='global:owner' AND p.type='personal' AND p.id='%s'),
      'credential', (SELECT json_build_object('id',c.id,'type',c.type,'projects',
        (SELECT json_agg(s."projectId") FROM shared_credentials s WHERE s."credentialsId"=c.id AND s.role='credential:owner'))
        FROM credentials_entity c WHERE c.id='%s'),
      'workflows', (SELECT COALESCE(json_agg(json_build_object('id',w.id,'active',w.active,'versionId',w."versionId",
        'activeVersionId',w."activeVersionId",'projects',(SELECT json_agg(s."projectId") FROM shared_workflow s
          WHERE s."workflowId"=w.id AND s.role='workflow:owner')) ORDER BY w.id),'[]'::json) FROM workflow_entity w));
    ROLLBACK;""" % (project, credential)
    return json.loads(run(["docker", "exec", "-i", "-u", "postgres", pg, "psql", "-XqtA", "-v", "ON_ERROR_STOP=1", "-d", "n8n"], sql.encode()))


def save(path, value):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary = path.with_name(path.name + "." + secrets.token_hex(4))
    fd = os.open(temporary, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, "w") as output:
        json.dump(value, output, indent=2)
        output.write("\n")
    os.replace(temporary, path)


def cli_import(editor, project, payload, kind, active=False):
    filename = "/tmp/vanrides-hml-" + secrets.token_hex(8) + ".json"
    create = "const fs=require('node:fs');const p=process.argv[1];const b=fs.readFileSync(0);const fd=fs.openSync(p,'wx',0o600);fs.writeFileSync(fd,b);fs.closeSync(fd);"
    try:
        run(["docker", "exec", "-i", "-u", "node", editor, "node", "-e", create, filename], encoded(payload))
        command = ["docker", "exec", "-u", "node", editor, "n8n", "import:" + kind,
                   "--input=" + filename, "--projectId=" + project]
        if kind == "workflow":
            command.append("--activeState=" + ("fromJson" if active else "false"))
        run(command)
    finally:
        run(["docker", "exec", "-u", "node", editor, "node", "-e",
             "require('node:fs').rmSync(process.argv[1],{force:true})", filename])


def execute(args):
    require(re.fullmatch(r"[A-Za-z0-9_-]{1,64}", args.project_id), "PROJECT_ID_INVALID")
    blob = (args.bundle / "manifest.json").read_bytes()
    manifest_sha = hashlib.sha256(blob).hexdigest()
    require(manifest_sha == args.manifest_sha256, "MANIFEST_DIGEST_CHANGED")
    manifest = json.loads(blob)
    require(re.fullmatch(r"vrHml[0-9a-f]{12}", manifest["namespace"]), "HML_NAMESPACE_REQUIRED")
    require(manifest["importOrder"] == ORDER and len(manifest["workflows"]) == 8, "EIGHT_WORKFLOWS_REQUIRED")
    credential = manifest["credentials"][0]
    require(credential["type"] == "httpHeaderAuth" and re.fullmatch(r"[A-Za-z0-9_-]{1,64}", credential["id"]), "CREDENTIAL_REFERENCE_INVALID")
    flows = {}
    for row in manifest["workflows"]:
        logical = row["logicalId"]
        require(logical in ORDER and row["file"] == logical + ".json", "WORKFLOW_FILE_INVALID")
        raw = (args.bundle / row["file"]).read_bytes()
        require(hashlib.sha256(raw).hexdigest() == row["sha256"], "WORKFLOW_DIGEST_CHANGED")
        flow = json.loads(raw)
        require(flow["id"] == manifest["namespace"] + logical and flow["active"] is False, "WORKFLOW_SCOPE_INVALID")
        flows[logical] = flow
    # Runtime checks error handlers' caller policy. Permit only this delivery's seven parents.
    flows["W07"]["settings"]["callerPolicy"] = "workflowsFromAList"
    flows["W07"]["settings"]["callerIds"] = ",".join(flows[key]["id"] for key in ORDER if key != "W07")
    ids = {flow["id"] for flow in flows.values()}
    editor, pg = container("n8n_editor"), container("postgres_postgres")
    require(run(["docker", "service", "inspect", "n8n_editor", "--format", "{{.Spec.TaskTemplate.ContainerSpec.Image}}"]).decode().strip() == IMAGE, "N8N_IMAGE_CHANGED")
    require(run(["docker", "exec", "-u", "node", editor, "node", "-p", "process.env.EXECUTIONS_MODE"]).decode().strip() == "queue", "QUEUE_MODE_REQUIRED")
    before = metadata(pg, args.project_id, credential["id"])
    require(before["owner"] is not None, "OWNER_PERSONAL_PROJECT_REQUIRED")
    found = {row["id"]: row for row in before["workflows"] if row["id"] in ids}
    report = {"stage": args.stage, "projectId": args.project_id, "ownerId": before["owner"]["userId"],
              "manifestSha256": manifest_sha, "namespace": manifest["namespace"], "credentialId": credential["id"],
              "workflowIds": sorted(ids), "existingOwnedWorkflows": len(found), "credentialExists": before["credential"] is not None,
              "approvedChannelSource": "c7cbe3d", "appliedInactiveBundleSha256": hashlib.sha256(encoded([flows[key] for key in ORDER])).hexdigest(),
              "derivedChange": "W07 callerIds restricted to this delivery's seven parent workflows; no other workflow setting changed"}
    if not args.apply or args.stage == "status":
        print(json.dumps(dict(report, status="READ_ONLY_CHECK")))
        return
    if args.stage == "importar":
        require(not args.receipt.exists() and not found and before["credential"] is None, "NEW_IMPORT_ONLY_USE_STATUS_TO_RECONCILE")
        require(args.control_file is not None, "CONTROL_FILE_REQUIRED")
        info = args.control_file.lstat()
        require(stat.S_ISREG(info.st_mode) and stat.S_IMODE(info.st_mode) == 0o600 and info.st_uid == os.getuid(), "PRIVATE_CONTROL_FILE_REQUIRED")
        token = args.control_file.read_bytes().strip()
        require(re.fullmatch(rb"[A-Za-z0-9_-]{43}", token), "CONTROL_TOKEN_INVALID")
        report["status"] = "PREPARED"
        save(args.receipt, report)
        cli_import(editor, args.project_id, [{"id": credential["id"], "name": credential["name"], "type": "httpHeaderAuth",
                   "data": {"name": "Authorization", "value": "Bearer " + token.decode()}}], "credentials")
        report["status"] = "CREDENTIAL_IMPORTED"
        save(args.receipt, report)
        cli_import(editor, args.project_id, [flows[key] for key in ORDER], "workflow")
        expected_status = "IMPORTED_INACTIVE"
    else:
        receipt = json.loads(args.receipt.read_text())
        require(receipt["manifestSha256"] == manifest_sha and receipt["projectId"] == args.project_id
                and receipt["credentialId"] == credential["id"] and set(receipt["workflowIds"]) == ids, "RECEIPT_SCOPE_MISMATCH")
        require(len(found) == 8 and all(args.project_id in row["projects"] for row in found.values()), "OWNED_PROJECT_WORKFLOWS_REQUIRED")
        require(before["credential"] is not None and before["credential"]["type"] == "httpHeaderAuth"
                and args.project_id in before["credential"]["projects"], "OWNED_CONTROL_CREDENTIAL_REQUIRED")
        if args.stage == "publicar-w07":
            require(receipt["status"] == "IMPORTED_INACTIVE", "INACTIVE_IMPORT_RECEIPT_REQUIRED")
            selected = ["W07"]
            expected_status = "W07_PUBLISHED"
        else:
            require(receipt["status"] == "W07_PUBLISHED" and found[flows["W07"]["id"]]["activeVersionId"], "PUBLISHED_W07_REQUIRED")
            selected = [key for key in ORDER if key in SCHEDULES]
            expected_status = "SCHEDULES_PUBLISHED"
        for logical in selected:
            flows[logical]["active"] = True
        report["status"] = "PUBLISHING_" + args.stage.upper()
        save(args.receipt, report)
        cli_import(editor, args.project_id, [flows[key] for key in selected], "workflow", active=True)
    expected_active = set() if args.stage == "importar" else {flows["W07"]["id"]}
    if args.stage == "publicar-agendamentos":
        expected_active.update(flows[key]["id"] for key in SCHEDULES)
    for attempt in range(46):
        after = metadata(pg, args.project_id, credential["id"])
        current = {row["id"]: row for row in after["workflows"] if row["id"] in ids}
        if {key for key, row in current.items() if row["active"] and row["activeVersionId"]} == expected_active:
            break
        require(attempt < 45 and args.stage != "importar", "PUBLICATION_POSTCONDITION_FAILED")
        time.sleep(1)
    require(len(current) == 8 and all(args.project_id in row["projects"] for row in current.values()), "IMPORT_POSTCONDITION_FAILED")
    require(after["credential"] is not None and after["credential"]["type"] == "httpHeaderAuth"
            and args.project_id in after["credential"]["projects"], "CREDENTIAL_POSTCONDITION_FAILED")
    require({key for key, row in current.items() if row["active"] and row["activeVersionId"]} == expected_active, "PUBLICATION_POSTCONDITION_FAILED")
    outside_before = [row for row in before["workflows"] if row["id"] not in ids]
    outside_after = [row for row in after["workflows"] if row["id"] not in ids]
    report.update(status=expected_status, outsideWorkflowStateUnchanged=outside_before == outside_after,
                  workflowVersions=current, credentialImported=True, w07Parents=flows["W07"]["settings"]["callerIds"].split(","))
    save(args.receipt, report)
    print(json.dumps({key: report[key] for key in ("status", "namespace", "projectId", "credentialId", "outsideWorkflowStateUnchanged")}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", choices=("status", "importar", "publicar-w07", "publicar-agendamentos"))
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--manifest-sha256", required=True)
    parser.add_argument("--project-id", required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--control-file", type=Path)
    parser.add_argument("--apply", action="store_true")
    try:
        execute(parser.parse_args())
    except (OSError, ValueError, KeyError, TypeError, RuntimeError, subprocess.TimeoutExpired):
        parser.exit(1, "Etapa n8n interrompida. Preserve o recibo e consulte status; nenhum segredo foi exibido.\n")


if __name__ == "__main__":
    main()

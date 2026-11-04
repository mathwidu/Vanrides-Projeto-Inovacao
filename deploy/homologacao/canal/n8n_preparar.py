#!/usr/bin/env python3
"""Gera exports HML locais com IDs exclusivos. Não conecta ao n8n nem lê segredos."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
GENERATOR = ROOT / "src/whatsapp/workflows/generate.mjs"
PIN = "e0c02a617f5e6ede3d6c448aff41f632a782aaca"
FLOWS = ["W0%d" % i for i in range(1, 9)]


def require(ok, code):
    if not ok:
        raise ValueError(code)


def write(path, value):
    data = (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode()
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as output:
        output.write(data)
    return hashlib.sha256(data).hexdigest()


def prepare(args):
    require(re.fullmatch(r"[A-Za-z0-9_-]{1,64}", args.credencial_id), "CREDENTIAL_ID_INVALID")
    require(1 <= len(args.credencial_nome) <= 100 and args.credencial_nome.isprintable(), "CREDENTIAL_NAME_INVALID")
    inventory = args.ids_existentes.read_bytes()
    require(len(inventory) <= 4 * 1024 * 1024, "INVENTORY_TOO_LARGE")
    existing = {line.strip() for line in inventory.decode().splitlines() if line.strip()}
    require(all(re.fullmatch(r"[A-Za-z0-9_-]{1,64}", value) for value in existing), "EXPECTED_WORKFLOW_IDS_ONLY")
    destination = args.saida.resolve()
    require(not destination.exists(), "OUTPUT_MUST_BE_NEW")
    namespace = "vrHml" + secrets.token_hex(6)
    ids = {flow: namespace + flow for flow in FLOWS}
    require(not set(ids.values()) & existing, "WORKFLOW_ID_COLLISION")
    with tempfile.TemporaryDirectory(prefix="vanrides-hml-exports-") as temp:
        base = Path(temp) / "generated"
        result = subprocess.run(["node", str(GENERATOR), "--homologacao", str(base), args.credencial_id],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
        require(result.returncode == 0, "CANONICAL_GENERATOR_FAILED")
        manifest = json.loads((base / "manifest.json").read_text())
        require(manifest["n8nVersion"] == "2.45.4" and manifest["n8nSourceCommit"] == PIN, "N8N_PIN_CHANGED")
        mapping = {row["id"]: ids[row["logicalId"]] for row in manifest["workflows"]}
        require(len(mapping) == 8 and sorted(manifest["importOrder"]) == FLOWS, "EXPECTED_EIGHT_EXPORTS")
        workflows = {}
        for flow in FLOWS:
            workflow = json.loads((base / (flow + ".json")).read_text())
            workflow["id"] = ids[flow]
            workflow["name"] = workflow["name"].replace("VanRides HML", "VanRides HML " + namespace, 1)
            require(workflow["active"] is False and workflow["pinData"] == {}, "INACTIVE_EXPORT_REQUIRED")
            settings = workflow["settings"]
            settings["callerIds"] = ",".join(mapping[value] for value in settings["callerIds"].split(","))
            if "errorWorkflow" in settings:
                settings["errorWorkflow"] = mapping[settings["errorWorkflow"]]
            for node in workflow["nodes"]:
                if node["type"] != "n8n-nodes-base.httpRequest":
                    continue
                require(node["parameters"]["url"].startswith("http://vanrides-hml-worker:7080/v1/"), "HML_WORKER_REQUIRED")
                node["credentials"]["httpHeaderAuth"] = {"id": args.credencial_id, "name": args.credencial_nome}
            workflows[flow] = workflow
        destination.mkdir(mode=0o700)
        for row in manifest["workflows"]:
            flow = row["logicalId"]
            row["id"] = ids[flow]
            row["errorWorkflow"] = None if flow == "W07" else ids["W07"]
            row["sha256"] = write(destination / (flow + ".json"), workflows[flow])
        manifest["credentials"][0].update(id=args.credencial_id, name=args.credencial_nome)
        manifest.update(namespace=namespace, existingIdsSha256=hashlib.sha256(inventory).hexdigest(),
                        canonicalGeneratorSha256=hashlib.sha256(GENERATOR.read_bytes()).hexdigest(),
                        remoteOperations="NOT_RUN", credentialValuesRead=False,
                        importBundleSha256=write(destination / "importar.json", [workflows[flow] for flow in manifest["importOrder"]]))
        write(destination / "manifest.json", manifest)
    print(json.dumps({"status": "PREPARED_INACTIVE_ONLY", "directory": str(destination),
                      "namespace": namespace, "workflows": ids, "remoteOperations": "NOT_RUN"}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ids-existentes", type=Path, required=True, help="Inventário somente de IDs, um por linha")
    parser.add_argument("--credencial-id", required=True, help="ID público da Header Auth criada no editor")
    parser.add_argument("--credencial-nome", default="VanRides HML Controle")
    parser.add_argument("--saida", type=Path, required=True, help="Diretório novo fora do checkout")
    args = parser.parse_args()
    try:
        require(not args.saida.resolve().is_relative_to(ROOT), "OUTPUT_OUTSIDE_CHECKOUT_REQUIRED")
        prepare(args)
    except (OSError, ValueError, KeyError, TypeError):
        parser.exit(1, "Não foi possível preparar exports inativos; confira pin, IDs, credencial e diretório novo.\n")


if __name__ == "__main__":
    main()

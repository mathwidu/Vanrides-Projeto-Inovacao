"""Prepare the private inputs for ONE new, isolated team channel. Never deploys."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import secrets
import stat
import subprocess
import tempfile

INSTANCE = "vanrides-hml"
ENVIRONMENT = "vanrides-mvp-hml"
ROLES = {"conversation": "CONVERSATION_WORKER", "delivery": "DELIVERY_WORKER",
         "job": "SYSTEM_JOB", "channel": "CHANNEL"}


def private_json(path):
    with os.fdopen(os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK), "rb") as file:
        info = os.fstat(file.fileno())
        if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or
            stat.S_IMODE(info.st_mode) != 0o600 or not 0 < info.st_size <= 65536):
            raise ValueError("Entrada privada inválida.")
        return json.loads(file.read(65537))


def stamp(value):
    return value.astimezone(dt.timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def write(directory, name, value):
    path = directory / name
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as file:
        file.write(value.encode() if isinstance(value, str) else value)


def create_tls(directory):
    def openssl(*args):
        subprocess.run(["openssl", *args], cwd=directory, check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    with tempfile.TemporaryDirectory(dir=directory) as scratch:
        tmp = Path(scratch)
        openssl("req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "35", "-subj",
                "/CN=VanRides HML CA", "-keyout", str(tmp / "ca.key"), "-out", str(directory / "tls_ca"),
                "-addext", "basicConstraints=critical,CA:TRUE", "-addext", "keyUsage=critical,keyCertSign")
        openssl("req", "-newkey", "rsa:2048", "-nodes", "-subj", "/CN=evolution-hml",
                "-keyout", str(directory / "tls_key"), "-out", str(tmp / "server.csr"))
        ext = tmp / "server.ext"
        ext.write_text("basicConstraints=critical,CA:FALSE\nsubjectAltName=DNS:evolution-hml\nextendedKeyUsage=serverAuth\n")
        openssl("x509", "-req", "-in", str(tmp / "server.csr"), "-CA", str(directory / "tls_ca"),
                "-CAkey", str(tmp / "ca.key"), "-set_serial", str(secrets.randbits(128) + 1),
                "-days", "30", "-sha256", "-extfile", str(ext), "-out", str(directory / "tls_crt"))
        openssl("verify", "-verify_hostname", "evolution-hml", "-CAfile", str(directory / "tls_ca"), str(directory / "tls_crt"))


def prepare(args):
    dest = args.output.absolute()
    parent = dest.parent
    if parent.resolve() != parent or parent.stat().st_uid != os.getuid() or parent.stat().st_mode & 0o077 or dest.exists() or any((p / ".git").exists() for p in (parent, *parent.parents)):
        raise ValueError("Use uma nova pasta fora do Git, em diretório privado 0700 do operador.")
    contacts = private_json(args.contacts)
    if not isinstance(contacts, list) or not 1 <= len(contacts) <= 4:
        raise ValueError("Esperado um a quatro participantes autorizados.")
    labels, aliases = set(), set()
    for item in contacts:
        if (not isinstance(item, dict) or set(item) not in ({"label","pn","approved"}, {"label","pn","lid","approved"}) or
            item["approved"] is not True or item["label"] not in ("A","B","C","D") or item["label"] in labels or
            not re.fullmatch(r"[1-9][0-9]{7,14}@s\.whatsapp\.net", item["pn"]) or item["pn"] in aliases or
            ("lid" in item and (not re.fullmatch(r"[0-9]{1,32}@lid", item["lid"]) or item["lid"] in aliases))):
            raise ValueError("Participante não autorizado ou duplicado.")
        labels.add(item["label"])
        aliases.update([item["pn"], item.get("lid")])
    start = dt.datetime.now(dt.timezone.utc)
    end = start + dt.timedelta(days=29)
    roles = {}
    for key, principal in ROLES.items():
        receipt = private_json(args.operator / (key + ".json"))
        if (receipt.get("status") != "COMMITTED" or receipt.get("environment") != ENVIRONMENT or
            receipt.get("principal") != principal or receipt.get("allowedInstanceId") != INSTANCE or
            not re.fullmatch(r"[A-Za-z0-9_-]{43}", receipt.get("credential",""))):
            raise ValueError("Recibo de credencial incompatível.")
        expires = dt.datetime.fromisoformat(receipt["expiresAt"].replace("Z","+00:00"))
        end = min(expires, end)
        roles[key] = receipt["credential"]
    if len(set(roles.values())) != len(roles) or end <= start + dt.timedelta(minutes=5):
        raise ValueError("Credenciais repetidas ou expiradas.")
    # Use a sibling directory so a failed preparation leaves no publishable files.
    with tempfile.TemporaryDirectory(dir=parent) as temp:
        stage = Path(temp) / "output"
        stage.mkdir(mode=0o700)
        values = {n: secrets.token_urlsafe(32) for n in ("pg_pass","redis_pass","evo_key","evo_instance","control","webhook_token")}
        values["redis_conf"] = ("bind 0.0.0.0\nprotected-mode yes\nsave \"\"\nappendonly no\nmaxmemory 96mb\n"
                                "maxmemory-policy noeviction\nrequirepass " + values["redis_pass"] + "\n")
        ingress = dict(mode="HML_V1", environment=ENVIRONMENT, instance=INSTANCE, webhookToken=values["webhook_token"],
                       channelToken=roles.pop("channel"), apiOrigin="https://vanrides-inovacao-api.matheusduarte.dev.br",
                       startsAt=stamp(start), endsAt=stamp(end), contacts=contacts,
                       listenHost="0.0.0.0", listenPort=7180,
                       limitsPerMinute={"human":48,"perContact":16,"status":240,"connection":32})
        for name, value in {**roles, **values, "ingress":json.dumps(ingress)}.items():
            write(stage, name, value)
        create_tls(stage)
        stage.rename(dest)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ("contacts","operator","output"):
        parser.add_argument("--" + key, required=True, type=Path)
    os.umask(0o077)
    try:
        prepare(parser.parse_args())
    except (ValueError, OSError, KeyError, TypeError, subprocess.SubprocessError):
        raise SystemExit("Não foi possível preparar o canal. Confira entradas, permissões e validade dos recibos.")
    print("Segredos preparados em diretório privado. Nenhum serviço foi alterado.")

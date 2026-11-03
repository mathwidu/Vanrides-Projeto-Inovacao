#!/usr/bin/env python3
"""Prepare privately the WhatsApp participants who have agreed to the team test."""
import argparse
from getpass import getpass
import json
import os
from pathlib import Path
import re
import tempfile


def participants():
    rows = []
    for label in "ABCD":
        raw = getpass(f"Telefone do participante {label} que aceitou testar, com país e DDD (Enter para concluir): ").strip()
        if not raw:
            break
        if not re.fullmatch(r"\+?[1-9][0-9]{7,14}", raw):
            raise SystemExit("Use somente os dígitos de país, DDD e telefone. Nada salvo.")
        jid = raw.lstrip("+") + "@s.whatsapp.net"
        if any(row["pn"] == jid for row in rows):
            raise SystemExit("O mesmo contato foi informado duas vezes. Nada salvo.")
        rows.append({"label": label, "pn": jid, "approved": True})
    if not rows:
        raise SystemExit("Nenhum integrante informado.")
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination", type=Path, help="Arquivo novo, fora do Git, em diretório privado do operador")
    args = parser.parse_args()
    dest = args.destination.expanduser().absolute()
    owner_dir = dest.parent
    if any((path / ".git").exists() for path in (owner_dir, *owner_dir.parents)):
        raise SystemExit("Escolha uma pasta fora do repositório.")
    if not owner_dir.is_dir() or owner_dir.resolve() != owner_dir or owner_dir.stat().st_uid != os.getuid() or owner_dir.stat().st_mode & 0o077:
        raise SystemExit("A pasta deve pertencer ao operador e ter permissão 0700.")
    if dest.exists() or dest.is_symlink():
        raise SystemExit("Escolha um arquivo novo. O arquivo anterior foi preservado.")
    print("Informe somente participantes que concordaram com o teste. Exclua o telefone do bot.")
    rows = participants()
    with tempfile.NamedTemporaryFile(mode="w", dir=owner_dir) as tmp:
        json.dump(rows, tmp)
        tmp.flush()
        os.fsync(tmp.fileno())
        os.link(tmp.name, dest)
    print(f"CONCLUÍDO. {len(rows)} participante(s) preparados localmente.")


if __name__ == "__main__":
    main()

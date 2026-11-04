#!/usr/bin/env python3
"""Prova de cadastro sintético da equipe. Requer Python 3 e uma fixture preparada."""
import argparse
import getpass
import http.client
import json
import re
import ssl
import sys
import time
import uuid
import warnings
from datetime import datetime

HOST = "vanrides-inovacao-api.matheusduarte.dev.br"
ENVIRONMENT = "vanrides-team-hml"
PREFIX = "/integrations/whatsapp/v1"
TOKEN = re.compile(r"[A-Za-z0-9_-]{43}")
MAX_BODY = 64 * 1024


class CheckFailed(Exception):
    """Mensagem pública para o colega, sem corpo de resposta ou credencial."""


def require(condition, message):
    if not condition:
        raise CheckFailed(message)


def canonical_uuid(value):
    try:
        return isinstance(value, str) and str(uuid.UUID(value)) == value
    except ValueError:
        return False


def request(method, path, body=None, headers=None, timeout=7):
    connection = http.client.HTTPSConnection(HOST, timeout=timeout,
                                             context=ssl.create_default_context())
    data = None if body is None else json.dumps(body).encode("utf-8")
    request_headers = {"Accept": "application/json", **(headers or {})}
    if data is not None:
        request_headers["Content-Type"] = "application/json"
    try:
        connection.request(method, path, body=data, headers=request_headers)
        response = connection.getresponse()
        # http.client não segue redirects. Nunca encaminhar Bearer para outro destino.
        require(not 300 <= response.status < 400, "O servidor redirecionou a chamada.")
        raw = response.read(MAX_BODY + 1)
        require(len(raw) <= MAX_BODY, "A resposta ultrapassou o tamanho esperado.")
        parsed = json.loads(raw) if raw else {}
        require(isinstance(parsed, dict), "A API retornou um formato inesperado.")
        return response.status, parsed, response.getheader("X-VanRides-Environment")
    except (OSError, http.client.HTTPException, ValueError) as error:
        raise CheckFailed("Falha de TLS, conexão ou leitura da resposta. Confira o resultado com o operador antes de repetir.") from error
    finally:
        connection.close()


def check_destination(send):
    status, data, marker = send("GET", "/actuator/health")
    require(status == 200 and data.get("status") == "UP" and marker == ENVIRONMENT,
            "O endereço não confirmou a API de homologação saudável.")
    status, _, _ = send("POST", PREFIX + "/inbox/claim", {"batchLimit": 1, "leaseSeconds": 60})
    require(status == 401, "A rota protegida não recusou a chamada sem credencial.")


def run_scenario(send, credential, event, now=time.time):
    require(isinstance(credential, str) and TOKEN.fullmatch(credential), "A credencial precisa ser a individual fornecida pelo operador.")
    auth = {"Authorization": "Bearer " + credential}
    status, claim, _ = send("POST", PREFIX + "/inbox/claim",
                            {"batchLimit": 1, "leaseSeconds": 60}, auth)
    require(status == 200, "A API recusou a reserva do evento. Confira validade e instância com o operador.")
    items = claim.get("items")
    require(isinstance(items, list) and len(items) == 1 and isinstance(items[0], dict),
            "Não há uma única fixture disponível. Confira se a reserva inicial já venceu.")
    item = items[0]
    require(item.get("inboxEventId") == event, "A API reservou outro evento. O cadastro não foi iniciado.")
    lease = item.get("inboxLease")
    require(isinstance(lease, str) and TOKEN.fullmatch(lease), "Reserva inválida.")
    try:
        expiry = datetime.fromisoformat(item["leaseExpiresAt"].replace("Z", "+00:00"))
        require(expiry.tzinfo is not None, "Reserva sem fuso horário.")
        deadline = expiry.timestamp()
    except (KeyError, AttributeError, TypeError, ValueError) as error:
        raise CheckFailed("Reserva sem prazo válido.") from error
    leased = {**auth, "X-Inbox-Lease": lease}

    def call(method, path, body=None, headers=leased):
        remaining = deadline - now()
        require(remaining > 0, "A reserva expirou. Peça nova fixture ao operador.")
        status, result, _ = send(method, PREFIX + path, body, headers, min(7, remaining))
        require(status == 200, "A API recusou uma etapa da fixture (HTTP %d). Confira o estado antes de repetir." % status)
        return result

    context = call("GET", "/context?inboxEventId=" + event)
    version = context.get("entityVersion")
    require(context.get("inboxEventId") == event and type(version) is int
            and isinstance(context.get("allowedCommands"), list)
            and "START_ONBOARDING" in context["allowedCommands"],
            "O evento não oferece o início de cadastro esperado.")
    command = {"commandType": "START_ONBOARDING", "inboxEventId": event,
               "expectedVersion": version, "payload": {}}
    headers = {**leased, "Idempotency-Key": "hml_" + event}
    first = call("POST", "/commands", command, headers)
    response_ids = first.get("responseIds")
    require(first.get("outcome") == "APPLIED" and canonical_uuid(first.get("commandId"))
            and type(first.get("entityVersion")) is int
            and isinstance(response_ids, list) and len(response_ids) == 1
            and canonical_uuid(response_ids[0]), "O início de cadastro não foi aplicado.")
    replay = call("POST", "/commands", command, headers)
    for key in ("commandId", "entityVersion", "outcome", "responseIds"):
        require(replay.get(key) == first.get(key), "A repetição não devolveu o recibo original.")
    ack = call("POST", "/inbox/" + event + "/result", {"outcome": "COMPLETED"})
    require(ack.get("inboxEventId") == event and ack.get("state") == "PROCESSED",
            "A API não confirmou o processamento do evento.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evento", required=True, type=uuid.UUID,
                        help="UUID da fixture entregue pelo operador para esta rodada")
    event = str(parser.parse_args().evento)
    try:
        check_destination(request)
        with warnings.catch_warnings():
            warnings.simplefilter("error", getpass.GetPassWarning)
            credential = getpass.getpass("Credencial de conversa da homologação (entrada oculta): ")
        run_scenario(request, credential, event)
    except (EOFError, KeyboardInterrupt, getpass.GetPassWarning):
        print("Entrada privada interrompida. Execute no Terminal ou no PowerShell.", file=sys.stderr)
        return 1
    except CheckFailed as error:
        print(str(error), file=sys.stderr)
        return 1
    print("PASS — HTTPS, autenticação, cadastro sintético, repetição idempotente e confirmação.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

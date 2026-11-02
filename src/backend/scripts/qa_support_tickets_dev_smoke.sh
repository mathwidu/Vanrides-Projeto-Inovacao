#!/usr/bin/env bash
set -euo pipefail

FIXTURE_ENV="${VANRIDES_DEV_FIXTURE_ENV:-${1:-}}"

if [[ -n "$FIXTURE_ENV" ]]; then
  if [[ ! -f "$FIXTURE_ENV" ]]; then
    echo "FAIL: fixture env nao encontrado: $FIXTURE_ENV" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "$FIXTURE_ENV"
  set +a
fi

export BASE_URL="${VANRIDES_BACKEND_BASE_URL:-${base_url:-http://127.0.0.1:8080}}"
export ADMIN_EMAIL="${VANRIDES_ADMIN_EMAIL:-${admin_email:-}}"
export ADMIN_PASSWORD="${VANRIDES_ADMIN_PASSWORD:-${admin_password:-}}"
export COMPANY_ID="${VANRIDES_COMPANY_ID:-${company_id:-}}"

if [[ -z "$ADMIN_EMAIL" || -z "$ADMIN_PASSWORD" || -z "$COMPANY_ID" ]]; then
  cat >&2 <<'EOF'
FAIL: smoke exige credenciais administrativas e company_id.
Informe VANRIDES_ADMIN_EMAIL, VANRIDES_ADMIN_PASSWORD e VANRIDES_COMPANY_ID,
ou passe um fixture.env gerado pelos smokes DEV.
EOF
  exit 1
fi

python3 - <<'PY'
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

base_url = os.environ["BASE_URL"].rstrip("/")
admin_email = os.environ["ADMIN_EMAIL"]
admin_password = os.environ["ADMIN_PASSWORD"]
company_id = int(os.environ["COMPANY_ID"])
run_id = f"{int(time.time())}-{uuid.uuid4().hex[:8]}"
password = "VanridesSmoke123!"


def log(message: str) -> None:
    print(f"\n[{datetime.now().strftime('%H:%M:%S')}] {message}", flush=True)


def sanitize(payload: str) -> str:
    try:
        data = json.loads(payload)
    except Exception:
        return payload[:1200]
    if isinstance(data, dict):
        for key in ("accessToken", "refreshToken"):
            if key in data:
                data[key] = "<redacted>"
    return json.dumps(data, ensure_ascii=False, indent=2)[:1200]


def request(method: str, path: str, *, expected: set[int] | int, token: str | None = None,
            body: dict | None = None, description: str = ""):
    expected_set = {expected} if isinstance(expected, int) else expected
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(f"{base_url}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            status = response.status
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        status = exc.code
        raw = exc.read().decode("utf-8", errors="replace")
    except Exception as exc:
        print(f"FAIL: {description or method + ' ' + path} falhou antes de HTTP: {exc}", file=sys.stderr)
        sys.exit(1)

    if status not in expected_set:
        print(
            f"FAIL: {description or method + ' ' + path} "
            f"(esperado={sorted(expected_set)}, atual={status})",
            file=sys.stderr,
        )
        if raw:
            print(sanitize(raw), file=sys.stderr)
        sys.exit(1)

    print(f"PASS: {description or method + ' ' + path} -> {status}")
    if not raw:
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw


def assert_equal(actual, expected, description: str) -> None:
    if actual != expected:
        print(f"FAIL: {description} (esperado={expected!r}, atual={actual!r})", file=sys.stderr)
        sys.exit(1)
    print(f"PASS: {description} -> {actual}")


def assert_truthy(value, description: str) -> None:
    if not value:
        print(f"FAIL: {description}", file=sys.stderr)
        sys.exit(1)
    print(f"PASS: {description}")


def login(email: str, password_value: str, label: str) -> dict:
    data = request(
        "POST",
        "/login",
        expected=200,
        body={"email": email, "senha": password_value},
        description=f"login {label}",
    )
    assert_truthy(isinstance(data, dict) and data.get("accessToken"), f"token recebido para {label}")
    return data


def passenger_payload() -> dict:
    suffix = uuid.uuid4().hex[:10]
    return {
        "nome": f"QA Ticket {run_id}",
        "email": f"qa.ticket.{run_id}.{suffix}@vanrides.dev",
        "telefone": f"5198{int(time.time()) % 1000000:06d}",
        "senha": password,
        "endereco": "Rua dos Andradas, 1000",
        "bairro": "Centro",
        "cidade": "Porto Alegre",
        "estado": "RS",
        "cep": "90020-008",
        "latitude": -30.0301,
        "longitude": -51.2300,
        "dataNascimento": "2000-01-10",
    }


log(f"Smoke support tickets em {base_url}")

request("OPTIONS", "/support/tickets", expected={200, 204}, description="CORS preflight support tickets")

admin_session = login(admin_email, admin_password, "admin empresa")
admin_token = admin_session["accessToken"]

future = (datetime.now(timezone.utc) + timedelta(days=1)).isoformat().replace("+00:00", "Z")
invite = request(
    "POST",
    "/company-admins/invite-codes",
    expected=201,
    token=admin_token,
    body={"expiresAt": future, "maxUses": 1},
    description="admin cria convite para passageiro do smoke",
)
code = invite.get("code")
assert_truthy(code, "codigo de convite retornado")

passenger = passenger_payload()
request("POST", "/passageiros", expected={200, 201}, body=passenger, description="cadastro publico passageiro")
passenger_session = login(passenger["email"], passenger["senha"], "passageiro sem empresa")
passenger_token = passenger_session["accessToken"]

linked = request(
    "POST",
    "/passageiro-onboarding/invite-codes/redeem",
    expected=200,
    token=passenger_token,
    body={"code": code},
    description="passageiro resgata convite",
)
assert_equal(linked.get("companyLinkStatus"), "COMPANY_LINKED", "passageiro vinculado por convite")

upgraded = request(
    "POST",
    "/passageiro-onboarding/session-upgrade",
    expected=200,
    token=passenger_token,
    description="upgrade de sessao passageiro vinculado",
)
linked_token = upgraded["accessToken"]

ticket = request(
    "POST",
    "/support/tickets",
    expected=201,
    token=linked_token,
    body={
        "category": "ATRASO",
        "subject": "Van atrasada",
        "message": "A van ainda nao passou na minha parada.",
    },
    description="passageiro abre ticket",
)
ticket_id = ticket.get("id")
assert_truthy(ticket_id, "ticket criado com id")
assert_equal(ticket.get("status"), "OPEN", "ticket inicia aberto")
assert_equal(ticket.get("messages", [{}])[0].get("authorRole"), "PASSENGER", "mensagem inicial auditada")

own_tickets = request("GET", "/support/tickets", expected=200, token=linked_token, description="passageiro lista tickets")
assert_truthy(any(item.get("id") == ticket_id for item in own_tickets or []), "ticket aparece para passageiro")

inbox = request(
    "GET",
    "/company-admins/support/tickets?status=OPEN",
    expected=200,
    token=admin_token,
    description="empresa lista inbox aberto",
)
assert_truthy(any(item.get("id") == ticket_id for item in inbox or []), "ticket aparece no inbox da empresa")

resolved = request(
    "POST",
    f"/company-admins/support/tickets/{ticket_id}/responses",
    expected=200,
    token=admin_token,
    body={"message": "Resolvido com o motorista.", "status": "RESOLVED"},
    description="empresa responde e resolve",
)
assert_equal(resolved.get("status"), "RESOLVED", "ticket resolvido pela empresa")
assert_equal(resolved.get("messages", [{}])[0].get("authorRole"), "COMPANY_ADMIN", "resposta auditada como admin")

detail = request(
    "GET",
    f"/support/tickets/{ticket_id}",
    expected=200,
    token=linked_token,
    description="passageiro consulta ticket resolvido",
)
assert_equal(detail.get("status"), "RESOLVED", "passageiro ve ticket resolvido")

print("\nPASS: smoke support tickets completo")
PY

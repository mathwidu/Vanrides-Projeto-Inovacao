#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
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
export COMPANY_SEARCH_QUERY="${VANRIDES_COMPANY_SEARCH_QUERY:-${company_slug:-${company_name:-}}}"

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
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

base_url = os.environ["BASE_URL"].rstrip("/")
admin_email = os.environ["ADMIN_EMAIL"]
admin_password = os.environ["ADMIN_PASSWORD"]
company_id = int(os.environ["COMPANY_ID"])
company_query = os.environ.get("COMPANY_SEARCH_QUERY") or str(company_id)
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


def passenger_payload(label: str) -> dict:
    suffix = uuid.uuid4().hex[:10]
    return {
        "nome": f"QA {label} {run_id}",
        "email": f"qa.{label.lower()}.{run_id}.{suffix}@vanrides.dev",
        "telefone": f"5199{int(time.time()) % 1000000:06d}",
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


def create_passenger(label: str) -> dict:
    payload = passenger_payload(label)
    request("POST", "/passageiros", expected={200, 201}, body=payload, description=f"cadastro publico {label}")
    return payload


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


def account_company_id(token: str) -> int | None:
    data = request("GET", "/account/me", expected=200, token=token, description="GET /account/me")
    if not isinstance(data, dict):
        return None
    empresa = data.get("empresa")
    if isinstance(empresa, dict):
        return empresa.get("id")
    return None


log(f"Smoke onboarding hibrido passageiro em {base_url}")

admin_session = login(admin_email, admin_password, "admin empresa")
admin_token = admin_session["accessToken"]

log("Passageiro sem empresa solicita vinculo por busca")
passenger = create_passenger("PassengerRequest")
passenger_session = login(passenger["email"], passenger["senha"], "passageiro sem empresa")
assert_equal(passenger_session.get("tipoUsuario"), "Passageiro", "tipo usuario passageiro")
assert_equal(passenger_session.get("companyLinkStatus"), "NO_COMPANY", "login inicia sem empresa")
passenger_token = passenger_session["accessToken"]

status = request("GET", "/passageiro-onboarding/status", expected=200, token=passenger_token, description="status inicial onboarding")
assert_equal(status.get("companyLinkStatus"), "NO_COMPANY", "status inicial NO_COMPANY")

query = urllib.parse.urlencode({"query": company_query})
companies = request("GET", f"/passageiro-onboarding/companies?{query}", expected=200, token=passenger_token, description="busca empresas")
assert_truthy(any(item.get("id") == company_id for item in companies or []), "empresa fixture aparece na busca")

status = request(
    "POST",
    "/passageiro-onboarding/requests",
    expected=201,
    token=passenger_token,
    body={"companyId": company_id},
    description="solicitacao de vinculo",
)
assert_equal(status.get("companyLinkStatus"), "PENDING_APPROVAL", "solicitacao fica pendente")
pending_request = status.get("pendingRequest") or {}
request_id = pending_request.get("id")

requests = request("GET", "/company-admins/access-requests", expected=200, token=admin_token, description="admin lista solicitacoes")
if not request_id:
    matches = [
        item for item in requests or []
        if item.get("passageiroEmail") == passenger["email"]
    ]
    request_id = matches[0]["id"] if matches else None
assert_truthy(request_id, "solicitacao pendente encontrada pelo admin")

approved = request(
    "POST",
    f"/company-admins/access-requests/{request_id}/approve",
    expected=200,
    token=admin_token,
    body={"reason": "Aprovado por smoke QA", "servicoIds": []},
    description="admin aprova solicitacao",
)
assert_equal(approved.get("status"), "APPROVED", "solicitacao aprovada")

upgraded = request(
    "POST",
    "/passageiro-onboarding/session-upgrade",
    expected=200,
    token=passenger_token,
    description="upgrade de sessao apos aprovacao",
)
assert_truthy(isinstance(upgraded, dict) and upgraded.get("accessToken"), "sessao promovida com token")
assert_equal(account_company_id(upgraded["accessToken"]), company_id, "account/me vinculado a empresa apos aprovacao")

log("Convite administrativo: invalido, valido e expirado")
future = (datetime.now(timezone.utc) + timedelta(days=1)).isoformat().replace("+00:00", "Z")
invite = request(
    "POST",
    "/company-admins/invite-codes",
    expected=201,
    token=admin_token,
    body={"expiresAt": future, "maxUses": 1},
    description="admin cria convite valido",
)
code = invite.get("code")
assert_truthy(code, "codigo de convite retornado")

invite_passenger = create_passenger("PassengerInvite")
invite_session = login(invite_passenger["email"], invite_passenger["senha"], "passageiro convite")
invite_token = invite_session["accessToken"]
request(
    "POST",
    "/passageiro-onboarding/invite-codes/redeem",
    expected=404,
    token=invite_token,
    body={"code": f"INV-INVALID-{run_id}"},
    description="resgate de convite inexistente",
)
linked = request(
    "POST",
    "/passageiro-onboarding/invite-codes/redeem",
    expected=200,
    token=invite_token,
    body={"code": code},
    description="resgate de convite valido",
)
assert_equal(linked.get("companyLinkStatus"), "COMPANY_LINKED", "convite vincula passageiro")
invite_upgraded = request(
    "POST",
    "/passageiro-onboarding/session-upgrade",
    expected=200,
    token=invite_token,
    description="upgrade de sessao apos convite",
)
assert_equal(account_company_id(invite_upgraded["accessToken"]), company_id, "account/me vinculado a empresa apos convite")

past = (datetime.now(timezone.utc) - timedelta(minutes=5)).isoformat().replace("+00:00", "Z")
expired_invite = request(
    "POST",
    "/company-admins/invite-codes",
    expected=201,
    token=admin_token,
    body={"expiresAt": past, "maxUses": 1},
    description="admin cria convite expirado para validacao",
)
expired_code = expired_invite.get("code")
expired_passenger = create_passenger("PassengerExpiredInvite")
expired_session = login(expired_passenger["email"], expired_passenger["senha"], "passageiro convite expirado")
request(
    "POST",
    "/passageiro-onboarding/invite-codes/redeem",
    expected=400,
    token=expired_session["accessToken"],
    body={"code": expired_code},
    description="resgate de convite expirado",
)

log("Smoke onboarding hibrido finalizado com sucesso")
PY

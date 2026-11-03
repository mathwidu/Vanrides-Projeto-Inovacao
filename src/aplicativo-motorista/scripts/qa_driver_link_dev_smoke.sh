#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

API_BASE_URL="${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}"
FIXTURE_ENV="${VANRIDES_DRIVER_LINK_FIXTURE_ENV:-}"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_driver_link_dev_smoke.sh [opcoes]

Opcoes:
  --api <url>             Backend alvo. Padrao: DEV.
  --fixture-env <path>    Arquivo fixture.env com admin_email/admin_password/company_id.
  -h, --help              Mostra esta ajuda.

Variaveis aceitas:
  VANRIDES_ADMIN_EMAIL
  VANRIDES_ADMIN_PASSWORD
  VANRIDES_COMPANY_ID

O script nao imprime tokens nem senhas. Ele cria motoristas QA temporarios
em @vanrides.dev e valida convite por link + solicitacao manual.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api)
      API_BASE_URL="$2"
      shift 2
      ;;
    --fixture-env)
      FIXTURE_ENV="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Opcao desconhecida: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -n "$FIXTURE_ENV" ]]; then
  if [[ ! -f "$FIXTURE_ENV" ]]; then
    echo "fixture-env nao encontrado: $FIXTURE_ENV" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "$FIXTURE_ENV"
  set +a
fi

export VANRIDES_API_BASE_URL="$API_BASE_URL"
export VANRIDES_ADMIN_EMAIL="${VANRIDES_ADMIN_EMAIL:-${admin_email:-}}"
export VANRIDES_ADMIN_PASSWORD="${VANRIDES_ADMIN_PASSWORD:-${admin_password:-}}"
export VANRIDES_COMPANY_ID="${VANRIDES_COMPANY_ID:-${company_id:-}}"

cd "$APP_DIR"

python3 - <<'PY'
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

base_url = os.environ["VANRIDES_API_BASE_URL"].rstrip("/")
admin_email = os.environ.get("VANRIDES_ADMIN_EMAIL", "").strip()
admin_password = os.environ.get("VANRIDES_ADMIN_PASSWORD", "")
company_id_raw = os.environ.get("VANRIDES_COMPANY_ID", "").strip()

if not admin_email or not admin_password:
    raise SystemExit("Defina VANRIDES_ADMIN_EMAIL/VANRIDES_ADMIN_PASSWORD ou passe --fixture-env.")


def request(method, path, *, token=None, body=None, expected=(200,)):
    url = base_url + path
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            status = resp.status
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        status = exc.code
        raw = exc.read().decode("utf-8")
    except urllib.error.URLError as exc:
        raise AssertionError(f"{method} {path} falhou por rede: {exc}") from exc

    if status not in expected:
        raise AssertionError(f"{method} {path} -> {status}, esperado {expected}. Body: {raw[:500]}")
    if not raw.strip():
        return {}
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"_raw": raw}


def login(email, password, expected_role):
    data = request(
        "POST",
        "/login",
        body={"email": email, "senha": password},
        expected=(200,),
    )
    role = data.get("tipoUsuario")
    token = data.get("accessToken")
    if role != expected_role:
        raise AssertionError(f"Login {email} retornou papel {role}, esperado {expected_role}.")
    if not token:
        raise AssertionError(f"Login {email} nao retornou accessToken.")
    return data


def create_driver(email, name, password):
    request(
        "POST",
        "/motoristas",
        body={
            "nome": name,
            "email": email,
            "telefone": "51981000000",
            "dataNascimento": "1990-01-15",
            "senha": password,
        },
        expected=(200,),
    )


def assert_account_company(token, expected_company_id):
    profile = request("GET", "/account/me", token=token, expected=(200,))
    company = profile.get("empresa") or {}
    actual = company.get("id")
    if int(actual) != int(expected_company_id):
        raise AssertionError(f"/account/me empresa.id={actual}, esperado {expected_company_id}.")
    return profile


suffix = str(int(time.time()))
driver_password = "Driver@123"
admin = login(admin_email, admin_password, "Admin")
admin_token = admin["accessToken"]
company_id = int(company_id_raw or admin.get("companyId") or 0)
if not company_id:
    raise AssertionError("Nao foi possivel identificar company_id para o smoke.")

print(f"PASS admin-login company_id={company_id}")

invite = request(
    "POST",
    "/company-admins/driver-invites",
    token=admin_token,
    body={},
    expected=(201,),
)
share_url = invite.get("shareUrl") or ""
if "driver-invite/" not in share_url:
    raise AssertionError(f"Convite nao retornou shareUrl de driver-invite: {share_url}")
token = share_url.rsplit("driver-invite/", 1)[-1].strip()
if not token:
    raise AssertionError("Convite retornou token vazio.")

preview = request(
    "GET",
    f"/driver-onboarding/invites/{urllib.parse.quote(token, safe='')}/preview",
    expected=(200,),
)
if int(preview.get("companyId")) != company_id:
    raise AssertionError("Preview do convite aponta para empresa diferente da fixture.")
print("PASS invite-preview")

invite_driver_email = f"qa.driver.link.{suffix}@vanrides.dev"
create_driver(invite_driver_email, f"QA Driver Link {suffix}", driver_password)
driver_login = login(invite_driver_email, driver_password, "Motorista")
redeem = request(
    "POST",
    f"/driver-onboarding/invites/{urllib.parse.quote(token, safe='')}/redeem",
    token=driver_login["accessToken"],
    expected=(200,),
)
if redeem.get("companyLinkStatus") != "COMPANY_LINKED":
    raise AssertionError(f"Redeem retornou {redeem.get('companyLinkStatus')}, esperado COMPANY_LINKED.")
upgrade = request(
    "POST",
    "/driver-onboarding/session-upgrade",
    token=driver_login["accessToken"],
    expected=(200,),
)
assert_account_company(upgrade["accessToken"], company_id)
print(f"PASS invite-redeem driver_email={invite_driver_email}")

manual_driver_email = f"qa.driver.request.{suffix}@vanrides.dev"
create_driver(manual_driver_email, f"QA Driver Request {suffix}", driver_password)
manual_login = login(manual_driver_email, driver_password, "Motorista")
companies = request(
    "GET",
    f"/driver-onboarding/companies?query={urllib.parse.quote(str(company_id))}",
    token=manual_login["accessToken"],
    expected=(200,),
)
if not isinstance(companies, list) or not any(int(item.get("id")) == company_id for item in companies):
    companies = request(
        "GET",
        "/driver-onboarding/companies",
        token=manual_login["accessToken"],
        expected=(200,),
    )
if not isinstance(companies, list) or not any(int(item.get("id")) == company_id for item in companies):
    raise AssertionError("Busca de empresas nao retornou a empresa da fixture.")
request_status = request(
    "POST",
    "/driver-onboarding/requests",
    token=manual_login["accessToken"],
    body={"companyId": company_id},
    expected=(201,),
)
pending = request_status.get("pendingRequest") or {}
request_id = pending.get("id")
if request_status.get("companyLinkStatus") != "PENDING_APPROVAL" or not request_id:
    raise AssertionError(f"Solicitacao manual inesperada: {request_status}")
request(
    "POST",
    f"/company-admins/driver-link-requests/{int(request_id)}/approve",
    token=admin_token,
    body={"reason": "Smoke DEV aprovado automaticamente."},
    expected=(200,),
)
manual_upgrade = request(
    "POST",
    "/driver-onboarding/session-upgrade",
    token=manual_login["accessToken"],
    expected=(200,),
)
assert_account_company(manual_upgrade["accessToken"], company_id)
print(f"PASS manual-request-approve driver_email={manual_driver_email}")
print("PASS driver-link-dev-smoke")
PY

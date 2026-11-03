#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/dev_fixture_preflight.sh"

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
LAB_PREFIX="${LAB_PREFIX:-igrlab}"
TODAY="${TODAY:-$(TZ=UTC date +%F)}"
TS="$(date +%Y%m%d%H%M%S)"
SHORT_TS="${TS: -6}"
RUN_ID="${TS}-$$"
COMPANY_NAME="Igrejinha Lab ${RUN_ID}"

ADMIN_EMAIL="lab.${LAB_PREFIX}.${RUN_ID}@vanrides.dev"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-SyntheticLab123!}"
DRIVER_PASSWORD="${DRIVER_PASSWORD:-Driver@123}"
PASSENGER_PASSWORD="${PASSENGER_PASSWORD:-Passenger@123}"
GUARDIAN_PASSWORD="${GUARDIAN_PASSWORD:-Resp@123}"

DRIVER_NAMES=("M1" "M2" "M3" "M4" "M5")
ROUTE_NAMES=(
  "Lab Centro"
  "Lab Bom Pastor"
  "Lab Viaduto"
  "Lab XV"
  "Lab Figueira"
)

# Pontos publicos/aproximados para QA humano em Igrejinha/RS.
DRIVER_ANCHORS=(
  "-29.574800,-50.790900"
  "-29.569500,-50.783700"
  "-29.567900,-50.799800"
  "-29.581600,-50.786400"
  "-29.576300,-50.804300"
)

PASSENGER_COORDS=(
  "-29.575300,-50.787100|-29.573000,-50.784200|-29.571100,-50.781400"
  "-29.568900,-50.780900|-29.566200,-50.778400|-29.563500,-50.776800"
  "-29.570100,-50.796700|-29.572500,-50.801500|-29.574900,-50.805700"
  "-29.580400,-50.790200|-29.583300,-50.792900|-29.586100,-50.795500"
  "-29.577900,-50.800800|-29.580400,-50.804900|-29.582800,-50.808600"
)

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to parse JSON responses." >&2
  exit 1
fi

json_get() {
  local path="$1"
  local payload
  payload="$(cat)"
  python3 - "$path" "$payload" <<'PY'
import json
import sys

path = sys.argv[1].split(".")
payload = json.loads(sys.argv[2])
value = payload
for part in path:
    if part.isdigit():
        value = value[int(part)]
    else:
        value = value[part]
if isinstance(value, bool):
    print("true" if value else "false")
elif value is None:
    print("")
else:
    print(value)
PY
}

today_day_name() {
  python3 - "$TODAY" <<'PY'
from datetime import datetime
import sys

date = datetime.strptime(sys.argv[1], "%Y-%m-%d").date()
print(date.strftime("%A").upper())
PY
}

request() {
  local method="$1"
  local path="$2"
  local auth_mode="${3:-none}"
  local payload="${4:-}"
  local auth_email="${5:-}"
  local auth_password="${6:-}"

  local -a curl_args=(
    --silent
    --show-error
    -X "$method"
    "$BASE_URL$path"
    -H "Content-Type: application/json"
  )

  if curl --help all 2>/dev/null | grep -q -- '--fail-with-body'; then
    curl_args+=(--fail-with-body)
  else
    curl_args+=(--fail)
  fi

  case "$auth_mode" in
    admin)
      curl_args+=(-u "${ADMIN_EMAIL}:${ADMIN_PASSWORD}")
      ;;
    custom-basic)
      curl_args+=(-u "${auth_email}:${auth_password}")
      ;;
  esac

  if [[ -n "$payload" ]]; then
    curl_args+=(-d "$payload")
  fi

  curl "${curl_args[@]}"
}

TODAY_DAY="$(today_day_name)"

vanrides_fixture_preflight_backend "$BASE_URL" "igrlab"

echo "[igrlab] Bootstrap company admin for new company ${COMPANY_NAME}..."
bootstrap_response="$(
  request POST "/company-admins/bootstrap" none "{
    \"nome\":\"Igrejinha Lab ${SHORT_TS}\",
    \"email\":\"${ADMIN_EMAIL}\",
    \"telefone\":\"5199000${SHORT_TS: -4}\",
    \"senha\":\"${ADMIN_PASSWORD}\",
    \"companyName\":\"${COMPANY_NAME}\"
  }"
)"

company_id="$(printf '%s' "$bootstrap_response" | json_get "companyId")"
admin_id="$(printf '%s' "$bootstrap_response" | json_get "id")"
COMPANY_SLUG="$(printf '%s' "$bootstrap_response" | json_get "companySlug")"
admin_email_verified="$(printf '%s' "$bootstrap_response" | json_get "emailVerificado")"
if [[ "$admin_email_verified" != "true" ]]; then
  printf '{"code":"EMAIL_NOT_VERIFIED","message":"Admin bootstrap da fixture Igrejinha nasceu com e-mail pendente; configure VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS=vanrides.dev no ambiente alvo.","email":"%s"}\n' "$ADMIN_EMAIL" >&2
  exit 86
fi

declare -a DRIVER_IDS=()
declare -a DRIVER_EMAILS=()
declare -a ROUTE_IDS=()
declare -a TRIP_IDS=()
declare -a PASSENGER_SUMMARIES=()
declare -a DRIVER_TRIP_ENDPOINTS=()

GUARDIAN_NAME="R1"
GUARDIAN_EMAIL="r1.${RUN_ID}@vanrides.dev"
GUARDIAN_PASSAGEIRO_ID=""
GUARDIAN_PASSAGEIRO_NAME=""
GUARDIAN_PASSAGEIRO_EMAIL=""

create_passenger() {
  local driver_id="$1"
  local passenger_name="$2"
  local passenger_email="$3"
  local lat="$4"
  local lng="$5"

  request POST "/company-admins/passageiros" admin "{
    \"nome\":\"${passenger_name}\",
    \"email\":\"${passenger_email}\",
    \"telefone\":\"5197${RANDOM:0:4}${SHORT_TS: -2}\",
    \"endereco\":\"Ponto ${passenger_name}\",
    \"bairro\":\"Igrejinha QA\",
    \"cidade\":\"Igrejinha\",
    \"estado\":\"RS\",
    \"cep\":\"95650-000\",
    \"senha\":\"${PASSENGER_PASSWORD}\",
    \"menorDeIdade\":false,
    \"motoristaId\":${driver_id},
    \"latitude\":${lat},
    \"longitude\":${lng}
  }"
}

for index in "${!DRIVER_NAMES[@]}"; do
  ordinal=$((index + 1))
  driver_name="${DRIVER_NAMES[$index]}"
  route_name="${ROUTE_NAMES[$index]}"
  driver_email="m${ordinal}.${RUN_ID}@vanrides.dev"

  echo "[igrlab] Creating driver ${driver_name}..."
  driver_response="$(
    request POST "/company-admins/motoristas" admin "{
      \"nome\":\"${driver_name}\",
      \"email\":\"${driver_email}\",
      \"telefone\":\"5198${ordinal}${SHORT_TS: -3}\",
      \"senha\":\"${DRIVER_PASSWORD}\"
    }"
  )"
  driver_id="$(printf '%s' "$driver_response" | json_get "id")"
  DRIVER_IDS+=("${driver_id}")
  DRIVER_EMAILS+=("${driver_email}")

  passenger_ids=()
  passenger_summary_parts=()
  IFS='|' read -r -a passenger_points <<<"${PASSENGER_COORDS[$index]}"
  for stop_index in "${!passenger_points[@]}"; do
    stop_ordinal=$((stop_index + 1))
    passenger_name="P${ordinal}${stop_ordinal}"
    passenger_email="p${ordinal}${stop_ordinal}.${RUN_ID}@vanrides.dev"
    IFS=',' read -r passenger_lat passenger_lng <<<"${passenger_points[$stop_index]}"

    passenger_response="$(
      create_passenger \
        "${driver_id}" \
        "${passenger_name}" \
        "${passenger_email}" \
        "${passenger_lat}" \
        "${passenger_lng}"
    )"
    passenger_id="$(printf '%s' "$passenger_response" | json_get "id")"
    passenger_ids+=("${passenger_id}")
    passenger_summary_parts+=("${passenger_name}:${passenger_email}:${passenger_id}")

    if [[ -z "$GUARDIAN_PASSAGEIRO_ID" ]]; then
      GUARDIAN_PASSAGEIRO_ID="${passenger_id}"
      GUARDIAN_PASSAGEIRO_NAME="${passenger_name}"
      GUARDIAN_PASSAGEIRO_EMAIL="${passenger_email}"
    fi

    request POST "/presencas" admin "{
      \"passageiro\":{\"id\":${passenger_id}},
      \"dataPresenca\":\"${TODAY}\",
      \"confirmado\":true
    }" >/dev/null
  done

  passenger_ids_json="$(IFS=,; printf '%s' "${passenger_ids[*]}")"
  rota_response="$(
    request POST "/company-admins/rotas-base" admin "{
      \"nome\":\"${route_name}\",
      \"motoristaId\":${driver_id},
      \"passageiroIds\":[${passenger_ids_json}],
      \"geracaoAutomaticaAtiva\":true,
      \"diasOperacao\":[\"${TODAY_DAY}\"]
    }"
  )"
  rota_id="$(printf '%s' "$rota_response" | json_get "id")"
  ROUTE_IDS+=("${rota_id}")

  trip_response="$(request GET "/viagens/motorista/${driver_id}/hoje?data=${TODAY}" admin)"
  trip_id="$(printf '%s' "$trip_response" | json_get "id")"
  TRIP_IDS+=("${trip_id}")

  IFS=',' read -r driver_lat driver_lng <<<"${DRIVER_ANCHORS[$index]}"
  request \
    POST \
    "/api/localizacao/${driver_id}" \
    custom-basic \
    "{
      \"latitude\":${driver_lat},
      \"longitude\":${driver_lng}
    }" \
    "${driver_email}" \
    "${DRIVER_PASSWORD}" >/dev/null

  PASSENGER_SUMMARIES+=("$(IFS=';'; printf '%s' "${passenger_summary_parts[*]}")")
  DRIVER_TRIP_ENDPOINTS+=("/viagens/motorista/${driver_id}/hoje?data=${TODAY}")
done

echo "[igrlab] Creating guardian ${GUARDIAN_NAME} linked to ${GUARDIAN_PASSAGEIRO_NAME}..."
guardian_response="$(
  request POST "/company-admins/responsaveis" admin "{
    \"nome\":\"${GUARDIAN_NAME}\",
    \"email\":\"${GUARDIAN_EMAIL}\",
    \"telefone\":\"5196${SHORT_TS}\",
    \"senha\":\"${GUARDIAN_PASSWORD}\"
  }"
)"
guardian_id="$(printf '%s' "$guardian_response" | json_get "id")"

request POST "/company-admins/responsaveis/${guardian_id}/passageiros" admin "{
  \"passageiroId\":${GUARDIAN_PASSAGEIRO_ID}
}" >/dev/null

cat <<EOF
[igrlab] Fixture ready
base_url=${BASE_URL}
fixture_date=${TODAY}
company_id=${company_id}
admin_id=${admin_id}
company_slug=${COMPANY_SLUG}
admin_email=${ADMIN_EMAIL}
admin_password=${ADMIN_PASSWORD}
driver_password=${DRIVER_PASSWORD}
passenger_password=${PASSENGER_PASSWORD}
guardian_id=${guardian_id}
guardian_name=${GUARDIAN_NAME}
guardian_email=${GUARDIAN_EMAIL}
guardian_password=${GUARDIAN_PASSWORD}
guardian_passageiro_id=${GUARDIAN_PASSAGEIRO_ID}
guardian_passageiro_name=${GUARDIAN_PASSAGEIRO_NAME}
guardian_passageiro_email=${GUARDIAN_PASSAGEIRO_EMAIL}
concept=current-model-validates-pickup-journeys-only; shared-destination-hub-is-not-modeled-yet

[igrlab] Drivers
EOF

for index in "${!DRIVER_NAMES[@]}"; do
  ordinal=$((index + 1))
  printf 'driver_%s_name=%s\n' "${ordinal}" "${DRIVER_NAMES[$index]}"
  printf 'driver_%s_email=%s\n' "${ordinal}" "${DRIVER_EMAILS[$index]}"
  printf 'driver_%s_id=%s\n' "${ordinal}" "${DRIVER_IDS[$index]}"
  printf 'driver_%s_trip_id=%s\n' "${ordinal}" "${TRIP_IDS[$index]}"
  printf 'driver_%s_route_base_id=%s\n' "${ordinal}" "${ROUTE_IDS[$index]}"
  printf 'driver_%s_trip_get=%s\n' "${ordinal}" "${DRIVER_TRIP_ENDPOINTS[$index]}"
  printf 'driver_%s_passengers=%s\n' "${ordinal}" "${PASSENGER_SUMMARIES[$index]}"
done

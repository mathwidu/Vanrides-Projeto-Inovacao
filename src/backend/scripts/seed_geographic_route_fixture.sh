#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
FIXTURE_PREFIX="${FIXTURE_PREFIX:-igrejinha}"
TODAY="${TODAY:-$(date +%F)}"
TS="$(date +%Y%m%d%H%M%S)"
RUN_ID="${TS}-$$"
COMPANY_NAME="QA Geo ${FIXTURE_PREFIX} ${RUN_ID}"

ADMIN_EMAIL="qa.geo.${FIXTURE_PREFIX}.${RUN_ID}@vanrides.dev"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-SyntheticLab123!}"
DRIVER_EMAIL="driver.geo.${FIXTURE_PREFIX}.${RUN_ID}@vanrides.dev"
DRIVER_PASSWORD="${DRIVER_PASSWORD:-Driver@123}"

# Pontos publicos/aproximados na regiao de Igrejinha para QA visual.
DRIVER_ANCHOR_LAT="${DRIVER_ANCHOR_LAT:--29.572800}"
DRIVER_ANCHOR_LNG="${DRIVER_ANCHOR_LNG:--50.791500}"
PASSAGEIRO_1_LAT="${PASSAGEIRO_1_LAT:--29.575500}"
PASSAGEIRO_1_LNG="${PASSAGEIRO_1_LNG:--50.786900}"
PASSAGEIRO_2_LAT="${PASSAGEIRO_2_LAT:--29.568900}"
PASSAGEIRO_2_LNG="${PASSAGEIRO_2_LNG:--50.781800}"
PASSAGEIRO_3_LAT="${PASSAGEIRO_3_LAT:--29.579400}"
PASSAGEIRO_3_LNG="${PASSAGEIRO_3_LNG:--50.799800}"

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

request() {
  local method="$1"
  local path="$2"
  local auth_mode="${3:-none}"
  local payload="${4:-}"

  local -a curl_args=(
    --silent
    --show-error
    --fail-with-body
    -X "$method"
    "$BASE_URL$path"
    -H "Content-Type: application/json"
  )

  case "$auth_mode" in
    admin)
      curl_args+=(-u "${ADMIN_EMAIL}:${ADMIN_PASSWORD}")
      ;;
    driver)
      curl_args+=(-u "${DRIVER_EMAIL}:${DRIVER_PASSWORD}")
      ;;
  esac

  if [[ -n "$payload" ]]; then
    curl_args+=(-d "$payload")
  fi

  curl "${curl_args[@]}"
}

today_day_name() {
  python3 - "$TODAY" <<'PY'
from datetime import datetime
import sys
date = datetime.strptime(sys.argv[1], "%Y-%m-%d").date()
print(date.strftime("%A").upper())
PY
}

TODAY_DAY="$(today_day_name)"

echo "[geo-seed] Bootstrap company admin for new company ${COMPANY_NAME}..."
bootstrap_response="$(
  request POST "/company-admins/bootstrap" none "{
    \"nome\":\"QA Geo ${FIXTURE_PREFIX} ${TS}\",
    \"email\":\"${ADMIN_EMAIL}\",
    \"telefone\":\"5199999${TS: -4}\",
    \"senha\":\"${ADMIN_PASSWORD}\",
    \"companyName\":\"${COMPANY_NAME}\"
  }"
)"

company_id="$(printf '%s' "$bootstrap_response" | json_get "companyId")"
admin_id="$(printf '%s' "$bootstrap_response" | json_get "id")"
COMPANY_SLUG="$(printf '%s' "$bootstrap_response" | json_get "companySlug")"

echo "[geo-seed] Creating driver..."
driver_response="$(
  request POST "/company-admins/motoristas" admin "{
    \"nome\":\"Motorista Geo ${FIXTURE_PREFIX}\",
    \"email\":\"${DRIVER_EMAIL}\",
    \"telefone\":\"5198888${TS: -4}\",
    \"senha\":\"${DRIVER_PASSWORD}\"
  }"
)"
driver_id="$(printf '%s' "$driver_response" | json_get "id")"

create_passenger() {
  local ordinal="$1"
  local lat="$2"
  local lng="$3"

  request POST "/company-admins/passageiros" admin "{
    \"nome\":\"Passageiro Geo ${ordinal}\",
    \"email\":\"passageiro.geo.${ordinal}.${FIXTURE_PREFIX}.${RUN_ID}@vanrides.dev\",
    \"telefone\":\"519777${ordinal}${TS: -3}\",
    \"endereco\":\"Ponto geografico ${ordinal}\",
    \"bairro\":\"Igrejinha QA\",
    \"cidade\":\"Igrejinha\",
    \"estado\":\"RS\",
    \"cep\":\"95650-000\",
    \"senha\":\"Passenger@123\",
    \"menorDeIdade\":false,
    \"motoristaId\":${driver_id},
    \"latitude\":${lat},
    \"longitude\":${lng}
  }"
}

echo "[geo-seed] Creating passengers..."
passageiro_1_response="$(create_passenger 1 "$PASSAGEIRO_1_LAT" "$PASSAGEIRO_1_LNG")"
passageiro_2_response="$(create_passenger 2 "$PASSAGEIRO_2_LAT" "$PASSAGEIRO_2_LNG")"
passageiro_3_response="$(create_passenger 3 "$PASSAGEIRO_3_LAT" "$PASSAGEIRO_3_LNG")"

passageiro_1_id="$(printf '%s' "$passageiro_1_response" | json_get "id")"
passageiro_2_id="$(printf '%s' "$passageiro_2_response" | json_get "id")"
passageiro_3_id="$(printf '%s' "$passageiro_3_response" | json_get "id")"

echo "[geo-seed] Creating recurring route base..."
rota_response="$(
  request POST "/company-admins/rotas-base" admin "{
    \"nome\":\"Rota Geo ${FIXTURE_PREFIX} ${TODAY}\",
    \"motoristaId\":${driver_id},
    \"passageiroIds\":[${passageiro_1_id},${passageiro_2_id},${passageiro_3_id}],
    \"geracaoAutomaticaAtiva\":true,
    \"diasOperacao\":[\"${TODAY_DAY}\"]
  }"
)"
rota_id="$(printf '%s' "$rota_response" | json_get "id")"

echo "[geo-seed] Confirming presence for today..."
request POST "/presencas" admin "{
  \"passageiro\":{\"id\":${passageiro_1_id}},
  \"dataPresenca\":\"${TODAY}\",
  \"confirmado\":true
}" >/dev/null
request POST "/presencas" admin "{
  \"passageiro\":{\"id\":${passageiro_2_id}},
  \"dataPresenca\":\"${TODAY}\",
  \"confirmado\":true
}" >/dev/null
request POST "/presencas" admin "{
  \"passageiro\":{\"id\":${passageiro_3_id}},
  \"dataPresenca\":\"${TODAY}\",
  \"confirmado\":true
}" >/dev/null

echo "[geo-seed] Materializing automatic trip for today..."
trip_response="$(request GET "/viagens/motorista/${driver_id}/hoje?data=${TODAY}" admin)"
trip_id="$(printf '%s' "$trip_response" | json_get "id")"
provider="$(printf '%s' "$trip_response" | json_get "rotaGeografica.provider")"

echo "[geo-seed] Publishing anchor driver location..."
request POST "/api/localizacao/${driver_id}" driver "{
  \"latitude\":${DRIVER_ANCHOR_LAT},
  \"longitude\":${DRIVER_ANCHOR_LNG}
}" >/dev/null

trip_with_location="$(request GET "/viagens/motorista/${driver_id}/hoje?data=${TODAY}" admin)"

cat <<EOF
[geo-seed] Fixture ready
base_url=${BASE_URL}
company_id=${company_id}
admin_id=${admin_id}
company_slug=${COMPANY_SLUG}
driver_id=${driver_id}
driver_email=${DRIVER_EMAIL}
driver_password=${DRIVER_PASSWORD}
trip_id=${trip_id}
route_base_id=${rota_id}
provider_initial=${provider}
passengers=${passageiro_1_id},${passageiro_2_id},${passageiro_3_id}
operational_get=/viagens/motorista/${driver_id}/hoje?data=${TODAY}
passenger_get_1=/viagens/passageiro/${passageiro_1_id}/atual?data=${TODAY}
passenger_get_2=/viagens/passageiro/${passageiro_2_id}/atual?data=${TODAY}
passenger_get_3=/viagens/passageiro/${passageiro_3_id}/atual?data=${TODAY}

[geo-seed] Driver trip payload with anchor location:
${trip_with_location}
EOF

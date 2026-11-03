#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
LAB_PREFIX="${LAB_PREFIX:-opday}"
TODAY="${TODAY:-$(TZ=UTC date +%F)}"
TS="$(date +%Y%m%d%H%M%S)"
SHORT_TS="${TS: -6}"
RUN_ID="${TS}-$$"

ADMIN_EMAIL="op.${LAB_PREFIX}.${RUN_ID}@vanrides.dev"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-SyntheticLab123!}"
DRIVER_PASSWORD="${DRIVER_PASSWORD:-Driver@123}"
PASSENGER_PASSWORD="${PASSENGER_PASSWORD:-Passenger@123}"
GUARDIAN_PASSWORD="${GUARDIAN_PASSWORD:-Resp@123}"
VANRIDES_FIXTURE_STORY_MODE="${VANRIDES_FIXTURE_STORY_MODE:-operational_qa}"

if [[ "$VANRIDES_FIXTURE_STORY_MODE" == "product_video" ]]; then
  DEFAULT_COMPANY_NAME="Transporte Igrejinha Feevale"
  DEFAULT_DRIVER_1_NAME="Carlos Schneider"
  DEFAULT_DRIVER_2_NAME="Marina Oliveira"
  DEFAULT_PASSENGER_MAIN_NAME="Eduardo Becker"
  DEFAULT_PASSENGER_SUPPORT_NAME="Luiza Hoffmann"
  DEFAULT_GUARDIAN_NAME="Ana Becker"
  DEFAULT_SERVICE_MORNING_NAME="Igrejinha para Feevale"
  DEFAULT_SERVICE_AFTERNOON_NAME="Feevale para Igrejinha"
  DEFAULT_SERVICE_NIGHT_NAME="Atividade extra Feevale"
  DEFAULT_DESTINATION_NAME="Universidade Feevale"
  DEFAULT_DESTINATION_ADDRESS="Universidade Feevale Campus II, ERS-239, 2755 - Vila Nova, Novo Hamburgo - RS"
  DEFAULT_PASSENGER_MAIN_ADDRESS="Prefeitura Municipal de Igrejinha, Av. Ildo Meneghetti, 757 - XV de Novembro, Igrejinha - RS"
  DEFAULT_PASSENGER_SUPPORT_ADDRESS="Alles Blau Shopping, Av. Pres. Tancredo Neves, 5676 - Casa de Pedra, Igrejinha - RS"
  # Pontos publicos/aproximados para gravacao: garagem/paradas em Igrejinha e destino na Feevale.
  DEFAULT_DRIVER_1_LAT="-29.574800"
  DEFAULT_DRIVER_1_LNG="-50.790900"
  DEFAULT_DRIVER_2_LAT="-29.569500"
  DEFAULT_DRIVER_2_LNG="-50.783700"
  DEFAULT_PASSENGER_MAIN_LAT="-29.575300"
  DEFAULT_PASSENGER_MAIN_LNG="-50.787100"
  DEFAULT_PASSENGER_SUPPORT_LAT="-29.573000"
  DEFAULT_PASSENGER_SUPPORT_LNG="-50.784200"
  DEFAULT_DESTINATION_LAT="-29.665083"
  DEFAULT_DESTINATION_LNG="-51.119017"
else
  DEFAULT_COMPANY_NAME="Operacional Day ${SHORT_TS}"
  DEFAULT_DRIVER_1_NAME="M1 Operacional"
  DEFAULT_DRIVER_2_NAME="M2 Reserva"
  DEFAULT_PASSENGER_MAIN_NAME="P1 Multi Serviço"
  DEFAULT_PASSENGER_SUPPORT_NAME="P2 Apoio Ausência"
  DEFAULT_GUARDIAN_NAME="R1 Operacional"
  DEFAULT_SERVICE_MORNING_NAME="Linha Manhã - Escola"
  DEFAULT_SERVICE_AFTERNOON_NAME="Linha Tarde - Retorno"
  DEFAULT_SERVICE_NIGHT_NAME="Linha Noite - Atividade"
  DEFAULT_DESTINATION_NAME="Escola QA Igrejinha"
  DEFAULT_DESTINATION_ADDRESS="Destino operacional QA, Igrejinha - RS"
  DEFAULT_PASSENGER_MAIN_ADDRESS="Ponto P1 Multi Serviço"
  DEFAULT_PASSENGER_SUPPORT_ADDRESS="Ponto P2 Apoio Ausência"
  # Pontos publicos/aproximados na regiao de Igrejinha para QA visual.
  DEFAULT_DRIVER_1_LAT="-29.574800"
  DEFAULT_DRIVER_1_LNG="-50.790900"
  DEFAULT_DRIVER_2_LAT="-29.569500"
  DEFAULT_DRIVER_2_LNG="-50.783700"
  DEFAULT_PASSENGER_MAIN_LAT="-29.575300"
  DEFAULT_PASSENGER_MAIN_LNG="-50.787100"
  DEFAULT_PASSENGER_SUPPORT_LAT="-29.573000"
  DEFAULT_PASSENGER_SUPPORT_LNG="-50.784200"
  DEFAULT_DESTINATION_LAT="-29.571100"
  DEFAULT_DESTINATION_LNG="-50.781400"
fi

COMPANY_NAME="${COMPANY_NAME:-$DEFAULT_COMPANY_NAME} ${RUN_ID}"
DRIVER_1_NAME="${DRIVER_1_NAME:-$DEFAULT_DRIVER_1_NAME}"
DRIVER_2_NAME="${DRIVER_2_NAME:-$DEFAULT_DRIVER_2_NAME}"
PASSENGER_MAIN_NAME="${PASSENGER_MAIN_NAME:-$DEFAULT_PASSENGER_MAIN_NAME}"
PASSENGER_SUPPORT_NAME="${PASSENGER_SUPPORT_NAME:-$DEFAULT_PASSENGER_SUPPORT_NAME}"
GUARDIAN_NAME="${GUARDIAN_NAME:-$DEFAULT_GUARDIAN_NAME}"
SERVICE_MORNING_NAME="${SERVICE_MORNING_NAME:-$DEFAULT_SERVICE_MORNING_NAME}"
SERVICE_AFTERNOON_NAME="${SERVICE_AFTERNOON_NAME:-$DEFAULT_SERVICE_AFTERNOON_NAME}"
SERVICE_NIGHT_NAME="${SERVICE_NIGHT_NAME:-$DEFAULT_SERVICE_NIGHT_NAME}"
DESTINATION_NAME="${DESTINATION_NAME:-$DEFAULT_DESTINATION_NAME}"
DESTINATION_ADDRESS="${DESTINATION_ADDRESS:-$DEFAULT_DESTINATION_ADDRESS}"
PASSENGER_MAIN_ADDRESS="${PASSENGER_MAIN_ADDRESS:-$DEFAULT_PASSENGER_MAIN_ADDRESS}"
PASSENGER_SUPPORT_ADDRESS="${PASSENGER_SUPPORT_ADDRESS:-$DEFAULT_PASSENGER_SUPPORT_ADDRESS}"
DRIVER_1_LAT="${DRIVER_1_LAT:-$DEFAULT_DRIVER_1_LAT}"
DRIVER_1_LNG="${DRIVER_1_LNG:-$DEFAULT_DRIVER_1_LNG}"
DRIVER_2_LAT="${DRIVER_2_LAT:-$DEFAULT_DRIVER_2_LAT}"
DRIVER_2_LNG="${DRIVER_2_LNG:-$DEFAULT_DRIVER_2_LNG}"
PASSENGER_MAIN_LAT="${PASSENGER_MAIN_LAT:-$DEFAULT_PASSENGER_MAIN_LAT}"
PASSENGER_MAIN_LNG="${PASSENGER_MAIN_LNG:-$DEFAULT_PASSENGER_MAIN_LNG}"
PASSENGER_SUPPORT_LAT="${PASSENGER_SUPPORT_LAT:-$DEFAULT_PASSENGER_SUPPORT_LAT}"
PASSENGER_SUPPORT_LNG="${PASSENGER_SUPPORT_LNG:-$DEFAULT_PASSENGER_SUPPORT_LNG}"
DESTINATION_LAT="${DESTINATION_LAT:-$DEFAULT_DESTINATION_LAT}"
DESTINATION_LNG="${DESTINATION_LNG:-$DEFAULT_DESTINATION_LNG}"

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

json_count() {
  local path="${1:-}"
  local payload
  payload="$(cat)"
  python3 - "$path" "$payload" <<'PY'
import json
import sys

path = [part for part in sys.argv[1].split(".") if part]
value = json.loads(sys.argv[2])
for part in path:
    if part.isdigit():
        value = value[int(part)]
    else:
        value = value[part]
print(len(value))
PY
}

json_select() {
  local path="$1"
  local field="$2"
  local expected="$3"
  local output_field="$4"
  local payload
  payload="$(cat)"
  python3 - "$path" "$field" "$expected" "$output_field" "$payload" <<'PY'
import json
import sys

path, field, expected, output_field = sys.argv[1:5]
payload = json.loads(sys.argv[5])
value = payload
for part in [part for part in path.split(".") if part]:
    if part.isdigit():
        value = value[int(part)]
    else:
        value = value[part]
for item in value:
    if str(item.get(field)) == expected:
        selected = item.get(output_field)
        if selected is None:
            print("")
        else:
            print(selected)
        raise SystemExit(0)
raise SystemExit(1)
PY
}

shell_quote() {
  printf '%q' "$1"
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

  local response_file
  response_file="$(mktemp "${TMPDIR:-/tmp}/vanrides-opday-response.XXXXXX")"

  set +e
  curl "${curl_args[@]}" --output "$response_file"
  local curl_code=$?
  set -e
  if [[ "$curl_code" -ne 0 ]]; then
    if [[ -s "$response_file" ]]; then
      cat "$response_file" >&2
      printf '\n' >&2
    fi
    rm -f "$response_file"
    return "$curl_code"
  fi

  cat "$response_file"
  rm -f "$response_file"
}

create_driver() {
  local name="$1"
  local email="$2"
  request POST "/company-admins/motoristas" admin "{
    \"nome\":\"${name}\",
    \"email\":\"${email}\",
    \"telefone\":\"5198${RANDOM:0:4}${SHORT_TS: -2}\",
    \"senha\":\"${DRIVER_PASSWORD}\"
  }"
}

create_passenger() {
  local name="$1"
  local email="$2"
  local driver_id="$3"
  local lat="$4"
  local lng="$5"
  local address="${6:-Ponto ${name}}"

  request POST "/company-admins/passageiros" admin "{
    \"nome\":\"${name}\",
    \"email\":\"${email}\",
    \"telefone\":\"5197${RANDOM:0:4}${SHORT_TS: -2}\",
    \"endereco\":\"${address}\",
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

create_service() {
  local name="$1"
  local type="$2"
  local time="$3"
  local driver_id="$4"
  local passenger_ids_json="$5"

  local motorista_field=""
  if [[ -n "$driver_id" ]]; then
    motorista_field="\"motoristaPadraoId\":${driver_id},"
  fi

  request POST "/company-admins/servicos-recorrentes" admin "{
    \"nome\":\"${name}\",
    ${motorista_field}
    \"tipoOperacao\":\"${type}\",
    \"politicaPresenca\":\"EXIGE_CONFIRMACAO\",
    \"geracaoAutomaticaAtiva\":true,
    \"diasOperacao\":[\"${TODAY_DAY}\"],
    \"horarioPartida\":\"${time}\",
    \"destinoFinalNome\":\"${DESTINATION_NAME}\",
    \"destinoFinalEndereco\":\"${DESTINATION_ADDRESS}\",
    \"destinoFinalLatitude\":${DESTINATION_LAT},
    \"destinoFinalLongitude\":${DESTINATION_LNG},
    \"capacidadePlanejada\":12,
    \"passageiroIds\":[${passenger_ids_json}]
  }"
}

mark_service_presence() {
  local service_id="$1"
  local passenger_id="$2"
  local status="$3"

  request POST "/presencas-servico" admin "{
    \"servicoId\":${service_id},
    \"passageiroId\":${passenger_id},
    \"dataOperacao\":\"${TODAY}\",
    \"status\":\"${status}\"
  }" >/dev/null
}

TODAY_DAY="$(today_day_name)"

DRIVER_1_EMAIL="op.m1.${RUN_ID}@vanrides.dev"
DRIVER_2_EMAIL="op.m2.${RUN_ID}@vanrides.dev"
PASSENGER_MAIN_EMAIL="op.p1.${RUN_ID}@vanrides.dev"
PASSENGER_SUPPORT_EMAIL="op.p2.${RUN_ID}@vanrides.dev"
GUARDIAN_EMAIL="op.r1.${RUN_ID}@vanrides.dev"

echo "[opday] Bootstrap company admin for new company ${COMPANY_NAME}..."
bootstrap_response="$(
  request POST "/company-admins/bootstrap" none "{
    \"nome\":\"${COMPANY_NAME}\",
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
  printf '{"code":"EMAIL_NOT_VERIFIED","message":"Admin bootstrap da fixture operacional nasceu com e-mail pendente; configure VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS=vanrides.dev no ambiente alvo.","email":"%s"}\n' "$ADMIN_EMAIL" >&2
  exit 86
fi

echo "[opday] Creating drivers..."
driver_1_response="$(create_driver "$DRIVER_1_NAME" "$DRIVER_1_EMAIL")"
driver_2_response="$(create_driver "$DRIVER_2_NAME" "$DRIVER_2_EMAIL")"
driver_1_id="$(printf '%s' "$driver_1_response" | json_get "id")"
driver_2_id="$(printf '%s' "$driver_2_response" | json_get "id")"

echo "[opday] Creating passengers..."
passenger_main_response="$(create_passenger "$PASSENGER_MAIN_NAME" "$PASSENGER_MAIN_EMAIL" "$driver_1_id" "$PASSENGER_MAIN_LAT" "$PASSENGER_MAIN_LNG" "$PASSENGER_MAIN_ADDRESS")"
passenger_support_response="$(create_passenger "$PASSENGER_SUPPORT_NAME" "$PASSENGER_SUPPORT_EMAIL" "$driver_1_id" "$PASSENGER_SUPPORT_LAT" "$PASSENGER_SUPPORT_LNG" "$PASSENGER_SUPPORT_ADDRESS")"
passenger_main_id="$(printf '%s' "$passenger_main_response" | json_get "id")"
passenger_support_id="$(printf '%s' "$passenger_support_response" | json_get "id")"
passenger_ids_json="${passenger_main_id},${passenger_support_id}"

echo "[opday] Creating recurring services for morning, afternoon and night..."
service_morning_response="$(create_service "$SERVICE_MORNING_NAME" "IDA" "07:00:00" "$driver_1_id" "$passenger_ids_json")"
service_afternoon_response="$(create_service "$SERVICE_AFTERNOON_NAME" "VOLTA" "13:00:00" "$driver_1_id" "$passenger_ids_json")"
service_night_response="$(create_service "$SERVICE_NIGHT_NAME" "CIRCULAR" "19:00:00" "" "$passenger_ids_json")"
service_morning_id="$(printf '%s' "$service_morning_response" | json_get "id")"
service_afternoon_id="$(printf '%s' "$service_afternoon_response" | json_get "id")"
service_night_id="$(printf '%s' "$service_night_response" | json_get "id")"

echo "[opday] Marking presence decisions per service/date..."
mark_service_presence "$service_morning_id" "$passenger_main_id" "CONFIRMED"
mark_service_presence "$service_morning_id" "$passenger_support_id" "CONFIRMED"
mark_service_presence "$service_afternoon_id" "$passenger_main_id" "CONFIRMED"
mark_service_presence "$service_afternoon_id" "$passenger_support_id" "ABSENT"
mark_service_presence "$service_night_id" "$passenger_main_id" "CONFIRMED"
mark_service_presence "$service_night_id" "$passenger_support_id" "ABSENT"

echo "[opday] Publishing driver anchors..."
request POST "/api/localizacao/${driver_1_id}" custom-basic "{
  \"latitude\":${DRIVER_1_LAT},
  \"longitude\":${DRIVER_1_LNG}
}" "$DRIVER_1_EMAIL" "$DRIVER_PASSWORD" >/dev/null
request POST "/api/localizacao/${driver_2_id}" custom-basic "{
  \"latitude\":${DRIVER_2_LAT},
  \"longitude\":${DRIVER_2_LNG}
}" "$DRIVER_2_EMAIL" "$DRIVER_PASSWORD" >/dev/null

echo "[opday] Generating automatic trips..."
bulk_response="$(request POST "/company-admins/viagens/gerar-automaticas" admin "{
  \"dataOperacao\":\"${TODAY}\"
}")"
trip_morning_id="$(printf '%s' "$bulk_response" | json_select "viagens" "servicoId" "$service_morning_id" "id")"
trip_afternoon_id="$(printf '%s' "$bulk_response" | json_select "viagens" "servicoId" "$service_afternoon_id" "id")"
night_ignored_motivo="$(printf '%s' "$bulk_response" | json_select "servicosIgnorados" "servicoId" "$service_night_id" "motivo")"
if [[ "$night_ignored_motivo" != "SEM_MOTORISTA" ]]; then
  echo "Expected night service to be ignored as SEM_MOTORISTA, got '${night_ignored_motivo}'." >&2
  exit 1
fi

echo "[opday] Reassigning afternoon trip to reserve driver..."
trip_afternoon_detail="$(request PUT "/company-admins/viagens/${trip_afternoon_id}/motorista" admin "{
  \"motoristaId\":${driver_2_id}
}")"
trip_afternoon_driver_id="$(printf '%s' "$trip_afternoon_detail" | json_get "motorista.id")"
if [[ "$trip_afternoon_driver_id" != "$driver_2_id" ]]; then
  echo "Expected afternoon trip driver ${driver_2_id}, got ${trip_afternoon_driver_id}." >&2
  exit 1
fi

echo "[opday] Creating night trip manually with reserve driver..."
trip_night_response="$(request POST "/company-admins/viagens" admin "{
  \"servicoId\":${service_night_id},
  \"motoristaId\":${driver_2_id},
  \"dataOperacao\":\"${TODAY}\"
}")"
trip_night_id="$(printf '%s' "$trip_night_response" | json_get "id")"

echo "[opday] Starting morning trip to materialize the operational route snapshot..."
trip_morning_started="$(request POST "/viagens/${trip_morning_id}/iniciar" custom-basic "" "$DRIVER_1_EMAIL" "$DRIVER_PASSWORD")"
trip_morning_status="$(printf '%s' "$trip_morning_started" | json_get "status")"
trip_morning_snapshot="$(printf '%s' "$trip_morning_started" | json_get "rotaGeografica.snapshotOperacional")"

passenger_services="$(request GET "/passageiros/${passenger_main_id}/servicos-disponiveis?data=${TODAY}" custom-basic "" "$PASSENGER_MAIN_EMAIL" "$PASSENGER_PASSWORD")"
passenger_day_trips="$(request GET "/viagens/passageiro/${passenger_main_id}/dia?data=${TODAY}" custom-basic "" "$PASSENGER_MAIN_EMAIL" "$PASSENGER_PASSWORD")"
support_day_trips="$(request GET "/viagens/passageiro/${passenger_support_id}/dia?data=${TODAY}" custom-basic "" "$PASSENGER_SUPPORT_EMAIL" "$PASSENGER_PASSWORD")"
driver_1_day_trips="$(request GET "/viagens/motorista/${driver_1_id}/dia?data=${TODAY}" custom-basic "" "$DRIVER_1_EMAIL" "$DRIVER_PASSWORD")"
driver_2_day_trips="$(request GET "/viagens/motorista/${driver_2_id}/dia?data=${TODAY}" custom-basic "" "$DRIVER_2_EMAIL" "$DRIVER_PASSWORD")"

passenger_services_count="$(printf '%s' "$passenger_services" | json_count)"
passenger_day_trip_count="$(printf '%s' "$passenger_day_trips" | json_count)"
support_day_trip_count="$(printf '%s' "$support_day_trips" | json_count)"
driver_1_day_trip_count="$(printf '%s' "$driver_1_day_trips" | json_count)"
driver_2_day_trip_count="$(printf '%s' "$driver_2_day_trips" | json_count)"

echo "[opday] Creating guardian linked to ${PASSENGER_MAIN_EMAIL}..."
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
  \"passageiroId\":${passenger_main_id}
}" >/dev/null

guardian_day_trips="$(request GET "/viagens/passageiro/${passenger_main_id}/dia?data=${TODAY}" custom-basic "" "$GUARDIAN_EMAIL" "$GUARDIAN_PASSWORD")"
guardian_day_trip_count="$(printf '%s' "$guardian_day_trips" | json_count)"

cat <<EOF
[opday] Fixture ready
base_url=${BASE_URL}
fixture_date=${TODAY}
company_id=${company_id}
admin_id=${admin_id}
company_name=$(shell_quote "$COMPANY_NAME")
company_slug=${COMPANY_SLUG}
admin_email=${ADMIN_EMAIL}
admin_password=${ADMIN_PASSWORD}
driver_password=${DRIVER_PASSWORD}
passenger_password=${PASSENGER_PASSWORD}
guardian_password=${GUARDIAN_PASSWORD}
driver_1_id=${driver_1_id}
driver_1_email=${DRIVER_1_EMAIL}
driver_1_name=$(shell_quote "$DRIVER_1_NAME")
driver_2_id=${driver_2_id}
driver_2_email=${DRIVER_2_EMAIL}
driver_2_name=$(shell_quote "$DRIVER_2_NAME")
passenger_main_id=${passenger_main_id}
passenger_main_name=$(shell_quote "$PASSENGER_MAIN_NAME")
passenger_main_email=${PASSENGER_MAIN_EMAIL}
passenger_support_id=${passenger_support_id}
passenger_support_name=$(shell_quote "$PASSENGER_SUPPORT_NAME")
passenger_support_email=${PASSENGER_SUPPORT_EMAIL}
guardian_id=${guardian_id}
guardian_email=${GUARDIAN_EMAIL}
guardian_passageiro_id=${passenger_main_id}
guardian_name=$(shell_quote "$GUARDIAN_NAME")
guardian_passageiro_name=$(shell_quote "$PASSENGER_MAIN_NAME")
service_morning_id=${service_morning_id}
service_morning_name=$(shell_quote "$SERVICE_MORNING_NAME")
service_afternoon_id=${service_afternoon_id}
service_afternoon_name=$(shell_quote "$SERVICE_AFTERNOON_NAME")
service_night_id=${service_night_id}
service_night_name=$(shell_quote "$SERVICE_NIGHT_NAME")
trip_morning_id=${trip_morning_id}
trip_afternoon_id=${trip_afternoon_id}
trip_night_id=${trip_night_id}
trip_morning_status=${trip_morning_status}
trip_morning_snapshot_operacional=${trip_morning_snapshot}
night_auto_ignored_motivo=${night_ignored_motivo}
passenger_services_count=${passenger_services_count}
passenger_day_trip_count=${passenger_day_trip_count}
support_day_trip_count=${support_day_trip_count}
guardian_day_trip_count=${guardian_day_trip_count}
driver_1_day_trip_count=${driver_1_day_trip_count}
driver_2_day_trip_count=${driver_2_day_trip_count}
concept=service-rights-presence-date-operational-trip-driver-per-trip

[opday] Expected checks
passenger_services_count=3
passenger_day_trip_count=3
support_day_trip_count=1
guardian_day_trip_count=3
driver_1_day_trip_count=1
driver_2_day_trip_count=2
night_auto_ignored_motivo=SEM_MOTORISTA
trip_morning_status=IN_PROGRESS
trip_morning_snapshot_operacional=true

[opday] Main passenger services:
${passenger_services}

[opday] Main passenger trips:
${passenger_day_trips}

[opday] Driver 1 trips:
${driver_1_day_trips}

[opday] Driver 2 trips:
${driver_2_day_trips}
EOF

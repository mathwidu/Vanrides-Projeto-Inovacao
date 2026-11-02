#!/bin/sh
# Disposable, LOCAL Docker only. Never starts Docker or builds/pulls images.
set -eu
if [ "$#" -ne 2 ]; then
  echo 'Usage: sh deploy/local_smoke.sh RECEIVER_LOCAL_IMAGE GATEWAY_LOCAL_IMAGE' >&2
  exit 2
fi
receiver_image=$1
gateway_image=$2
deploy_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${DOCKER_CONTEXT:-}" ]; then
  endpoint=$(docker context inspect "$DOCKER_CONTEXT" --format '{{.Endpoints.docker.Host}}')
elif [ -n "${DOCKER_HOST:-}" ]; then
  endpoint=$DOCKER_HOST
else
  endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
fi
case "$endpoint" in
  unix://*) ;;
  *) echo '{"smoke":"BLOCKED","reason":"LOCAL_DOCKER_REQUIRED"}' >&2; exit 2 ;;
esac
receiver_platform=$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$receiver_image")
gateway_platform=$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$gateway_image")
if [ "$receiver_platform" != "$gateway_platform" ]; then
  echo '{"smoke":"BLOCKED","reason":"IMAGE_PLATFORM_MISMATCH"}' >&2
  exit 2
fi
# Exercise startup under the actual read-only filesystem before the journey.
# An inert loopback alias satisfies nginx's static upstream DNS resolution.
docker run --rm --pull never --platform "$gateway_platform" --network none \
  --add-host receiver:127.0.0.1 --read-only --cap-drop ALL \
  --tmpfs /tmp:size=16777216,mode=1777 --entrypoint nginx "$gateway_image" -t -e stderr
stamp=$(date +%s)-$$
network=vr-gps-smoke-$stamp
state=vr-gps-smoke-state-$stamp
restored=vr-gps-smoke-restored-$stamp
receiver=vr-gps-smoke-receiver-$stamp
gateway=vr-gps-smoke-gateway-$stamp
cleanup() {
  docker rm -f "$gateway" "$receiver" >/dev/null 2>&1 || :
  docker network rm "$network" >/dev/null 2>&1 || :
  docker volume rm "$restored" "$state" >/dev/null 2>&1 || :
}
trap cleanup EXIT
trap 'exit 130' INT TERM
docker network create --internal --label com.vanrides.gps-spike.disposable=true "$network" >/dev/null
docker volume create --label com.vanrides.gps-spike.disposable=true "$state" >/dev/null
docker volume create --label com.vanrides.gps-spike.disposable=true "$restored" >/dev/null
docker run --rm --pull never --network none --read-only --cap-drop ALL \
  --tmpfs /tmp:size=16777216,mode=1777 --mount type=volume,src="$state",dst=/data \
  "$receiver_image" python -m receiver --db /data/receiver.sqlite \
  provision --alias synthetic_gateway_smoke --hours 1 --out /data/smoke-credential.json
start_gateway() {
  docker run -d --pull never --name "$gateway" --network "$network" --network-alias gateway \
    --read-only --cap-drop ALL --memory 64m --cpus 0.25 --tmpfs /tmp:size=16777216,mode=1777 \
    "$gateway_image" >/dev/null
}
docker run -d --pull never --name "$receiver" --network "$network" --network-alias receiver \
  --read-only --cap-drop ALL --memory 256m --cpus 0.50 --tmpfs /tmp:size=16777216,mode=1777 \
  --mount type=volume,src="$state",dst=/data "$receiver_image" >/dev/null
start_gateway
docker exec -i "$receiver" python - --credential-file /data/smoke-credential.json \
  < "$deploy_dir/../tests/gateway_smoke.py"
docker exec "$gateway" /usr/local/bin/gps-gateway-healthcheck
docker exec -i "$receiver" python - backup --source /data/receiver.sqlite --out /data/receiver-backup.sqlite \
  < "$deploy_dir/state_backup.py"
docker rm -f "$gateway" "$receiver" >/dev/null
# Mount the ORIGINAL state read-only; restore writes only into the fresh volume.
docker run --rm -i --pull never --network none --read-only --cap-drop ALL \
  --tmpfs /tmp:size=16777216,mode=1777 \
  --mount type=volume,src="$state",dst=/backup,readonly \
  --mount type=volume,src="$restored",dst=/data "$receiver_image" \
  python - restore --source /backup/receiver-backup.sqlite --out /data/receiver.sqlite \
  < "$deploy_dir/state_backup.py"
docker run -d --pull never --name "$receiver" --network "$network" --network-alias receiver \
  --read-only --cap-drop ALL --memory 256m --cpus 0.50 --tmpfs /tmp:size=16777216,mode=1777 \
  --mount type=volume,src="$restored",dst=/data \
  --mount type=volume,src="$state",dst=/smoke-input,readonly "$receiver_image" >/dev/null
start_gateway
docker exec -i "$receiver" python - --credential-file /smoke-input/smoke-credential.json --mode restored \
  < "$deploy_dir/../tests/gateway_smoke.py"
docker exec -i "$receiver" python - verify --source /data/receiver.sqlite \
  --expected-receipts 1 --expected-batches 1 --expect-revoked < "$deploy_dir/state_backup.py"
echo '{"smoke":"PASS","backupRestore":"PASS","scope":"DISPOSABLE_LOCAL_DOCKER"}'

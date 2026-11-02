#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$(id -u)" != "0" ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="/opt/vanrides/ops"
SUDOERS_FILE="/etc/sudoers.d/codexops-vanrides-dev-ops"

id codexops >/dev/null 2>&1 || {
  echo "User codexops does not exist" >&2
  exit 1
}

install -d -o root -g root -m 0755 "$TARGET_DIR"
install -d -o root -g root -m 0755 "$TARGET_DIR/lib"

install -o root -g root -m 0755 "$SCRIPT_DIR/vanrides-dev-status" "$TARGET_DIR/vanrides-dev-status"
install -o root -g root -m 0755 "$SCRIPT_DIR/vanrides-dev-backup-db" "$TARGET_DIR/vanrides-dev-backup-db"
install -o root -g root -m 0755 "$SCRIPT_DIR/vanrides-dev-reset-db" "$TARGET_DIR/vanrides-dev-reset-db"
install -o root -g root -m 0755 "$SCRIPT_DIR/vanrides-dev-deploy-image" "$TARGET_DIR/vanrides-dev-deploy-image"
install -o root -g root -m 0755 "$SCRIPT_DIR/vanrides-dev-smoke" "$TARGET_DIR/vanrides-dev-smoke"
install -o root -g root -m 0644 "$SCRIPT_DIR/lib/vanrides-dev-common.sh" "$TARGET_DIR/lib/vanrides-dev-common.sh"

cat > "$SUDOERS_FILE" <<EOF
# VanRides DEV/staging operations for Codex.
# Managed from vanrides-backend/deploy/ops.
codexops ALL=(root) NOPASSWD: $TARGET_DIR/vanrides-dev-status
codexops ALL=(root) NOPASSWD: $TARGET_DIR/vanrides-dev-backup-db
codexops ALL=(root) NOPASSWD: $TARGET_DIR/vanrides-dev-reset-db
codexops ALL=(root) NOPASSWD: $TARGET_DIR/vanrides-dev-deploy-image
codexops ALL=(root) NOPASSWD: $TARGET_DIR/vanrides-dev-smoke
EOF

chmod 0440 "$SUDOERS_FILE"
visudo -cf "$SUDOERS_FILE"

echo "Installed VanRides DEV ops scripts in $TARGET_DIR"
echo "Allowed codexops sudo commands:"
cat "$SUDOERS_FILE"

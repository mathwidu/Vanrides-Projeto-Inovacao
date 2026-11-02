#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OPS_DIR = ROOT / "deploy/ops"
INSTALLER = OPS_DIR / "install-codexops-vanrides-dev-ops.sh"
DEPLOY_SCRIPT = OPS_DIR / "vanrides-dev-deploy-image"


class VanRidesDevOpsScriptsTest(unittest.TestCase):
    def test_deploy_image_script_has_dev_only_guardrails(self) -> None:
        script = DEPLOY_SCRIPT.read_text(encoding="utf-8")

        self.assertIn('readonly EXPECTED_CONFIRMATION="DEPLOY-VANRIDES-DEV"', script)
        self.assertIn("ghcr.io/mathwidu/vanrides-backend:dev-", script)
        self.assertIn("assert_dev_guardrails", script)
        self.assertIn('"$VANRIDES_DEV_API_SERVICE"', script)
        self.assertIn("--with-registry-auth", script)
        self.assertIn("/opt/vanrides/ops/vanrides-dev-smoke", script)
        self.assertNotIn("vanrides-prod", script)

    def test_installer_allows_deploy_script_for_codexops(self) -> None:
        installer = INSTALLER.read_text(encoding="utf-8")

        self.assertIn('"$SCRIPT_DIR/vanrides-dev-deploy-image"', installer)
        self.assertIn("$TARGET_DIR/vanrides-dev-deploy-image", installer)
        self.assertIn("codexops ALL=(root) NOPASSWD: $TARGET_DIR/vanrides-dev-deploy-image", installer)


if __name__ == "__main__":
    unittest.main()

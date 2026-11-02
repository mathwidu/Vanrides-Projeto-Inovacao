#!/usr/bin/env python3
from __future__ import annotations

import stat
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/qa_maplibre_style_tile_smoke.sh"
QA_GATE = ROOT / "scripts/qa_gate_card.sh"
STYLE_FILE = (
    ROOT
    / "lib/features/tracking/presentation/widgets/vanrides_maplibre_proof_surface.dart"
)
DOC = ROOT / "backlog/QA-MapLibre-Device-Smoke.md"


class MapLibreStyleTileSmokeContractTest(unittest.TestCase):
    def test_script_exists_and_is_executable(self) -> None:
        self.assertTrue(
            SCRIPT.exists(),
            "scripts/qa_maplibre_style_tile_smoke.sh is missing",
        )
        self.assertTrue(
            SCRIPT.stat().st_mode & stat.S_IXUSR,
            "scripts/qa_maplibre_style_tile_smoke.sh is not executable",
        )

    def test_check_only_passes_without_device_or_tile_server(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--check-only"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        self.assertIn("MAPLIBRE_STYLE_TILE_SMOKE_OK", result.stdout)

    def test_gate_runs_style_tile_contract_only(self) -> None:
        gate = QA_GATE.read_text(encoding="utf-8")

        self.assertIn("qa_maplibre_style_tile_smoke_test.py", gate)
        self.assertIn("qa_maplibre_style_tile_smoke.sh", gate)
        self.assertIn("--check-only", gate)
        self.assertNotIn("qa_maplibre_style_tile_smoke.sh\" --run-device", gate)

    def test_style_declares_self_hosted_vector_tile_contract(self) -> None:
        style_source = STYLE_FILE.read_text(encoding="utf-8")

        self.assertIn("vanrides-vector-tiles", style_source)
        self.assertIn("vanrides:region", style_source)
        self.assertIn("BR-RS", style_source)
        self.assertIn("style-smoke-v1", style_source)
        self.assertIn("br-rs-smoke-v1", style_source)
        self.assertIn(
            "http://127.0.0.1:8099/vanrides/maps/BR-RS/vector/{z}/{x}/{y}.pbf",
            style_source,
        )
        self.assertNotIn("tile.openstreetmap.org", style_source)
        self.assertNotIn("demotiles.maplibre.org", style_source)

    def test_qa_doc_records_style_tile_limits(self) -> None:
        doc = DOC.read_text(encoding="utf-8")

        self.assertIn("MAPLIBRE_STYLE_TILE_SMOKE_OK", doc)
        self.assertIn("vanrides-vector-tiles", doc)
        self.assertIn("BR-RS", doc)
        self.assertIn("nao busca tiles reais", doc)


if __name__ == "__main__":
    unittest.main()

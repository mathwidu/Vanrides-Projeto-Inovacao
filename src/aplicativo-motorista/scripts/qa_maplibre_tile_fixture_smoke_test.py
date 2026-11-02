#!/usr/bin/env python3
from __future__ import annotations

import base64
import re
import stat
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/qa_maplibre_tile_fixture_smoke.sh"
QA_GATE = ROOT / "scripts/qa_gate_card.sh"
STYLE_FILE = (
    ROOT
    / "lib/features/tracking/presentation/widgets/vanrides_maplibre_proof_surface.dart"
)
FIXTURE = ROOT / "test/fixtures/maps/BR-RS/vector/0/0/0.pbf.base64"
DOC = ROOT / "backlog/QA-MapLibre-Device-Smoke.md"

EXPECTED_TEMPLATE = (
    "http://127.0.0.1:8099/vanrides/maps/BR-RS/vector/{z}/{x}/{y}.pbf"
)
EXPECTED_FIXTURE_URL = (
    "http://127.0.0.1:8099/vanrides/maps/BR-RS/vector/0/0/0.pbf"
)


class MapLibreTileFixtureSmokeContractTest(unittest.TestCase):
    def test_script_exists_and_is_executable(self) -> None:
        self.assertTrue(
            SCRIPT.exists(),
            "scripts/qa_maplibre_tile_fixture_smoke.sh is missing",
        )
        self.assertTrue(
            SCRIPT.stat().st_mode & stat.S_IXUSR,
            "scripts/qa_maplibre_tile_fixture_smoke.sh is not executable",
        )

    def test_fixture_decodes_to_non_empty_vector_tile(self) -> None:
        encoded = FIXTURE.read_text(encoding="utf-8").strip()
        payload = base64.b64decode(encoded, validate=True)

        self.assertGreater(len(payload), 0)
        self.assertLess(len(payload), 256)
        self.assertIn(b"vanrides_smoke", payload)
        self.assertNotIn(b"school", payload.lower())
        self.assertNotIn(b"passageiro", payload.lower())
        self.assertNotIn(b"motorista", payload.lower())

    def test_check_only_fetches_controlled_fixture(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--check-only"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        self.assertIn("MAPLIBRE_TILE_FIXTURE_SMOKE_OK", result.stdout)
        self.assertIn(EXPECTED_FIXTURE_URL, result.stdout)
        self.assertRegex(result.stdout, r"bytes=\d+")
        self.assertRegex(result.stdout, r"sha256=[0-9a-f]{64}")

    def test_gate_runs_tile_fixture_contract(self) -> None:
        gate = QA_GATE.read_text(encoding="utf-8")

        self.assertIn("qa_maplibre_tile_fixture_smoke_test.py", gate)
        self.assertIn("qa_maplibre_tile_fixture_smoke.sh", gate)
        self.assertIn("MapLibre tile fixture fetch smoke contract", gate)
        self.assertIn("--check-only", gate)

    def test_style_url_matches_fixture_path(self) -> None:
        style_source = STYLE_FILE.read_text(encoding="utf-8")

        self.assertIn(EXPECTED_TEMPLATE, style_source)
        self.assertIn("vanrides-vector-tiles", style_source)
        self.assertIn("BR-RS", style_source)
        self.assertNotIn("tile.openstreetmap.org", style_source)
        self.assertNotIn("demotiles.maplibre.org", style_source)

    def test_qa_doc_records_fixture_fetch_limits(self) -> None:
        doc = DOC.read_text(encoding="utf-8")

        self.assertIn("MAPLIBRE_TILE_FIXTURE_SMOKE_OK", doc)
        self.assertIn(EXPECTED_FIXTURE_URL, doc)
        self.assertIn("fixture local", doc)
        self.assertIn("nao prova geracao real de tiles", doc)
        self.assertIn("nao exige VPS", doc)


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "backlog/QA-Navigation-Audio-Operator-Handoff.md"
QA_GATE = ROOT / "scripts/qa_gate_card.sh"


class NavigationAudioOperatorHandoffTest(unittest.TestCase):
    def test_handoff_doc_points_operator_to_current_pending_evidence(self) -> None:
        doc = DOC.read_text(encoding="utf-8")

        self.assertIn("APP-NAV-AUDIO-HANDOFF-001", doc)
        self.assertIn("APP-NAV-AUDIO-CONFIRM-001", doc)
        self.assertIn(
            "output/navigation-tts-device-smoke/<timestamp>",
            doc,
        )
        self.assertIsNone(
            re.search(r"output/navigation-tts-device-smoke/20\d{6}[^\s`]*", doc),
            "o contrato de handoff nao pode fixar uma execucao historica",
        )
        self.assertIn("NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY", doc)
        self.assertIn("navigation-tts-device-smoke.provenance.json", doc)
        self.assertIn("Use o fallback para seguir com seguranca", doc)
        self.assertIn("PENDING_AUDIO_CONFIRMATION", doc)
        self.assertIn("operatorHeardPtBr=false", doc)
        self.assertIn("--package", doc)
        self.assertIn("--assert-confirmed", doc)
        self.assertIn("nao deve conter dados reais", doc)
        self.assertIn("nao libera parceiro", doc)
        self.assertIn("nao libera campo", doc)
        self.assertIn("valor exato `SIM`", doc)
        self.assertIn("NAO OUVIU", doc)

    def test_gate_keeps_operator_handoff_contract_checked(self) -> None:
        gate = QA_GATE.read_text(encoding="utf-8")

        self.assertIn("qa_navigation_audio_operator_handoff_test.py", gate)
        self.assertIn("Navigation audio operator handoff contract", gate)


if __name__ == "__main__":
    unittest.main()

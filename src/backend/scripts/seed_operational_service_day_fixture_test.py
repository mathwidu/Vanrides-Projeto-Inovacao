#!/usr/bin/env python3
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "seed_operational_service_day_fixture.sh"


def product_video_block() -> str:
    content = SCRIPT.read_text(encoding="utf-8")
    match = re.search(
        r'if \[\[ "\$VANRIDES_FIXTURE_STORY_MODE" == "product_video" \]\]; then\n(?P<body>.*?)\nelse\n',
        content,
        flags=re.S,
    )
    if not match:
        raise AssertionError("product_video block not found")
    return match.group("body")


def shell_assignment(block: str, name: str) -> str:
    match = re.search(rf'^\s*{re.escape(name)}="(?P<value>.*)"$', block, flags=re.M)
    if not match:
        raise AssertionError(f"{name} not found")
    return match.group("value")


class ProductVideoSeedFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.block = product_video_block()

    def test_uses_real_public_story_addresses(self) -> None:
        self.assertEqual(
            shell_assignment(self.block, "DEFAULT_PASSENGER_MAIN_ADDRESS"),
            "Prefeitura Municipal de Igrejinha, Av. Ildo Meneghetti, 757 - XV de Novembro, Igrejinha - RS",
        )
        self.assertEqual(
            shell_assignment(self.block, "DEFAULT_PASSENGER_SUPPORT_ADDRESS"),
            "Alles Blau Shopping, Av. Pres. Tancredo Neves, 5676 - Casa de Pedra, Igrejinha - RS",
        )
        self.assertEqual(
            shell_assignment(self.block, "DEFAULT_DESTINATION_ADDRESS"),
            "Universidade Feevale Campus II, ERS-239, 2755 - Vila Nova, Novo Hamburgo - RS",
        )

    def test_coordinates_match_igrejinha_to_feevale_demo_region(self) -> None:
        igrejinha_points = [
            ("DEFAULT_DRIVER_1_LAT", "DEFAULT_DRIVER_1_LNG"),
            ("DEFAULT_DRIVER_2_LAT", "DEFAULT_DRIVER_2_LNG"),
            ("DEFAULT_PASSENGER_MAIN_LAT", "DEFAULT_PASSENGER_MAIN_LNG"),
            ("DEFAULT_PASSENGER_SUPPORT_LAT", "DEFAULT_PASSENGER_SUPPORT_LNG"),
        ]
        for lat_name, lng_name in igrejinha_points:
            lat = float(shell_assignment(self.block, lat_name))
            lng = float(shell_assignment(self.block, lng_name))
            self.assertGreater(lat, -29.59, lat_name)
            self.assertLess(lat, -29.55, lat_name)
            self.assertGreater(lng, -50.82, lng_name)
            self.assertLess(lng, -50.77, lng_name)

        destination_lat = float(shell_assignment(self.block, "DEFAULT_DESTINATION_LAT"))
        destination_lng = float(shell_assignment(self.block, "DEFAULT_DESTINATION_LNG"))
        self.assertAlmostEqual(destination_lat, -29.665083, places=5)
        self.assertAlmostEqual(destination_lng, -51.119017, places=5)

    def test_product_video_copy_is_not_generic_qa_copy(self) -> None:
        self.assertNotIn("Centro, Igrejinha - RS", self.block)
        self.assertNotIn("Casa de Luiza, Igrejinha - RS", self.block)
        self.assertNotIn("Destino operacional QA", self.block)


if __name__ == "__main__":
    unittest.main()

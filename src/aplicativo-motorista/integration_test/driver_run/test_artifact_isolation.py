"""Binary framing fixtures only: these tests never build, install or execute an app."""
import importlib.util
from pathlib import Path
import plistlib
import struct
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("artifact_isolation", Path(__file__).with_name("artifact_isolation.py"))
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


def dex_fixture(strings):
    data = bytearray(112 + len(strings) * 4)
    data[:8] = b"dex\n035\0"
    struct.pack_into("<I", data, 36, 112)
    struct.pack_into("<I", data, 40, 0x12345678)
    struct.pack_into("<II", data, 56, len(strings), 112)
    for index, value in enumerate(strings):
        encoded = value.encode()
        if len(encoded) >= 128:
            raise ValueError("Keep the synthetic DEX string fixture bounded")
        struct.pack_into("<I", data, 112 + index * 4, len(data))
        data.extend(bytes([len(encoded)]) + encoded + b"\0")
    struct.pack_into("<I", data, 32, len(data))
    return bytes(data)


class ArtifactPolicyTests(unittest.TestCase):
    def apk(self, root, strings):
        path = Path(root) / "fixture.apk"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"binary manifest fixture")
            archive.writestr("classes.dex", dex_fixture(strings))
        return path

    def test_normal_authentication_labels_are_not_confused_with030(self):
        with tempfile.TemporaryDirectory() as root:
            result = policy.inspect_apk(self.apk(root, ["DPoP", "Authorization", "Bearer normal-scheme"]))
            self.assertEqual(result["status"], "PASS")
            self.assertFalse(result["executedApp"])
            self.assertEqual(result["dexCount"], 1)

    def test_mvp_rejects_origin_or_transport_even_when_only_one_survives(self):
        with tempfile.TemporaryDirectory() as root:
            for marker in ["https://" + policy.SPIKE_HOST, *policy.SPIKE_CLASSES]:
                with self.assertRaisesRegex(policy.IsolationFailure, "MVP_CONTAINS_SPIKE030"):
                    policy.inspect_apk(self.apk(root, [marker]))

    def test_spike_requires_both_implementation_and_closed_origin(self):
        with tempfile.TemporaryDirectory() as root:
            path = self.apk(root, [policy.SPIKE_HOST, *policy.SPIKE_CLASSES])
            self.assertEqual(policy.inspect_apk(path, "spike")["status"], "PASS")
            with self.assertRaisesRegex(policy.IsolationFailure, "INCOMPLETE"):
                policy.inspect_apk(self.apk(root, [policy.SPIKE_HOST]), "spike")

    def test_malformed_dex_and_missing_dex_never_pass(self):
        with self.assertRaises(policy.IsolationFailure):
            list(policy.dex_strings(b"not a dex"))
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "fixture.apk"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"fixture")
            with self.assertRaisesRegex(policy.IsolationFailure, "APK_DEX_MISSING"):
                policy.inspect_apk(path)

    def test_embedded_ios_framework_is_scanned_without_running_the_host(self):
        with tempfile.TemporaryDirectory() as root:
            app = Path(root) / "Runner.app"
            app.mkdir()
            (app / "Info.plist").write_bytes(plistlib.dumps({"CFBundleExecutable": "Runner"}))
            (app / "Runner").write_bytes(b"\xcf\xfa\xed\xfeDPoP")
            framework = app / "Frameworks/App.framework"
            framework.mkdir(parents=True)
            (framework / "App").write_bytes(b"\xcf\xfa\xed\xfe" + policy.SPIKE_HOST.encode())
            with self.assertRaisesRegex(policy.IsolationFailure, "MVP_IOS_CONTAINS_SPIKE030"):
                policy.inspect_ios_app(app)
            (framework / "App").write_bytes(b"\xcf\xfa\xed\xfeunrelated data")
            self.assertEqual(policy.inspect_ios_app(app)["machOCount"], 2)


if __name__ == "__main__":
    unittest.main()

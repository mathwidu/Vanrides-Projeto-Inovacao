"""Pure checks: no adb, device, socket connection or process startup."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("driver_run_lab", Path(__file__).with_name("android_lab.py"))
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)


class PermissionPolicyTests(unittest.TestCase):
    def test_only_the_app_location_dialog_can_choose_while_using(self):
        rows = [(["Permitir que VanRides Motorista acesse a localização?"], ""),
                (["Durante o uso do app"], "[0,0][100,100]"), (["O tempo todo"], "[0,100][100,200]")]
        self.assertEqual(lab.permission_choice(rows), "[0,0][100,100]")

    def test_other_apps_and_unrestricted_grants_are_blocked(self):
        for rows in [
            [(["Outro aplicativo quer localização", "Permitir"], "[0,0][100,100]")],
            [(["VanRides Motorista quer localização o tempo todo", "Permitir"], "[0,0][100,100]")],
            [(["VanRides Motorista quer câmera", "Permitir"], "[0,0][100,100]")],
        ]:
            with self.assertRaises(lab.pairing.LabFailure):
                lab.permission_choice(rows)

    def test_notifications_dialog_is_bounded_to_the_same_app(self):
        self.assertEqual(lab.permission_choice([(["VanRides Motorista pode enviar notificações?"], ""),
                                               (["Permitir"], "[0,0][100,100]")]), "[0,0][100,100]")

    def test_locked_or_unknown_screen_never_triggers_unlock(self):
        class Device:
            def __init__(self, policy):
                self.policy = policy
                self.calls = []
            def adb(self, *args, **kwargs):
                self.calls.append(args)
                return self.policy
        for policy in ["showing=true", "unknown platform output"]:
            device = Device(policy)
            with self.assertRaises(lab.pairing.LabFailure):
                lab.unlocked(device)
            self.assertEqual(device.calls, [("shell", "dumpsys", "window", "policy")])
        lab.unlocked(Device("showing=false"))


if __name__ == "__main__":
    unittest.main()

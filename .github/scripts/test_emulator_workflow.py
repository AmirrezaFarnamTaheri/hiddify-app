"""The Android emulator job requires working KVM, not silent software fallback."""
from pathlib import Path
import unittest

WORKFLOW = (Path(__file__).resolve().parents[1] / "workflows/lifecycle-regressions.yml")


class EmulatorInfrastructureTests(unittest.TestCase):
    def test_preflight_checks_runner_has_usable_kvm(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("sudo chmod 666 /dev/kvm", text)
        self.assertIn("test -r /dev/kvm && test -w /dev/kvm", text)
        self.assertIn("Emulator cannot run: /dev/kvm unavailable", text)

    def test_real_emulator_smoke_is_still_required(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("reactivecircus/android-emulator-runner@v2", text)
        self.assertIn("script: bash .github/scripts/android-emulator-smoke.sh", text)
        self.assertIn("needs: android-app-smoke", text)


if __name__ == "__main__":
    unittest.main()

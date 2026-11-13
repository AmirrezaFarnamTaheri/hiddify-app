"""Cross-PR source guards for the unified upstream and VPN lifecycle integration."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class UnifiedIntegrationSourceTest(unittest.TestCase):
    def test_foreground_start_acknowledged_and_partial_native_setup_unwound(self):
        source = (ROOT / "android/app/src/main/kotlin/com/hiddify/hiddify/bg/BoxService.kt").read_text(encoding="utf-8")
        self.assertIn('notification.show(activeProfileName, R.string.status_starting)', source)
        self.assertIn('Log.e(TAG, "cannot start the service", e)', source)
        self.assertIn('withContext(NonCancellable + Dispatchers.IO)', source)
        self.assertIn('resources.rollback(', source)
        self.assertIn('closeCore = { Mobile.close(grpcSetupMode) }', source)
        self.assertIn('if (status.value == Status.Stopped) start()', source)

    def test_upstream_crash_reports_and_local_desktop_generation_guards_coexist(self):
        source = (ROOT / "lib/hiddifycore/core_interface/core_interface_desktop.dart").read_text(encoding="utf-8")
        self.assertIn('await process.crashReason()', source)
        self.assertIn('_restartGuard.isCurrent(crashedGeneration)', source)
        self.assertIn('_restartGuard.finishSetup(', source)
        self.assertIn('await runningProcess.stop()', source)

    def test_callback_epoch_and_partial_resource_cleanup_have_real_jvm_tests(self):
        build = (ROOT / "tools/network-jvm-tests/build.gradle.kts").read_text(encoding="utf-8")
        for name in (
            "NetworkCallbackEpoch.kt", "NetworkCallbackEpochTest.kt",
            "StartupResourceRollback.kt", "StartupResourceRollbackTest.kt",
        ):
            self.assertIn(name, build)
        listener = (ROOT / "android/app/src/main/kotlin/com/hiddify/hiddify/bg/DefaultNetworkListener.kt").read_text(encoding="utf-8")
        self.assertIn("callbackEpoch.stop()", listener)
        self.assertIn("callbackEpoch.accepts(message.generation)", listener)

    def test_upstream_and_doctor_translations_both_survive(self):
        for locale in ("en", "fa"):
            text = (ROOT / f"assets/translations/{locale}.i18n.json").read_text(encoding="utf-8")
            obj = json.loads(text)
            self.assertTrue(obj["pages"]["proxies"]["keepTop"]["restingShow"])
            health = obj["connection"]["health"]
            for key in ("doctorTitle", "doctorRouteUnverified", "probeUnknown", "probeLimit"):
                self.assertTrue(health[key])


if __name__ == "__main__":
    unittest.main()

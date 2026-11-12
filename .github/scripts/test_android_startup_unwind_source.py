"""Integration contract for Hiddify Android failed-startup resource ownership."""
from pathlib import Path
import unittest

SOURCE = (
    Path(__file__).resolve().parents[2]
    / "android/app/src/main/kotlin/com/hiddify/hiddify/bg/BoxService.kt"
)


class BoxServiceStartupUnwindContract(unittest.TestCase):
    def setUp(self):
        text = SOURCE.read_text(encoding="utf-8")
        self.start = text.split("private suspend fun startService() {", 1)[1].split(
            "fun serviceReload()", 1
        )[0]
        self.fail = text.split("private suspend fun stopAndAlert(", 1)[1].split(
            "internal fun onStartCommand()", 1
        )[0]

    def test_track_operations_before_native_or_network_allocation(self):
        self.assertRegex(
            self.start,
            r"resources\.beforeNetworkStart\(\)\s*DefaultNetworkMonitor\.start\(\)",
        )
        self.assertRegex(
            self.start,
            r"resources\.beforeCoreSetup\(\)\s*Mobile\.setup\(",
        )

    def test_every_failure_dispatches_cleanup_of_this_attempt(self):
        self.assertIn(
            "stopAndAlert(Alert.EmptyConfiguration, resources = resources)", self.start
        )
        self.assertIn(
            "stopAndAlert(Alert.CreateService, e.message ?: e.toString(), resources)",
            self.start,
        )
        self.assertIn("stopAndAlert(Alert.StartService, e.message, resources)", self.start)
        self.assertLess(self.start.index("notification.start()"), self.start.index("status.postValue(Status.Started)"))

    def test_early_initialize_error_uses_the_same_non_native_cleanup_path(self):
        code = SOURCE.read_text(encoding="utf-8")
        entry = code.split("internal fun onStartCommand()", 1)[1].split(
            "fun onBind(", 1
        )[0]
        self.assertRegex(entry, r"try\s*\{\s*initialize\(\)")
        self.assertIn("stopAndAlert(Alert.StartService", entry)
        self.assertIn("StartupResourceRollback()", entry)

    def test_rollback_releases_tun_network_and_native_before_reporting_stopped(self):
        self.assertIn("withContext(NonCancellable + Dispatchers.IO)", self.fail)
        for element in (
            "resources.rollback(",
            "fileDescriptor = null",
            "fd?.close()",
            "stopNetwork = { DefaultNetworkMonitor.stop() }",
            "closeCore = { Mobile.close(grpcSetupMode) }",
            "cleanupErrors.forEach",
            "service.stopSelf()",
        ):
            self.assertIn(element, self.fail)
        self.assertLess(self.fail.index("resources.rollback("), self.fail.index("status.value = Status.Stopped"))
        self.assertLess(self.fail.index("status.value = Status.Stopped"), self.fail.index("service.stopSelf()"))


if __name__ == "__main__":
    unittest.main()

"""Static integration checks for desktop startup ownership and cleanup."""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[2]
          / "lib/hiddifycore/core_interface/core_interface_desktop.dart")
GUARD = (Path(__file__).resolve().parents[2]
         / "lib/hiddifycore/core_interface/core_process_restart_guard.dart")


class DesktopSetupRollbackSourceTest(unittest.TestCase):
    def test_new_process_is_stopped_if_startup_fails(self):
        text = SOURCE.read_text(encoding="utf-8")
        setup = text.split("Future<String> _setupProcess(", 1)[1].split(
            "static Future<CoreProcess> _startProcessRetrying(", 1
        )[0]
        self.assertIn("_restartGuard.finishSetup(", setup)
        self.assertIn("rollback: () async {", setup)
        self.assertIn("!startedHere || !identical(_process, runningProcess)", setup)
        self.assertIn("await runningProcess.stop();", setup)

    def test_rpc_result_cannot_re_adopt_core_after_shutdown(self):
        text = SOURCE.read_text(encoding="utf-8")
        connect = text.split("Future<String> _connect({int? generation})", 1)[1].split(
            "ChannelCredentials _buildMtlsCredentials()", 1
        )[0]
        self.assertIn("!_restartGuard.isCurrent(generation)", connect)
        self.assertLess(
            connect.index("final previousChannel = _channel;"),
            connect.index("fgClient = CoreClient(_channel!);"),
        )
        self.assertIn("_connect(generation: generation)", text)

    def test_guard_cleans_up_failed_and_stale_setup(self):
        guard = GUARD.read_text(encoding="utf-8")
        self.assertIn("Future<String> finishSetup(", guard)
        self.assertIn("await rollback();", guard)
        self.assertIn("return 'Core setup cancelled';", guard)


if __name__ == "__main__":
    unittest.main()

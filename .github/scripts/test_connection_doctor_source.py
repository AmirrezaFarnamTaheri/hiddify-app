"""Source and localization checks for read-only route diagnostics.

The doctor must never derive VPN routing/DNS privacy from a core URL test.
"""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCTOR = ROOT / "lib/features/connection/model/connection_doctor_evidence.dart"
VIEW = ROOT / "lib/features/connection/widget/connection_health_sheet.dart"
KEYS = (
    "doctorTitle", "doctorCoreLabel", "doctorCoreRunning",
    "doctorCoreStopped", "doctorCoreTransitioning", "doctorCoreUnknown",
    "doctorRequestLabel", "doctorRequestResponded", "doctorRequestNoReply",
    "doctorRequestUnknown", "doctorRouteLabel", "doctorRouteUnverified",
    "doctorLimit",
)


class ConnectionDoctorSourceContract(unittest.TestCase):
    def test_no_invented_tunnel_or_leak_evidence(self):
        source = DOCTOR.read_text(encoding="utf-8")
        self.assertIn("status.isRunning && health.server != null", source)
        self.assertIn("DoctorRouteEvidence.unverified", source)
        self.assertIn("bool get provesTunnelReady => false", source)
        self.assertIn("bool get provesNoDnsLeak => false", source)
        self.assertIn("DoctorRequestEvidence.unknown", source)

    def test_existing_health_sheet_shows_separate_core_request_route_states(self):
        view = VIEW.read_text(encoding="utf-8")
        self.assertIn("ConnectionDoctorEvidence.from(health)", view)
        self.assertIn("DoctorRouteEvidence.unverified => h.doctorRouteUnverified", view)
        for key in ("doctorCoreLabel", "doctorRequestLabel", "doctorRouteLabel", "doctorLimit"):
            self.assertIn("h." + key, view)
        self.assertNotIn("ipinfo.ip", view)

    def test_primary_language_coverage(self):
        for locale in ("en", "fa"):
            data = json.loads((ROOT / f"assets/translations/{locale}.i18n.json").read_text("utf-8"))
            health = data["connection"]["health"]
            for key in KEYS:
                self.assertTrue(health[key].strip(), (locale, key))


if __name__ == "__main__":
    unittest.main()

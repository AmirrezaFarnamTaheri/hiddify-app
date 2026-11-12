"""Guard the source-to-UI provenance contract without a Flutter runtime."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "lib/features/proxy/model/probe_provenance.dart"
SHEET = ROOT / "lib/features/connection/widget/connection_health_sheet.dart"
KEYS = (
    "probeEvidenceTitle", "probeEvidenceSource", "probeResponded",
    "probeNoReply", "probeNotTested", "probeTimeUnknown", "probeIpNonCached",
    "probeIpCached", "probeIpUnavailable", "probeFamilyV4",
    "probeFamilyV6", "probeFamilyUnknown", "probeLimit",
)


class ProbeProvenanceIntegrationContract(unittest.TestCase):
    def test_real_url_test_and_cache_flags_drive_result_interpretation(self):
        source = MODEL.read_text(encoding="utf-8")
        for phrase in (
            "ConnectionConst.isValidDelay(delay)",
            "outbound.urlTestDelay",
            "outbound.ipinfo.fromCache",
            "InternetAddress.tryParse(outbound.ipinfo.ip)",
            "DateTime? get observedAt => null",
            "bool get provesDeviceWideRouting => false",
        ):
            self.assertIn(phrase, source)

    def test_diagnostics_panel_only_shows_derived_metadata(self):
        source = SHEET.read_text(encoding="utf-8")
        self.assertIn("ProbeProvenance.fromOutbound(health.server!)", source)
        # A disconnected service cannot present an old URL observation as active.
        self.assertIn("health.status.isRunning && health.server != null", source)
        self.assertIn("h.probeTimeUnknown", source)
        self.assertIn("h.probeLimit", source)
        for code in ("UrlProbeOutcome.responded", "IpProbeSource.cached",
                     "ObservedIpFamily.ipv4", "ObservedIpFamily.ipv6"):
            self.assertIn(code, source)
        self.assertNotIn("ipinfo.ip", source)
        self.assertNotIn("subscriptionUrl", source)

    def test_primary_languages_have_identical_diagnostics_keys(self):
        for lang in ("en", "fa"):
            data = json.loads((ROOT / f"assets/translations/{lang}.i18n.json").read_text("utf-8"))
            health = data["connection"]["health"]
            for key in KEYS:
                self.assertIn(key, health)
                self.assertTrue(health[key].strip())


if __name__ == "__main__":
    unittest.main()

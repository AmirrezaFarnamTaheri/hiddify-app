"""Flutter runtime JARs already use AndroidX and must not exhaust Gradle Jetifier."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
PROPERTIES = ROOT / "android/gradle.properties"


class GradleJetifierContract(unittest.TestCase):
    def test_only_flutter_abi_artifacts_bypass_legacy_jetifier(self):
        gradle = PROPERTIES.read_text(encoding="utf-8")
        self.assertIn("android.useAndroidX=true", gradle)
        self.assertIn("android.enableJetifier=true", gradle)
        self.assertIn(
            "android.jetifier.ignorelist=.*(arm64_v8a|armeabi_v7a|x86_64)_(debug|profile|release).*",
            gradle,
        )

    def test_full_android_build_and_emulator_are_still_required(self):
        workflow = (ROOT / ".github/workflows/lifecycle-regressions.yml").read_text(encoding="utf-8")
        self.assertIn("flutter build apk --debug", workflow)
        self.assertIn("reactivecircus/android-emulator-runner@v2", workflow)


if __name__ == "__main__":
    unittest.main()

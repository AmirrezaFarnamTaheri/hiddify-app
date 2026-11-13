"""Lifecycle regression: Android connectivity callbacks never wait for the actor."""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[2]
          / "android/app/src/main/kotlin/com/hiddify/hiddify/bg/DefaultNetworkListener.kt")

class DefaultNetworkCallbackSourceTests(unittest.TestCase):
    def test_os_callbacks_do_not_block_the_connectivity_thread(self):
        text = SOURCE.read_text(encoding="utf-8")
        callbacks = text.split("private class Callback(", 1)[1].split("private var fallback", 1)[0]
        self.assertNotIn("runBlocking", callbacks)
        self.assertNotIn("GlobalScope", text)
        self.assertIn("networkActor.trySend(", text)
        self.assertIn("Channel.UNLIMITED", text)
        self.assertIn("SupervisorJob() + Dispatchers.Default", text)

    def test_stale_callbacks_cannot_modify_new_network_actor_generation(self):
        text = SOURCE.read_text(encoding="utf-8")
        self.assertIn("val callbackEpoch = NetworkCallbackEpoch()", text)
        self.assertIn("callbackEpoch.newRegistration()", text)
        self.assertIn("callbackEpoch.stop()", text)
        self.assertIn("private class Callback(private val generation: Long)", text)
        for signal in ("Put", "Update", "Lost"):
            self.assertIn(f"NetworkMessage.{signal}(network, generation)", text)
        self.assertGreaterEqual(text.count("callbackEpoch.accepts(message.generation)"), 3)
        self.assertIn("registeredCallback?.let { unregister(it) }", text)

    def test_pending_get_timeout_and_stop_guards_remain(self):
        text = SOURCE.read_text(encoding="utf-8")
        self.assertIn("withTimeout(15_000L)", text)
        self.assertIn("pendingRequests.reject(CancellationException", text)
        self.assertIn("response.cancel()", text)
        self.assertIn("NetworkMessage.Forget(response)", text)

if __name__ == "__main__":
    unittest.main()

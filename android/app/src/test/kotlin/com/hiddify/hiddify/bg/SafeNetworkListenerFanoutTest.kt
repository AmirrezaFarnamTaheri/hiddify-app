package com.hiddify.hiddify.bg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeNetworkListenerFanoutTest {
    @Test fun throwingSubscriberCannotPreventHandoverDelivery() {
        val observed = mutableListOf<String?>()
        var failures = 0
        val listeners: List<(String?) -> Unit> = listOf(
            { throw IllegalStateException("private endpoint in exception") },
            { observed.add(it) },
        )
        SafeNetworkListenerFanout.dispatch(listeners, "wifi") { failures++ }
        SafeNetworkListenerFanout.dispatch(listeners, "cellular") { failures++ }
        SafeNetworkListenerFanout.dispatch(listeners, null) { failures++ }
        assertEquals(listOf("wifi", "cellular", null), observed)
        assertEquals(3, failures)
    }

    @Test fun throwingFailureReporterCannotKillFollowingSubscribers() {
        val delivered = mutableListOf<String>()
        val callbacks: List<(String?) -> Unit> = listOf(
            { error("first subscriber failed") },
            { delivered.add(it ?: "offline") },
        )
        SafeNetworkListenerFanout.dispatch(callbacks, "wifi") { error("reporter failed") }
        assertEquals(listOf("wifi"), delivered)
    }

    @Test fun emptySubscribersAndHealthyCallbacksWorkWithoutSideEffects() {
        val received = mutableListOf<Int?>()
        SafeNetworkListenerFanout.dispatch(emptyList<(Int?) -> Unit>(), 1)
        SafeNetworkListenerFanout.dispatch(listOf({ n: Int? -> received.add(n) }), 42)
        assertEquals(listOf(42), received)
        assertTrue(received.none { it == null })
    }
}

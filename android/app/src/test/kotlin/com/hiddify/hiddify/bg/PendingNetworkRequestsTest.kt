package com.hiddify.hiddify.bg

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingNetworkRequestsTest {
    @Test fun unregisterRejectsEveryPendingWaiter() = runBlocking {
        val pending = PendingNetworkRequests<String>()
        val first = CompletableDeferred<String>()
        val second = CompletableDeferred<String>()
        pending.add(first)
        pending.add(second)
        pending.reject(IllegalStateException("listener stopped"))
        assertTrue(first.isCompleted)
        assertTrue(second.isCompleted)
        assertTrue(runCatching { first.await() }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { second.await() }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun networkArrivalResolvesPendingWaiters() = runBlocking {
        val pending = PendingNetworkRequests<String>()
        val first = CompletableDeferred<String>()
        val second = CompletableDeferred<String>()
        pending.add(first)
        pending.add(second)
        pending.resolve("wifi")
        assertEquals("wifi", first.await())
        assertEquals("wifi", second.await())
    }

    @Test fun timedOutWaiterDoesNotAccumulate() {
        val pending = PendingNetworkRequests<String>()
        val abandoned = CompletableDeferred<String>()
        val active = CompletableDeferred<String>()
        pending.add(abandoned)
        pending.remove(abandoned)
        pending.add(active)
        pending.resolve("cell")
        assertFalse(abandoned.isCompleted)
        assertTrue(active.isCompleted)
    }
}

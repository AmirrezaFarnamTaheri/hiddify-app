package com.hiddify.hiddify.bg

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Test

class InterfaceUpdateRetryTest {
    @Test
    fun successfulLookupStopsAtFirstResult() = runBlocking {
        var lookups = 0
        var waits = 0
        val index = findInterfaceIndexWithRetry(
            attempts = 10,
            lookup = { lookups++; 23 },
            pauseBeforeRetry = { waits++ },
        )
        assertEquals(23, index)
        assertEquals(1, lookups)
        assertEquals(0, waits)
    }

    @Test
    fun failuresRetryOnlyUntilSuccessful() = runBlocking {
        var lookups = 0
        var waits = 0
        val index = findInterfaceIndexWithRetry(
            attempts = 5,
            lookup = {
                lookups++
                if (lookups < 3) throw IllegalStateException("not ready")
                42
            },
            pauseBeforeRetry = { waits++ },
        )
        assertEquals(42, index)
        assertEquals(3, lookups)
        assertEquals(2, waits)
    }

    @Test
    fun absentInterfaceHasBoundedRetryCountAndNoFinalWait() = runBlocking {
        var lookups = 0
        var waits = 0
        val index = findInterfaceIndexWithRetry(
            attempts = 3,
            lookup = { lookups++; null },
            pauseBeforeRetry = { waits++ },
        )
        assertEquals(null, index)
        assertEquals(3, lookups)
        assertEquals(2, waits)
    }
    @Test
    fun negativeAndZeroInterfaceIndicesAreNotPublished() = runBlocking {
        var lookups = 0
        var waits = 0
        val values = listOf(-1, 0, 42)
        val index = findInterfaceIndexWithRetry(
            attempts = 3,
            lookup = { values[lookups++] },
            pauseBeforeRetry = { waits++ },
        )
        assertEquals(42, index)
        assertEquals(3, lookups)
        assertEquals(2, waits)
    }

    @Test
    fun onlyInvalidIndicesYieldNoInterface() = runBlocking {
        var waits = 0
        val index = findInterfaceIndexWithRetry(
            attempts = 3,
            lookup = { 0 },
            pauseBeforeRetry = { waits++ },
        )
        assertEquals(null, index)
        assertEquals(2, waits)
    }

    @Test
    fun cancelledInterfaceLookupPropagatesImmediately() = runBlocking {
        var calls = 0
        var waits = 0
        var cancelled = false
        try {
            findInterfaceIndexWithRetry(
                attempts = 3,
                lookup = { calls++; throw CancellationException("cancelled") },
                pauseBeforeRetry = { waits++ },
            )
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(1, calls)
        assertEquals(0, waits)
    }
}

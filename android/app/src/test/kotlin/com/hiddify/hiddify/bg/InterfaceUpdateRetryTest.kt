package com.hiddify.hiddify.bg

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}

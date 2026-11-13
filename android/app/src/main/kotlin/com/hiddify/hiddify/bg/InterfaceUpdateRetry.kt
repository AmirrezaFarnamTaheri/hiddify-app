package com.hiddify.hiddify.bg

import kotlin.coroutines.cancellation.CancellationException

/** A successful interface lookup ends retries immediately. Delays are cancellable. */
internal suspend fun findInterfaceIndexWithRetry(
    attempts: Int,
    lookup: () -> Int?,
    pauseBeforeRetry: suspend () -> Unit,
): Int? {
    for (attempt in 0 until attempts) {
        val index = try {
            lookup()
        } catch (cancelled: CancellationException) {
            // Cancellation is terminal, not a missing interface to retry.
            throw cancelled
        } catch (_: Exception) {
            null
        }
        // NetworkInterface.index may be -1 when unavailable; zero is also unusable.
        if (index != null && index > 0) return index
        if (attempt + 1 < attempts) pauseBeforeRetry()
    }
    return null
}

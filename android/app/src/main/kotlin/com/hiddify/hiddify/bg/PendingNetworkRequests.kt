package com.hiddify.hiddify.bg

import kotlinx.coroutines.CompletableDeferred

/** Owns unresolved network Get responses in the single-threaded network actor. */
internal class PendingNetworkRequests<T> {
    private val requests = mutableListOf<CompletableDeferred<T>>()

    fun add(request: CompletableDeferred<T>) { requests.add(request) }

    fun remove(request: CompletableDeferred<T>) { requests.remove(request) }

    fun resolve(value: T) {
        val outstanding = requests.toList()
        requests.clear()
        outstanding.forEach { it.complete(value) }
    }

    fun reject(reason: Throwable) {
        val outstanding = requests.toList()
        requests.clear()
        outstanding.forEach { it.completeExceptionally(reason) }
    }
}

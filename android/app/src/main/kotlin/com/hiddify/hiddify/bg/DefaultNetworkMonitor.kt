package com.hiddify.hiddify.bg

import android.net.Network
import android.os.Build
import com.hiddify.hiddify.Application
import com.hiddify.core.libbox.InterfaceUpdateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.NetworkInterface

object DefaultNetworkMonitor {

    @Volatile
    var defaultNetwork: Network? = null
        private set

    @Volatile
    private var listener: InterfaceUpdateListener? = null

    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val updateLock = Any()
    private var updateJob: Job? = null
    private var updateGeneration = 0L

    suspend fun start() {
        DefaultNetworkListener.start(this) {
            defaultNetwork = it
            checkDefaultInterfaceUpdate(it)
        }
        defaultNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Application.connectivity.activeNetwork
        } else {
            DefaultNetworkListener.get()
        }
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    suspend fun stop() {
        DefaultNetworkListener.stop(this)
        val pending = synchronized(updateLock) {
            updateGeneration++
            updateJob.also { updateJob = null }
        }
        pending?.cancelAndJoin()
        defaultNetwork = null
    }

    suspend fun require(): Network {
        val network = defaultNetwork
        if (network != null) return network
        return DefaultNetworkListener.get()
    }

    fun setListener(listener: InterfaceUpdateListener?) {
        this.listener = listener
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    private fun checkDefaultInterfaceUpdate(newNetwork: Network?) {
        synchronized(updateLock) {
            val generation = ++updateGeneration
            updateJob?.cancel()
            updateJob = updateScope.launch {
                val currentListener = listener ?: return@launch
                if (newNetwork == null) {
                    if (isCurrent(generation, currentListener)) {
                        currentListener.updateDefaultInterface("", -1, false, false)
                    }
                    return@launch
                }
                val name = Application.connectivity.getLinkProperties(newNetwork)?.interfaceName
                    ?: return@launch
                val index = findInterfaceIndexWithRetry(
                    attempts = 10,
                    lookup = { NetworkInterface.getByName(name)?.index },
                    pauseBeforeRetry = { delay(100) },
                ) ?: return@launch

                if (isCurrent(generation, currentListener)) {
                    currentListener.updateDefaultInterface(name, index, false, false)
                }
            }
        }
    }

    private fun isCurrent(generation: Long, currentListener: InterfaceUpdateListener): Boolean =
        synchronized(updateLock) {
            generation == updateGeneration && listener === currentListener
        }
}

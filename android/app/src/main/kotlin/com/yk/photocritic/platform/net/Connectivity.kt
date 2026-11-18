package com.yk.photocritic.platform.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the default network has internet (file 34). Drives the offline
 * banner, Send and Send report. Registered once for the app's lifetime.
 */
class Connectivity(
    context: Context,
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(hasInternet(manager.getNetworkCapabilities(manager.activeNetwork)))

    val online: StateFlow<Boolean> = state.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    state.value = hasInternet(capabilities)
                }

                override fun onLost(network: Network) {
                    state.value = false
                }
            },
        )
    }

    private fun hasInternet(capabilities: NetworkCapabilities?) =
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

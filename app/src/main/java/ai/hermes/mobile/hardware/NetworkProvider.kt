package ai.hermes.mobile.hardware

import ai.hermes.mobile.hardware.model.NetworkStatus
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Abstraction for network telemetry queries per HARDWARE-BRIDGE-CONTRACT.md.
 */
interface NetworkProvider {
    fun getNetworkStatus(): NetworkStatus
}

/**
 * Android platform implementation backed by ConnectivityManager and NetworkCapabilities.
 */
class AndroidNetworkProvider(
    private val context: Context,
) : NetworkProvider {
    private val connectivityManager: ConnectivityManager? by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    override fun getNetworkStatus(): NetworkStatus {
        val manager = connectivityManager ?: return NetworkStatus.offline()
        val activeNetwork = manager.activeNetwork ?: return NetworkStatus.offline()
        val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus.offline()

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isConnected = hasInternet && isValidated

        val type =
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.TYPE_WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.TYPE_CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkStatus.TYPE_ETHERNET
                else -> NetworkStatus.TYPE_OTHER
            }

        val isMetered =
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                manager.isActiveNetworkMetered

        return NetworkStatus(
            isConnected = isConnected,
            type = if (isConnected) type else NetworkStatus.TYPE_NONE,
            isMetered = isMetered,
            isOffline = !isConnected,
        )
    }
}

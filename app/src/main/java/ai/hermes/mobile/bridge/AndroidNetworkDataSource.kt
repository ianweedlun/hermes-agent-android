package ai.hermes.mobile.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

internal class AndroidNetworkDataSource(
    context: Context,
) : NetworkDataSource {
    private val connectivityManager: ConnectivityManager? by lazy {
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    }
    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    override fun currentSnapshot(): NetworkSnapshot {
        val manager = connectivityManager ?: return NetworkSnapshot.OFFLINE
        return try {
            val active = manager.activeNetwork ?: return NetworkSnapshot.OFFLINE
            val capabilities = manager.getNetworkCapabilities(active)
            snapshotFromCapabilities(capabilities)
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing ACCESS_NETWORK_STATE permission when reading network status", e)
            NetworkSnapshot.OFFLINE
        }
    }

    override fun registerCallback(onChanged: (NetworkSnapshot) -> Unit) {
        if (activeCallback != null) {
            return
        }
        val manager = connectivityManager ?: return
        val created =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onChanged(snapshotForNetwork(manager, network))
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    onChanged(snapshotFromCapabilities(networkCapabilities))
                }

                override fun onLost(network: Network) {
                    onChanged(currentSnapshot())
                }

                override fun onUnavailable() {
                    onChanged(NetworkSnapshot.OFFLINE)
                }
            }
        try {
            manager.registerDefaultNetworkCallback(created)
            activeCallback = created
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to register ConnectivityManager callback", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "ConnectivityManager rejected network callback registration", e)
        }
    }

    override fun unregisterCallback() {
        val callback = activeCallback ?: return
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Network callback already unregistered", e)
        } finally {
            activeCallback = null
        }
    }

    private fun snapshotForNetwork(
        manager: ConnectivityManager,
        network: Network,
    ): NetworkSnapshot =
        try {
            snapshotFromCapabilities(manager.getNetworkCapabilities(network))
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to inspect network capabilities", e)
            NetworkSnapshot.OFFLINE
        }

    private fun snapshotFromCapabilities(capabilities: NetworkCapabilities?): NetworkSnapshot {
        if (capabilities == null) {
            return NetworkSnapshot.OFFLINE
        }
        return NetworkSnapshot(
            hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            isEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }

    companion object {
        private const val TAG = "AndroidNetworkDataSource"
    }
}

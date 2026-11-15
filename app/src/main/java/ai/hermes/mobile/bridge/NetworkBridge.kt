package ai.hermes.mobile.bridge

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class NetworkSnapshot(
    val hasInternet: Boolean,
    val isValidated: Boolean,
    val isWifi: Boolean,
    val isCellular: Boolean,
    val isEthernet: Boolean,
    val isMetered: Boolean,
) {
    companion object {
        val OFFLINE: NetworkSnapshot =
            NetworkSnapshot(
                hasInternet = false,
                isValidated = false,
                isWifi = false,
                isCellular = false,
                isEthernet = false,
                isMetered = false,
            )
    }
}

internal interface NetworkDataSource {
    fun currentSnapshot(): NetworkSnapshot

    fun registerCallback(onChanged: (NetworkSnapshot) -> Unit)

    fun unregisterCallback()
}

/**
 * Provides live and snapshot connectivity telemetry backed by `ConnectivityManager.NetworkCallback`.
 */
class NetworkBridge internal constructor(
    private val dataSource: NetworkDataSource,
) {
    constructor(context: Context) : this(AndroidNetworkDataSource(context))

    private val lock = Any()
    private val _networkStatus: MutableStateFlow<NetworkStatus> =
        MutableStateFlow(mapSnapshot(dataSource.currentSnapshot()))
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    fun observeNetworkStatus(): Flow<NetworkStatus> = networkStatus

    fun getNetworkStatus(): NetworkStatus {
        val snapshot = mapSnapshot(dataSource.currentSnapshot())
        _networkStatus.value = snapshot
        return snapshot
    }

    fun start() {
        synchronized(lock) {
            dataSource.registerCallback { snapshot ->
                _networkStatus.value = mapSnapshot(snapshot)
            }
            _networkStatus.value = mapSnapshot(dataSource.currentSnapshot())
        }
    }

    fun stop() {
        synchronized(lock) {
            dataSource.unregisterCallback()
        }
    }

    companion object {
        internal fun mapSnapshot(snapshot: NetworkSnapshot): NetworkStatus {
            val connected = snapshot.hasInternet && snapshot.isValidated
            if (!connected) {
                return NetworkStatus.OFFLINE
            }
            val type =
                when {
                    snapshot.isEthernet -> NetworkType.ETHERNET
                    snapshot.isWifi -> NetworkType.WIFI
                    snapshot.isCellular -> NetworkType.CELLULAR
                    else -> NetworkType.NONE
                }
            return NetworkStatus(
                isConnected = true,
                type = type,
                isMetered = snapshot.isMetered,
                isOffline = false,
            )
        }
    }
}

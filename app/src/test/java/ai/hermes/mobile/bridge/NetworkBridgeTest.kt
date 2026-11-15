package ai.hermes.mobile.bridge

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkBridgeTest {
    @Test
    fun networkBridgeProducesSnapshotsAndLiveCallbackUpdates() =
        runBlocking {
            val dataSource =
                FakeNetworkDataSource(
                    NetworkSnapshot(
                        hasInternet = true,
                        isValidated = true,
                        isWifi = true,
                        isCellular = false,
                        isEthernet = false,
                        isMetered = false,
                    ),
                )
            val bridge = NetworkBridge(dataSource)
            val initial = bridge.getNetworkStatus()
            assertTrue(initial.isConnected)
            assertFalse(initial.isOffline)
            assertFalse(initial.isMetered)
            assertEquals(NetworkType.WIFI, initial.type)

            bridge.start()
            assertTrue(dataSource.isRegistered)

            dataSource.emit(
                NetworkSnapshot(
                    hasInternet = true,
                    isValidated = true,
                    isWifi = false,
                    isCellular = true,
                    isEthernet = false,
                    isMetered = true,
                ),
            )
            val cellular = bridge.observeNetworkStatus().first()
            assertTrue(cellular.isConnected)
            assertTrue(cellular.isMetered)
            assertEquals(NetworkType.CELLULAR, cellular.type)

            dataSource.emit(NetworkSnapshot.OFFLINE)
            val offline = bridge.networkStatus.value
            assertFalse(offline.isConnected)
            assertTrue(offline.isOffline)
            assertEquals(NetworkType.NONE, offline.type)

            bridge.stop()
            assertFalse(dataSource.isRegistered)
        }

    @Test
    fun networkBridgeTreatsUnvalidatedNetworkAsOfflineAndPrioritizesEthernet() {
        val unvalidated =
            NetworkBridge.mapSnapshot(
                NetworkSnapshot(
                    hasInternet = true,
                    isValidated = false,
                    isWifi = true,
                    isCellular = false,
                    isEthernet = false,
                    isMetered = false,
                ),
            )
        assertEquals(NetworkStatus.OFFLINE, unvalidated)

        val ethernet =
            NetworkBridge.mapSnapshot(
                NetworkSnapshot(
                    hasInternet = true,
                    isValidated = true,
                    isWifi = true,
                    isCellular = false,
                    isEthernet = true,
                    isMetered = false,
                ),
            )
        assertEquals(NetworkType.ETHERNET, ethernet.type)
        assertTrue(ethernet.isConnected)
    }
}

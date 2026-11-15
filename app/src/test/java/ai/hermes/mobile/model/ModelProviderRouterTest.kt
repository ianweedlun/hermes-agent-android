package ai.hermes.mobile.model

import ai.hermes.mobile.hardware.model.NetworkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProviderRouterTest {
    @Test
    fun defaultStateIsTier1AndAllowsHeavyTools() {
        val router = ModelProviderRouter()

        assertEquals(ModelTier.TIER_1_PRIMARY, router.activeTier)
        assertEquals("gemini-3.8-flash", router.activeModel)
        assertFalse(router.isOfflineMode)
        assertTrue(router.canExecuteHeavyTools())
    }

    @Test
    fun offlineNetworkSwitchesToTier3AndBlocksHeavyTools() {
        var observedTier: ModelTier? = null
        val router = ModelProviderRouter { tier -> observedTier = tier }

        val offlineStatus = NetworkStatus.offline()
        router.onNetworkStatusChanged(offlineStatus)

        assertEquals(ModelTier.TIER_3_LOCAL_FALLBACK, router.activeTier)
        assertEquals("gemma-2-2b-it", router.activeModel)
        assertTrue(router.isOfflineMode)
        assertFalse(router.canExecuteHeavyTools())
        assertEquals(ModelTier.TIER_3_LOCAL_FALLBACK, observedTier)
    }

    @Test
    fun reconnectRestoresPrimaryOrHighReasoningTier() {
        val router = ModelProviderRouter()
        router.setHighReasoningPreferred(true)
        assertEquals(ModelTier.TIER_2_HIGH_REASONING, router.activeTier)

        // Go offline
        router.onNetworkStatusChanged(NetworkStatus.offline())
        assertEquals(ModelTier.TIER_3_LOCAL_FALLBACK, router.activeTier)

        // Reconnect online
        val onlineStatus =
            NetworkStatus(
                isConnected = true,
                type = NetworkStatus.TYPE_WIFI,
                isMetered = false,
                isOffline = false,
            )
        router.onNetworkStatusChanged(onlineStatus)
        assertEquals(ModelTier.TIER_2_HIGH_REASONING, router.activeTier)
    }

    @Test
    fun cloudTimeoutTriggersLocalFallback() {
        val router = ModelProviderRouter()
        assertEquals(ModelTier.TIER_1_PRIMARY, router.activeTier)

        router.onCloudTimeout()
        assertEquals(ModelTier.TIER_3_LOCAL_FALLBACK, router.activeTier)
        assertTrue(router.isOfflineMode)
    }
}

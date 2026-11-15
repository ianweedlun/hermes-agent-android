package ai.hermes.mobile.model

import ai.hermes.mobile.hardware.model.NetworkStatus
import java.util.concurrent.atomic.AtomicReference

enum class ModelTier(
    val targetModel: String,
    val endpoint: String,
    val isLocal: Boolean,
) {
    TIER_1_PRIMARY("gemini-3.8-flash", "https://generativelanguage.googleapis.com", false),
    TIER_2_HIGH_REASONING("gemini-4-hydrogen-high", "https://generativelanguage.googleapis.com", false),
    TIER_3_LOCAL_FALLBACK("gemma-2-2b-it", "http://127.0.0.1:8080/v1", true),
}

/**
 * Manages model provider cascade and offline resiliency per MODEL-FALLBACK-CONTRACT.md.
 */
class ModelProviderRouter(
    initialTier: ModelTier = ModelTier.TIER_1_PRIMARY,
    private val onTierChanged: ((ModelTier) -> Unit)? = null,
) {
    private val currentTier = AtomicReference(initialTier)
    private var highReasoningPreferred = false

    val activeTier: ModelTier
        get() = currentTier.get()

    val activeModel: String
        get() = currentTier.get().targetModel

    val isOfflineMode: Boolean
        get() = currentTier.get().isLocal

    /**
     * Determines whether heavy tool calls (web search, subagent delegations) can execute
     * under the active tier per MODEL-FALLBACK-CONTRACT.md.
     */
    fun canExecuteHeavyTools(): Boolean = !currentTier.get().isLocal

    /**
     * Updates active tier based on network connectivity transitions.
     */
    fun onNetworkStatusChanged(status: NetworkStatus) {
        if (status.isOffline || !status.isConnected) {
            transitionTo(ModelTier.TIER_3_LOCAL_FALLBACK)
        } else {
            val target =
                if (highReasoningPreferred) {
                    ModelTier.TIER_2_HIGH_REASONING
                } else {
                    ModelTier.TIER_1_PRIMARY
                }
            transitionTo(target)
        }
    }

    /**
     * Signals network timeout (> 15s) or cloud failure triggering local fallback.
     */
    fun onCloudTimeout() {
        transitionTo(ModelTier.TIER_3_LOCAL_FALLBACK)
    }

    /**
     * Sets preference for Tier 2 high reasoning (delegated coding / architecture).
     */
    fun setHighReasoningPreferred(preferred: Boolean) {
        highReasoningPreferred = preferred
        if (!isOfflineMode) {
            val target =
                if (preferred) {
                    ModelTier.TIER_2_HIGH_REASONING
                } else {
                    ModelTier.TIER_1_PRIMARY
                }
            transitionTo(target)
        }
    }

    private fun transitionTo(newTier: ModelTier) {
        val previous = currentTier.getAndSet(newTier)
        if (previous != newTier) {
            onTierChanged?.invoke(newTier)
        }
    }
}

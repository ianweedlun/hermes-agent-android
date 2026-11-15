package ai.hermes.mobile.bridge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardBridgeTest {
    @Test
    fun sensitiveClipboardAutoClearsAfterSixtySecondDelay() =
        runBlocking {
            val gateway = FakeClipboardGateway()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val gate = CompletableDeferred<Unit>()
            var requestedDelayMs = -1L
            val bridge =
                ClipboardBridge(
                    gateway = gateway,
                    scope = scope,
                    clock = { 10_000L },
                    delayFn = { delayMs ->
                        requestedDelayMs = delayMs
                        gate.await()
                    },
                )

            bridge.setClipboardText(text = "sensitive-clip-value", isSensitive = true)
            assertEquals(ClipboardBridge.DEFAULT_SENSITIVE_CLEAR_DELAY_MS, requestedDelayMs)
            assertEquals(60_000L, requestedDelayMs)
            assertEquals("sensitive-clip-value", bridge.getClipboardText().text)
            assertTrue(bridge.getClipboardText().isSensitive)

            gate.complete(Unit)
            assertNull(gateway.currentClip)
            assertEquals("", bridge.getClipboardText().text)
            scope.cancel()
        }

    @Test
    fun overwritingSensitiveClipWithNonSensitiveClipCancelsPendingAutoClear() =
        runBlocking {
            val gateway = FakeClipboardGateway()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val gate = CompletableDeferred<Unit>()
            val bridge =
                ClipboardBridge(
                    gateway = gateway,
                    scope = scope,
                    clock = { 20_000L },
                    delayFn = { gate.await() },
                )

            bridge.setClipboardText(text = "temp-secret", isSensitive = true)
            bridge.setClipboardText(text = "public-note", isSensitive = false)
            gate.complete(Unit)

            val current = bridge.getClipboardText()
            assertEquals("public-note", current.text)
            assertFalse(current.isSensitive)
            scope.cancel()
        }

    @Test
    fun externalClipboardReplacementPreventsClearingUserClip() =
        runBlocking {
            val gateway = FakeClipboardGateway()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val gate = CompletableDeferred<Unit>()
            val bridge =
                ClipboardBridge(
                    gateway = gateway,
                    scope = scope,
                    clock = { 30_000L },
                    delayFn = { gate.await() },
                )

            bridge.setClipboardText(text = "hermes-secret", isSensitive = true)
            gateway.writeClip(text = "user-copied-elsewhere", isSensitive = false, timestamp = 35_000L)
            gate.complete(Unit)

            assertEquals("user-copied-elsewhere", bridge.getClipboardText().text)
            scope.cancel()
        }
}

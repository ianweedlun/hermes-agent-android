package ai.hermes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaemonPowerControllerTest {
    @Test
    fun wakeLockAcquiresOnActiveStatesAndReleasesOnIdleErrorAndDestroy() {
        val wakeLock = FakeWakeLockHandle()
        val thermalMonitor = FakeThermalMonitor()
        val controller = DaemonPowerController(wakeLock, thermalMonitor) {}

        controller.syncWakeLock(DaemonStatus(state = AgentState.IDLE))
        assertFalse(wakeLock.isHeld)
        assertEquals(0, wakeLock.acquireCount)

        controller.syncWakeLock(DaemonStatus(state = AgentState.THINKING))
        assertTrue(wakeLock.isHeld)
        assertEquals(1, wakeLock.acquireCount)
        assertEquals(DaemonPowerController.DEFAULT_WAKELOCK_TIMEOUT_MS, wakeLock.lastTimeoutMs)

        controller.syncWakeLock(DaemonStatus(state = AgentState.IDLE))
        assertFalse(wakeLock.isHeld)
        assertEquals(1, wakeLock.releaseCount)

        controller.syncWakeLock(DaemonStatus(state = AgentState.EXECUTING_TOOL, activeTool = "terminal"))
        assertTrue(wakeLock.isHeld)
        assertEquals(2, wakeLock.acquireCount)

        controller.syncWakeLock(DaemonStatus(state = AgentState.ERROR))
        assertFalse(wakeLock.isHeld)
        assertEquals(2, wakeLock.releaseCount)

        controller.syncWakeLock(DaemonStatus(state = AgentState.THINKING))
        assertTrue(wakeLock.isHeld)
        controller.releaseWakeLock()
        assertFalse(wakeLock.isHeld)
    }

    @Test
    fun thermalThrottlingReleasesWakeLockAndDeliversUpdates() {
        val wakeLock = FakeWakeLockHandle()
        val thermalMonitor = FakeThermalMonitor()
        val observed = mutableListOf<ThermalLevel>()
        val controller = DaemonPowerController(wakeLock, thermalMonitor) { observed.add(it) }

        controller.registerThermalListener()
        assertEquals(listOf(ThermalLevel.NONE), observed)

        controller.syncWakeLock(DaemonStatus(state = AgentState.EXECUTING_TOOL))
        assertTrue(wakeLock.isHeld)

        thermalMonitor.emit(ThermalLevel.SEVERE)
        assertEquals(listOf(ThermalLevel.NONE, ThermalLevel.SEVERE), observed)
        controller.syncWakeLock(
            DaemonStatus(state = AgentState.EXECUTING_TOOL, thermalLevel = ThermalLevel.SEVERE),
        )
        assertFalse(wakeLock.isHeld)

        controller.unregisterThermalListener()
        assertFalse(thermalMonitor.isRegistered)
    }

    @Test
    fun wakeLockTimeoutIsBoundedToMaximumDuration() {
        val wakeLock = FakeWakeLockHandle()
        val thermalMonitor = FakeThermalMonitor()
        val controller = DaemonPowerController(wakeLock, thermalMonitor) {}

        controller.syncWakeLock(
            status = DaemonStatus(state = AgentState.THINKING),
            timeoutMs = DaemonPowerController.MAX_WAKELOCK_TIMEOUT_MS * 3,
        )

        assertEquals(DaemonPowerController.MAX_WAKELOCK_TIMEOUT_MS, wakeLock.lastTimeoutMs)
    }

    private class FakeWakeLockHandle : WakeLockHandle {
        override var isHeld: Boolean = false
        var acquireCount: Int = 0
        var releaseCount: Int = 0
        var lastTimeoutMs: Long = 0L

        override fun acquire(timeoutMs: Long) {
            isHeld = true
            acquireCount += 1
            lastTimeoutMs = timeoutMs
        }

        override fun release() {
            isHeld = false
            releaseCount += 1
        }
    }

    private class FakeThermalMonitor : ThermalMonitor {
        private var callback: ((ThermalLevel) -> Unit)? = null
        var isRegistered: Boolean = false
        private var level: ThermalLevel = ThermalLevel.NONE

        override fun currentThermalLevel(): ThermalLevel = level

        override fun register(onThermalChanged: (ThermalLevel) -> Unit) {
            callback = onThermalChanged
            isRegistered = true
            onThermalChanged(level)
        }

        override fun unregister() {
            callback = null
            isRegistered = false
        }

        fun emit(newLevel: ThermalLevel) {
            level = newLevel
            callback?.invoke(newLevel)
        }
    }
}

package ai.hermes.mobile.bridge

internal class FakeBatteryDataSource(
    private var current: BatteryRawReading,
) : BatteryDataSource {
    var isRegistered: Boolean = false
    private var listener: ((BatteryRawReading) -> Unit)? = null

    override fun readCurrent(): BatteryRawReading = current

    override fun registerUpdates(onReading: (BatteryRawReading) -> Unit) {
        listener = onReading
        isRegistered = true
    }

    override fun unregisterUpdates() {
        listener = null
        isRegistered = false
    }

    fun emit(reading: BatteryRawReading) {
        current = reading
        listener?.invoke(reading)
    }
}

internal class FakeNetworkDataSource(
    private var current: NetworkSnapshot,
) : NetworkDataSource {
    var isRegistered: Boolean = false
    private var listener: ((NetworkSnapshot) -> Unit)? = null

    override fun currentSnapshot(): NetworkSnapshot = current

    override fun registerCallback(onChanged: (NetworkSnapshot) -> Unit) {
        listener = onChanged
        isRegistered = true
    }

    override fun unregisterCallback() {
        listener = null
        isRegistered = false
    }

    fun emit(snapshot: NetworkSnapshot) {
        current = snapshot
        listener?.invoke(snapshot)
    }
}

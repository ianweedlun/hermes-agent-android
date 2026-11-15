package ai.hermes.mobile.bridge

internal class FakeClipboardGateway : ClipboardGateway {
    var currentClip: ClipboardPayload? = null

    override fun readClip(): ClipboardPayload? = currentClip

    override fun writeClip(
        text: String,
        isSensitive: Boolean,
        timestamp: Long,
    ) {
        currentClip = ClipboardPayload(text = text, isSensitive = isSensitive, timestamp = timestamp)
    }

    override fun clearClip(expectedText: String?) {
        if (expectedText == null || currentClip?.text == expectedText) {
            currentClip = null
        }
    }
}

internal class FakeHapticPlayer(
    private val supported: Boolean,
) : HapticPlayer {
    val played = mutableListOf<HapticPattern>()

    override fun hasVibrator(): Boolean = supported

    override fun play(pattern: HapticPattern): Boolean {
        played.add(pattern)
        return true
    }
}

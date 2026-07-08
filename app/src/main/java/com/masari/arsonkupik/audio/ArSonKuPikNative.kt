package com.masari.arsonkupik.audio

data class ArSonKuPikMeters(
    val inputPeakDb: Float,
    val outputPeakDb: Float,
    val gainReductionDb: Float,
    val correlation: Float,
    val clipping: Boolean,
)

class ArSonKuPikNative(
    sampleRate: Int = 48_000,
    channels: Int = 2,
) : AutoCloseable {

    private var handle: Long = nativeCreate(sampleRate, channels)

    init {
        require(handle != 0L) { "Failed to create ArSonKuPik native engine" }
    }

    fun prepare(sampleRate: Int, channels: Int) {
        nativePrepare(handle, sampleRate, channels)
    }

    fun setPreset(id: String) {
        nativeSetPreset(handle, id)
    }

    fun setMacros(
        bass: Float,
        vocal: Float,
        width: Float,
        air: Float,
        loud: Float,
    ) {
        nativeSetMacros(handle, bass, vocal, width, air, loud)
    }

    fun setBypass(bypass: Boolean) {
        nativeSetBypass(handle, bypass)
    }

    fun setSmartProtect(enabled: Boolean) {
        nativeSetSmartProtect(handle, enabled)
    }

    fun setOutputTrimDb(trimDb: Float) {
        nativeSetOutputTrimDb(handle, trimDb)
    }

    /**
     * Interleaved float PCM: L R L R ... in range roughly -1.0..1.0.
     * Keep this buffer reused by the audio processor to avoid GC pressure.
     */
    fun processFloatInterleaved(buffer: FloatArray, frames: Int, channels: Int) {
        nativeProcessFloatInterleaved(handle, buffer, frames, channels)
    }

    fun meters(): ArSonKuPikMeters {
        val m = nativeGetMeters(handle)
        return ArSonKuPikMeters(
            inputPeakDb = m[0],
            outputPeakDb = m[1],
            gainReductionDb = m[2],
            correlation = m[3],
            clipping = m[4] > 0.5f,
        )
    }

    override fun close() {
        val h = handle
        if (h != 0L) {
            nativeDestroy(h)
            handle = 0L
        }
    }

    private external fun nativeCreate(sampleRate: Int, channels: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativePrepare(handle: Long, sampleRate: Int, channels: Int)
    private external fun nativeSetPreset(handle: Long, presetId: String)
    private external fun nativeSetMacros(handle: Long, bass: Float, vocal: Float, width: Float, air: Float, loud: Float)
    private external fun nativeSetBypass(handle: Long, bypass: Boolean)
    private external fun nativeSetSmartProtect(handle: Long, enabled: Boolean)
    private external fun nativeSetOutputTrimDb(handle: Long, trimDb: Float)
    private external fun nativeProcessFloatInterleaved(handle: Long, buffer: FloatArray, frames: Int, channels: Int)
    private external fun nativeGetMeters(handle: Long): FloatArray

    companion object {
        init {
            System.loadLibrary("arsonkupik_android_dsp")
        }
    }
}

package com.masari.arsonkupik.audio

/**
 * Integration sketch for Media3 AudioProcessor.
 *
 * Keep this as the design reference, because Media3 AudioProcessor method
 * signatures can shift slightly between versions. The important production rule:
 * decode to PCM -> convert to float interleaved -> native DSP -> convert back.
 *
 * Recommended playback path:
 * ExoPlayer + DefaultRenderersFactory override buildAudioSink()
 * -> DefaultAudioSink.Builder().setAudioProcessors(arrayOf(thisProcessor))
 */
class ArSonKuPikAudioProcessorSketch {
    private var engine: ArSonKuPikNative? = null
    private var sampleRate = 48_000
    private var channels = 2

    private var floatBuffer = FloatArray(0)

    fun onConfigure(inputSampleRate: Int, inputChannelCount: Int) {
        sampleRate = inputSampleRate
        channels = inputChannelCount.coerceIn(1, 8)
        if (engine == null) engine = ArSonKuPikNative(sampleRate, channels)
        engine?.prepare(sampleRate, channels)
    }

    fun setPreset(id: String) = engine?.setPreset(id)
    fun setBypass(bypass: Boolean) = engine?.setBypass(bypass)
    fun setMacros(bass: Float, vocal: Float, width: Float, air: Float, loud: Float) =
        engine?.setMacros(bass, vocal, width, air, loud)

    /**
     * PCM16 interleaved example. Production should also handle PCM float and
     * 24/32-bit depending on the exact Media3 output format configuration.
     */
    fun processPcm16Interleaved(input: ShortArray): ShortArray {
        val frameCount = input.size / channels
        if (floatBuffer.size < input.size) floatBuffer = FloatArray(input.size)

        for (i in input.indices) {
            floatBuffer[i] = input[i] / 32768.0f
        }
        engine?.processFloatInterleaved(floatBuffer, frameCount, channels)
        val out = ShortArray(input.size)
        for (i in input.indices) {
            val s = (floatBuffer[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt()
            out[i] = s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    fun meters(): ArSonKuPikMeters? = engine?.meters()

    fun release() {
        engine?.close()
        engine = null
    }
}

package com.masari.arsonkupik.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Process
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.min
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real-time playback engine for SonKuPik.
 *
 * The engine has two source paths:
 * - built-in synth beds, used only as a fallback/demo source;
 * - Android audio URIs decoded with MediaExtractor/MediaCodec.
 *
 * Both paths produce interleaved stereo float PCM, run through the native
 * ArSonKuPik DSP engine, then stream to AudioTrack.
 */
class ArSonKuPikSmartMusicEngine(
    context: Context,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val defaultSampleRate = 48_000
    private val channels = 2
    private val framesPerBlock = 768
    private val engine = ArSonKuPikNative(defaultSampleRate, channels)
    private val lock = Any()

    @Volatile private var closed = false
    @Volatile private var playing = false
    @Volatile private var lastError: String? = null
    @Volatile private var metersSnapshot = ArSonKuPikMeters(
        inputPeakDb = -120f,
        outputPeakDb = -120f,
        gainReductionDb = 0f,
        correlation = 1f,
        clipping = false,
    )

    private var sourceKind = SourceKind.Synth
    private var sourceUri: Uri? = null
    private var sourceVersion = 0
    private var pendingSeekProgress: Float? = null
    private var toneHz = 440.0
    private var durationSec = 222
    private var playheadSec = 0.0

    private val renderThread = thread(
        start = true,
        isDaemon = true,
        name = "ArSonKuPikSmartMusicEngine"
    ) {
        renderLoop()
    }

    fun loadTrack(toneHz: Double, durationSec: Int, progress: Float) {
        synchronized(lock) {
            sourceKind = SourceKind.Synth
            sourceUri = null
            sourceVersion += 1
            this.toneHz = toneHz.coerceIn(40.0, 16_000.0)
            this.durationSec = durationSec.coerceAtLeast(1)
            playheadSec = this.durationSec * progress.coerceIn(0f, 1f).toDouble()
            pendingSeekProgress = progress.coerceIn(0f, 1f)
            lastError = null
        }
    }

    fun loadAudioUri(uri: Uri, durationSec: Int, progress: Float) {
        synchronized(lock) {
            sourceKind = SourceKind.Uri
            sourceUri = uri
            sourceVersion += 1
            this.durationSec = durationSec.coerceAtLeast(1)
            playheadSec = this.durationSec * progress.coerceIn(0f, 1f).toDouble()
            pendingSeekProgress = progress.coerceIn(0f, 1f)
            lastError = null
        }
    }

    fun setPlaying(enabled: Boolean) {
        playing = enabled
    }

    fun seekToProgress(progress: Float) {
        synchronized(lock) {
            val safeProgress = progress.coerceIn(0f, 1f)
            playheadSec = durationSec * safeProgress.toDouble()
            pendingSeekProgress = safeProgress
        }
    }

    fun configureDsp(
        presetId: String,
        bass: Float,
        vocal: Float,
        width: Float,
        air: Float,
        loud: Float,
        trimDb: Float,
        bypass: Boolean,
        protect: Boolean,
    ) {
        synchronized(lock) {
            runCatching {
                engine.setPreset(presetId)
                engine.setMacros(bass, vocal, width, air, loud)
                engine.setOutputTrimDb(trimDb)
                engine.setBypass(bypass)
                engine.setSmartProtect(protect)
            }.onFailure {
                lastError = it.message ?: "Failed to configure DSP"
            }.onSuccess {
                lastError = null
            }
        }
    }

    fun progress(): Float = synchronized(lock) {
        (playheadSec / durationSec.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun meters(): ArSonKuPikMeters = metersSnapshot

    fun error(): String? = lastError

    override fun close() {
        closed = true
        playing = false
        runCatching { renderThread.join(700) }
        synchronized(lock) {
            engine.close()
        }
    }

    private fun renderLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        var audioTrack: AudioTrack? = null
        var activeSampleRate = defaultSampleRate
        var activeVersion = -1
        var reader: CodecPcmReader? = null

        val floatBuffer = FloatArray(framesPerBlock * channels)
        val shortBuffer = ShortArray(framesPerBlock * channels)

        fun releaseTrack() {
            audioTrack?.let {
                runCatching {
                    it.pause()
                    it.flush()
                    it.release()
                }
            }
            audioTrack = null
        }

        fun ensureTrack(sampleRate: Int): AudioTrack? {
            if (audioTrack != null && activeSampleRate == sampleRate) return audioTrack
            releaseTrack()
            activeSampleRate = sampleRate.coerceAtLeast(8_000)
            synchronized(lock) {
                engine.prepare(activeSampleRate, channels)
            }
            return runCatching {
                createAudioTrack(activeSampleRate)
            }.onFailure {
                lastError = it.message ?: "Failed to create AudioTrack"
            }.getOrNull().also {
                audioTrack = it
            }
        }

        try {
            while (!closed) {
                if (!playing) {
                    if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack?.pause()
                        audioTrack?.flush()
                    }
                    Thread.sleep(18)
                    continue
                }

                val snapshot = synchronized(lock) {
                    SourceSnapshot(
                        kind = sourceKind,
                        version = sourceVersion,
                        uri = sourceUri,
                        toneHz = toneHz,
                        durationSec = durationSec,
                        seekProgress = pendingSeekProgress,
                    ).also {
                        pendingSeekProgress = null
                    }
                }

                if (snapshot.version != activeVersion) {
                    reader?.close()
                    reader = null
                    activeVersion = snapshot.version
                }

                val framesRead = when (snapshot.kind) {
                    SourceKind.Synth -> {
                        ensureTrack(defaultSampleRate) ?: continue
                        synchronized(lock) {
                            renderMusicBlock(floatBuffer, defaultSampleRate, snapshot.toneHz, snapshot.durationSec)
                        }
                        framesPerBlock
                    }

                    SourceKind.Uri -> {
                        val uri = snapshot.uri
                        if (uri == null) {
                            lastError = "Audio source is missing"
                            playing = false
                            continue
                        }

                        val currentReader = reader ?: runCatching {
                            CodecPcmReader(appContext, uri)
                        }.onFailure {
                            lastError = it.message ?: "Failed to decode audio"
                            playing = false
                        }.getOrNull()?.also {
                            reader = it
                            ensureTrack(it.sampleRate) ?: return@also
                        }

                        if (currentReader == null) {
                            Thread.sleep(20)
                            continue
                        }

                        ensureTrack(currentReader.sampleRate) ?: continue
                        snapshot.seekProgress?.let { currentReader.seekToProgress(it) }
                        val read = currentReader.read(floatBuffer, framesPerBlock)
                        if (read <= 0) {
                            if (currentReader.isEnded) {
                                synchronized(lock) {
                                    playheadSec = durationSec.toDouble()
                                }
                                playing = false
                            } else {
                                Thread.sleep(6)
                            }
                            continue
                        }
                        synchronized(lock) {
                            playheadSec = (playheadSec + read / activeSampleRate.toDouble())
                                .coerceAtMost(durationSec.toDouble())
                        }
                        read
                    }
                }

                val track = ensureTrack(activeSampleRate) ?: continue
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }

                synchronized(lock) {
                    engine.processFloatInterleaved(floatBuffer, framesRead, channels)
                    metersSnapshot = engine.meters()
                }

                val sampleCount = framesRead * channels
                for (i in 0 until sampleCount) {
                    val pcm = (floatBuffer[i].coerceIn(-1f, 1f) * 32767f).toInt()
                    shortBuffer[i] = pcm.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                track.write(shortBuffer, 0, sampleCount)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (t: Throwable) {
            lastError = t.message ?: "Playback engine stopped"
        } finally {
            reader?.close()
            releaseTrack()
        }
    }

    private fun createAudioTrack(sampleRate: Int): AudioTrack {
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufferBytes = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        val blockShorts = framesPerBlock * channels
        val bufferBytes = max(minBufferBytes, blockShorts * Short.SIZE_BYTES * 4)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(encoding)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
    }

    private fun renderMusicBlock(
        out: FloatArray,
        sampleRate: Int,
        localTone: Double,
        localDuration: Int,
    ) {
        val duration = localDuration.toDouble()
        val bassTone = (localTone / 4.0).coerceAtLeast(42.0)
        val chordTone = localTone / 2.0
        val beatHz = 1.85

        for (frame in 0 until framesPerBlock) {
            val t = playheadSec + frame / sampleRate.toDouble()
            val beatPhase = (t * beatHz) % 1.0
            val kickEnv = if (beatPhase < 0.18) exp(-beatPhase * 22.0) else 0.0
            val shimmer = 0.5 + 0.5 * sin(2.0 * PI * 0.07 * t)
            val pan = sin(2.0 * PI * 0.11 * t) * 0.18

            val kick = sin(2.0 * PI * (48.0 + localTone / 40.0) * t) * kickEnv * 0.12
            val bass = sin(2.0 * PI * bassTone * t) * 0.055
            val lead = (
                sin(2.0 * PI * localTone * t) * 0.026 +
                    sin(2.0 * PI * localTone * 1.5 * t) * 0.014
                ) * (0.74 + shimmer * 0.26)
            val pad = (
                sin(2.0 * PI * chordTone * t + sin(2.0 * PI * 0.045 * t)) +
                    sin(2.0 * PI * chordTone * 1.25 * t)
                ) * 0.018

            val left = (kick + bass + lead * (1.0 - pan) + pad * 0.92).toFloat()
            val right = (kick + bass + lead * (1.0 + pan) + pad * 1.08).toFloat()
            out[frame * 2] = left
            out[frame * 2 + 1] = right
        }

        playheadSec += framesPerBlock / sampleRate.toDouble()
        if (playheadSec >= duration) {
            playheadSec = duration
            playing = false
        }
    }

    private enum class SourceKind { Synth, Uri }

    private data class SourceSnapshot(
        val kind: SourceKind,
        val version: Int,
        val uri: Uri?,
        val toneHz: Double,
        val durationSec: Int,
        val seekProgress: Float?,
    )

    private class CodecPcmReader(
        context: Context,
        uri: Uri,
    ) : AutoCloseable {
        private val extractor = MediaExtractor()
        private val descriptor: ParcelFileDescriptor
        private val decoder: MediaCodec
        private val info = MediaCodec.BufferInfo()
        private var inputDone = false
        private var outputDone = false
        private var pending = FloatArray(0)
        private var pendingFrameOffset = 0
        private var pendingFrameCount = 0
        private var outputFormat: MediaFormat
        private var channelCount: Int
        private var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        val durationUs: Long
        val sampleRate: Int

        val isEnded: Boolean
            get() = outputDone && pendingFrameOffset >= pendingFrameCount

        init {
            descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open selected audio file")
            extractor.setDataSource(descriptor.fileDescriptor)

            var selectedTrack = -1
            var selectedFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    selectedTrack = i
                    selectedFormat = format
                    break
                }
            }

            val format = selectedFormat ?: error("No playable audio track found")
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Audio codec is unknown")
            extractor.selectTrack(selectedTrack)

            sampleRate = format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 48_000)
                .coerceIn(8_000, 192_000)
            channelCount = format.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
                .coerceAtLeast(1)
            durationUs = format.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)
            outputFormat = format

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
        }

        fun seekToProgress(progress: Float) {
            val targetUs = (durationUs * progress.coerceIn(0f, 1f)).toLong().coerceAtLeast(0L)
            extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            decoder.flush()
            inputDone = false
            outputDone = false
            pending = FloatArray(0)
            pendingFrameOffset = 0
            pendingFrameCount = 0
        }

        fun read(out: FloatArray, requestedFrames: Int): Int {
            var copiedFrames = copyPending(out, 0, requestedFrames)
            var idleCount = 0

            while (copiedFrames < requestedFrames && !outputDone && idleCount < 6) {
                feedInput()
                when (val status = decoder.dequeueOutputBuffer(info, 8_000L)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        idleCount += 1
                    }
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = decoder.outputFormat
                        channelCount = outputFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
                            .coerceAtLeast(1)
                        pcmEncoding = outputFormat.getIntegerOrDefault(
                            MediaFormat.KEY_PCM_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT,
                        )
                    }
                    else -> {
                        if (status >= 0) {
                            val buffer = decoder.getOutputBuffer(status)
                            if (buffer != null && info.size > 0) {
                                pending = convertOutputBuffer(buffer, info, channelCount, pcmEncoding)
                                pendingFrameOffset = 0
                                pendingFrameCount = pending.size / 2
                                copiedFrames += copyPending(out, copiedFrames, requestedFrames - copiedFrames)
                            }
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                            decoder.releaseOutputBuffer(status, false)
                        }
                    }
                }
            }

            return copiedFrames
        }

        private fun feedInput() {
            if (inputDone) return
            val inputIndex = decoder.dequeueInputBuffer(0L)
            if (inputIndex < 0) return

            val inputBuffer = decoder.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            val sampleSize = extractor.readSampleData(inputBuffer, 0)
            if (sampleSize < 0) {
                decoder.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    0L,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                inputDone = true
            } else {
                decoder.queueInputBuffer(
                    inputIndex,
                    0,
                    sampleSize,
                    extractor.sampleTime.coerceAtLeast(0L),
                    0,
                )
                extractor.advance()
            }
        }

        private fun copyPending(out: FloatArray, outFrameOffset: Int, frameCapacity: Int): Int {
            val available = (pendingFrameCount - pendingFrameOffset).coerceAtLeast(0)
            val framesToCopy = min(frameCapacity, available)
            if (framesToCopy > 0) {
                System.arraycopy(
                    pending,
                    pendingFrameOffset * 2,
                    out,
                    outFrameOffset * 2,
                    framesToCopy * 2,
                )
                pendingFrameOffset += framesToCopy
            }
            if (pendingFrameOffset >= pendingFrameCount) {
                pending = FloatArray(0)
                pendingFrameOffset = 0
                pendingFrameCount = 0
            }
            return framesToCopy
        }

        private fun convertOutputBuffer(
            buffer: ByteBuffer,
            bufferInfo: MediaCodec.BufferInfo,
            channels: Int,
            encoding: Int,
        ): FloatArray {
            val bytesPerSample = when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                else -> 2
            }
            val safeChannels = channels.coerceAtLeast(1)
            val frameCount = bufferInfo.size / (bytesPerSample * safeChannels)
            val converted = FloatArray(frameCount * 2)
            val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            data.position(bufferInfo.offset)
            data.limit(bufferInfo.offset + bufferInfo.size)

            for (frame in 0 until frameCount) {
                var left = 0f
                var right = 0f
                for (channel in 0 until safeChannels) {
                    val sample = readSample(data, encoding)
                    if (channel == 0) left = sample
                    if (channel == 1) right = sample
                }
                if (safeChannels == 1) right = left
                converted[frame * 2] = left
                converted[frame * 2 + 1] = right
            }
            return converted
        }

        private fun readSample(buffer: ByteBuffer, encoding: Int): Float =
            when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> buffer.getFloat().coerceIn(-1f, 1f)
                else -> (buffer.getShort() / 32768f).coerceIn(-1f, 1f)
            }

        override fun close() {
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            runCatching { extractor.release() }
            runCatching { descriptor.close() }
        }

        private fun MediaFormat.getIntegerOrDefault(key: String, fallback: Int): Int =
            if (containsKey(key)) getInteger(key) else fallback

        private fun MediaFormat.getLongOrDefault(key: String, fallback: Long): Long =
            if (containsKey(key)) getLong(key) else fallback
    }
}

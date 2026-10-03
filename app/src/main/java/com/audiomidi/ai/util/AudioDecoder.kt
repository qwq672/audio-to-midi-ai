package com.audiomidi.ai.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.audiomidi.ai.pipeline.AudioData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Decodes any audio file (MP3, M4A, WAV, FLAC, OGG, etc.) referenced by a
 * content URI into a mono/stereo float PCM [AudioData].
 *
 * Uses Android's MediaExtractor + MediaCodec — no external dependencies.
 * The codec outputs 16-bit signed PCM interleaved for stereo; we convert
 * to float in range [-1.0, 1.0] and pack into a flat FloatArray.
 *
 * Output format:
 *   - sampleRate: whatever the source file uses (caller must resample if
 *     the consumer needs a specific rate)
 *   - channels: 1 or 2 (matches source)
 *   - samples: FloatArray of size numFrames * channels, interleaved
 *             (for stereo: [L0, R0, L1, R1, ...])
 *
 * Note: this is a basic implementation. It does not handle:
 *   - Encrypted content
 *   - Multi-track selection (picks first audio track)
 *   - Drm-protected files
 *
 * On failure, throws with a descriptive message so the UI can show it
 * in the error card.
 */
class AudioDecoder(private val context: Context) {

    suspend fun decode(uri: Uri): AudioData = withContext(Dispatchers.IO) {
        Timber.i("Decoding audio from URI: $uri")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val audioTrackIdx = (0 until extractor.trackCount).firstOrNull { idx ->
                val mime = extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME)
                mime?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("No audio track found in file")

            extractor.selectTrack(audioTrackIdx)
            val format = extractor.getTrackFormat(audioTrackIdx)
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Track has no MIME type")

            Timber.i("Audio: $mime, $sampleRate Hz, $channels channel(s), duration ~${
                if (format.containsKey(MediaFormat.KEY_DURATION))
                    format.getLong(MediaFormat.KEY_DURATION) / 1000 else -1
            } ms")

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // Output format may differ from input format; use the actual
            // output format once we receive INFO_OUTPUT_FORMAT_CHANGED.
            var outSampleRate = sampleRate
            var outChannels = channels

            // Pre-allocate assuming up to 5 minutes of audio (avoids repeated grow)
            val initialCap = outSampleRate * 60 * 5 * outChannels
            val samples = ArrayList<Float>(initialCap.coerceAtMost(2_000_000))
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10000L
            var sawInputEOS = false
            var sawOutputEOS = false
            var idleCount = 0
            var totalSamples = 0

            while (!sawOutputEOS) {
                // Feed input
                if (!sawInputEOS) {
                    val inputIdx = codec.dequeueInputBuffer(timeoutUs)
                    if (inputIdx >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIdx)
                            ?: throw IllegalStateException("Input buffer null")
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(
                                inputIdx, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                // Drain output
                val outputIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outputIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output right now; if we've sent EOS and been idle
                        // for a long time (5 sec), assume codec finished.
                        if (sawInputEOS) {
                            idleCount++
                            if (idleCount > 500) {  // 500 * 10ms = 5 sec
                                Timber.w("Codec idle for >5s after EOS, breaking")
                                break
                            }
                        }
                    }
                    outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            outSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            outChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        Timber.i("Output format: $outSampleRate Hz, $outChannels ch")
                    }
                    outputIdx >= 0 -> {
                        idleCount = 0
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIdx)
                            if (outputBuffer != null) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val pcm = ByteArray(outputBuffer.remaining())
                                outputBuffer.get(pcm)
                                // PCM 16-bit LE interleaved -> float [-1, 1]
                                val n = pcm.size / 2
                                for (i in 0 until n) {
                                    val lo = pcm[i * 2].toInt() and 0xFF
                                    val hi = pcm[i * 2 + 1].toInt() and 0xFF
                                    val s = (hi shl 8) or lo
                                    val signed = if (s >= 32768) s - 65536 else s
                                    samples.add(signed / 32768f)
                                }
                                totalSamples += n
                            }
                        }
                        codec.releaseOutputBuffer(outputIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                        }
                    }
                }
            }

            Timber.i("Decoded $totalSamples samples, ${totalSamples / outChannels} frames, ${totalSamples.toFloat() / outSampleRate / outChannels} sec, $outSampleRate Hz, $outChannels ch")

            AudioData(
                sampleRate = outSampleRate,
                channels = outChannels,
                samples = samples.toFloatArray()
            )
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}

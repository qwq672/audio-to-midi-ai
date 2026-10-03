package com.audiomidi.ai.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.closeQuietly
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * HTTP downloader with resume support, parallel concurrency limit,
 * and TTFB (time-to-first-byte) probing.
 */
class Downloader(
    maxParallel: Int = 3
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // we handle fallback at higher level
        .build()

    private val activeCount = AtomicInteger(0)

    /**
     * Download [url] to [targetFile], resuming from [partialFile] if present.
     *
     * Uses HTTP Range header to resume. After successful completion, the
     * [partialFile] is deleted and the [targetFile] holds the full data.
     *
     * @param onProgress (downloadedBytes, totalBytes, bytesPerSecond) -> Unit
     */
    suspend fun downloadWithResume(
        url: String,
        targetFile: File,
        partialFile: File,
        expectedSize: Long,
        onProgress: (Long, Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Wait for a free slot if many parallel downloads
        while (activeCount.get() >= 4) {
            kotlinx.coroutines.delay(50)
        }
        activeCount.incrementAndGet()

        try {
            // Ensure parent dirs exist
            targetFile.parentFile?.mkdirs()
            partialFile.parentFile?.mkdirs()

            // Determine resume position from existing partial
            val existingBytes = if (partialFile.exists()) partialFile.length() else 0L

            val requestBuilder = Request.Builder().url(url).get()
            if (existingBytes > 0) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 416 && existingBytes > 0) {
                        // Range not satisfiable → file may already be complete
                        if (existingBytes == expectedSize) {
                            partialFile.renameTo(targetFile)
                            onProgress(expectedSize, expectedSize, 0)
                            return@withContext
                        }
                    }
                    throw RuntimeException("HTTP ${response.code} ${response.message}")
                }

                val totalContentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val isPartialResponse = response.code == 206
                val totalBytes = if (isPartialResponse && existingBytes > 0) {
                    existingBytes + totalContentLength
                } else if (totalContentLength > 0) {
                    totalContentLength
                } else {
                    expectedSize
                }

                val responseBody = response.body ?: throw RuntimeException("Empty body")
                val stream = responseBody.byteStream()

                // Open partial file in append mode if resuming, else overwrite
                val raf = RandomAccessFile(partialFile, "rw")
                try {
                    if (isPartialResponse) {
                        raf.seek(existingBytes)
                    } else {
                        raf.setLength(0)
                    }

                    val buf = ByteArray(64 * 1024)
                    var downloaded = if (isPartialResponse) existingBytes else 0L
                    var lastEmitTime = System.currentTimeMillis()
                    var lastEmitBytes = downloaded

                    while (true) {
                        val n = stream.read(buf)
                        if (n <= 0) break
                        raf.write(buf, 0, n)
                        downloaded += n

                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= 250) { // throttle progress to 4 Hz
                            val elapsedSec = (now - lastEmitTime) / 1000.0
                            val bytesDiff = downloaded - lastEmitBytes
                            val bps = if (elapsedSec > 0) (bytesDiff / elapsedSec).toLong() else 0L
                            onProgress(downloaded, totalBytes, bps)
                            lastEmitTime = now
                            lastEmitBytes = downloaded
                        }
                    }
                    onProgress(downloaded, totalBytes, 0)

                    // Move partial → final target atomically
                    raf.closeQuietly()
                    if (targetFile.exists()) targetFile.delete()
                    if (!partialFile.renameTo(targetFile)) {
                        // renameTo can fail on cross-mount; fall back to copy.
                        partialFile.copyTo(targetFile, overwrite = true)
                        partialFile.delete()
                    }
                } finally {
                    raf.closeQuietly()
                }
            }
        } finally {
            activeCount.decrementAndGet()
        }
    }

    /**
     * Measure time-to-first-byte for a URL. Used by RegionDetector.
     */
    suspend fun probeTtfb(url: String): Long = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            val req = Request.Builder().url(url).head().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) System.currentTimeMillis() - start
                else Long.MAX_VALUE
            }
        } catch (e: SocketTimeoutException) {
            Long.MAX_VALUE
        } catch (e: Exception) {
            Timber.w(e, "TTFB probe failed for $url")
            Long.MAX_VALUE
        }
    }
}

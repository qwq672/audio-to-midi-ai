package com.audiomidi.ai.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Result of writing a MIDI file to user-accessible storage.
 *
 * @property uri Content URI (e.g. content://media/external/downloads/123)
 *                for sharing/opening with other apps via Intent.
 * @property displayPath Human-readable path for UI display
 *                        (e.g. "Download/AudioToMidi/song_1.mid").
 */
data class MidiOutput(
    val uri: Uri,
    val displayName: String,
    val displayPath: String
)

/**
 * Writes MIDI byte arrays to user-accessible external storage.
 *
 * Behavior by Android version:
 *  - API 29+ (Android 10+): use MediaStore.Downloads with RELATIVE_PATH
 *    "Download/AudioToMidi/". No permission needed. Files appear in the
 *    system Downloads app and any file manager. Content URI is shareable
 *    with other apps via Intent + FLAG_GRANT_READ_URI_PERMISSION.
 *  - API 26-28 (Android 8-9): use direct file path
 *    `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)/AudioToMidi/`.
 *    Requires WRITE_EXTERNAL_STORAGE permission (declared in manifest).
 *    Returns a file:// URI wrapped by FileProvider for sharing.
 */
object MidiStorageWriter {

    private const val DIR_NAME = "AudioToMidi"
    private const val MIME_MIDI = "audio/midi"
    // Fallback if MediaStore doesn't accept audio/midi
    private const val MIME_MIDI_FALLBACK = "audio/x-midi"

    /**
     * Write [midiBytes] to user-accessible storage under
     * Download/{DIR_NAME}/[fileName].
     *
     * @return MidiOutput with the URI and display info, or null on failure.
     */
    fun write(
        context: Context,
        fileName: String,
        midiBytes: ByteArray
    ): MidiOutput? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, fileName, midiBytes)
        } else {
            writeViaDirectPath(context, fileName, midiBytes)
        }
    }

    private fun writeViaMediaStore(
        context: Context,
        fileName: String,
        midiBytes: ByteArray
    ): MidiOutput? {
        val resolver = context.contentResolver
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_MIDI)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DIR_NAME")
                // Mark as not pending so it's immediately visible
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
            }

            // First try MediaStore.Downloads collection
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collection, values)
                ?: throw IOException("MediaStore.insert returned null")

            resolver.openOutputStream(uri)?.use { os ->
                os.write(midiBytes)
                os.flush()
            } ?: throw IOException("openOutputStream returned null")

            Timber.i("Wrote %d bytes to MediaStore URI: %s", midiBytes.size, uri)
            MidiOutput(
                uri = uri,
                displayName = fileName,
                displayPath = "${Environment.DIRECTORY_DOWNLOADS}/$DIR_NAME/$fileName"
            )
        } catch (e: Exception) {
            Timber.e(e, "MediaStore write failed; falling back to direct file path")
            writeViaDirectPath(context, fileName, midiBytes)
        }
    }

    private fun writeViaDirectPath(
        context: Context,
        fileName: String,
        midiBytes: ByteArray
    ): MidiOutput? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val outDir = File(downloadsDir, DIR_NAME).apply { if (!exists()) mkdirs() }
            val outFile = File(outDir, fileName)
            outFile.outputStream().use { os ->
                os.write(midiBytes)
                os.flush()
            }
            Timber.i("Wrote %d bytes to direct file: %s", midiBytes.size, outFile.absolutePath)

            // Wrap with FileProvider URI so other apps can read it.
            val authority = "${context.packageName}.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, outFile)
            MidiOutput(
                uri = uri,
                displayName = fileName,
                displayPath = "${Environment.DIRECTORY_DOWNLOADS}/$DIR_NAME/$fileName"
            )
        } catch (e: Exception) {
            Timber.e(e, "Direct path write failed")
            null
        }
    }
}

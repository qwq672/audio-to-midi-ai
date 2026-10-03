package com.audiomidi.ai.pipeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.audiomidi.ai.R
import timber.log.Timber

/**
 * Foreground service that runs the pipeline so that long-running audio
 * transcription survives screen-off and background limits on Android 14+.
 *
 * **Status: Skeleton.**
 * Real implementation should:
 *   - Receive an Intent with audio path + genre config
 *   - Build the [com.audiomidi.ai.pipeline.PipelineExecutor]
 *   - Run it on a background coroutine
 *   - Update a foreground notification with progress
 *   - Stop self when done
 */
class PipelineService : Service() {

    companion object {
        private const val CHANNEL_ID = "audio_to_midi_pipeline"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, audioPath: String, genreId: String) {
            val intent = Intent(context, PipelineService::class.java).apply {
                putExtra("audio_path", audioPath)
                putExtra("genre_id", genreId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PipelineService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification("准备处理")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val audioPath = intent?.getStringExtra("audio_path")
        val genreId = intent?.getStringExtra("genre_id")
        Timber.i("PipelineService started: audio=$audioPath genre=$genreId")

        // TODO: real pipeline execution.
        // For now, stop the service immediately.
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio → MIDI Pipeline",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service for audio-to-MIDI processing"
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio To MIDI")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }
}

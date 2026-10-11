package dev.local.bedrockchat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** True while an app screen is visible. Set by MainActivity. */
object AppVisibility {
    @Volatile var inFront = false

    /** A chat to show, set when you tap "Claude responded". An empty id shows the current chat. */
    val openChat = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

/**
 * Keeps the app running while Claude writes a chat reply, so that the reply continues when you leave the app.
 * When the reply finishes and the app is in the background, it posts "Claude responded".
 * It runs only while a reply streams, and only when "Notify when Claude responds" is on.
 */
class ReplyService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels(this)
        val n = NotificationCompat.Builder(this, CHANNEL_WORKING)
            .setSmallIcon(R.drawable.ic_notify_mark)
            .setContentTitle("Claude is responding…")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openIntent(this, null))
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(WORKING_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(WORKING_ID, n)
        }
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_CHAT = "chat_id"
        private const val CHANNEL_WORKING = "chat_working"
        private const val CHANNEL_REPLIES = "chat_replies"
        private const val WORKING_ID = 2
        private const val REPLY_ID = 3

        /** Call when a reply starts, while the app is in front. */
        fun start(context: Context) {
            if (!(context.applicationContext as App).settings.chatNotifications) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ReplyService::class.java)) }
        }

        /**
         * Call when a reply ends. [text] is null when you stopped the reply yourself: then nothing is posted.
         * For an incognito chat, the notification does not show the reply.
         */
        fun finish(context: Context, chatId: String?, title: String, text: String?, failed: Boolean, incognito: Boolean) {
            context.stopService(Intent(context, ReplyService::class.java))
            val settings = (context.applicationContext as App).settings
            if (text == null || AppVisibility.inFront || !settings.chatNotifications) return
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            createChannels(context)
            val body = when {
                failed -> "The reply did not finish. Open the chat to try again."
                incognito -> "Your reply is ready."
                else -> PcWatchService.preview(text)
            }
            val n = NotificationCompat.Builder(context, CHANNEL_REPLIES)
                .setSmallIcon(R.drawable.ic_notify_mark)
                .setContentTitle(if (failed) "Claude couldn't finish" else "Claude responded")
                .setSubText(if (incognito) "Incognito chat" else title.ifBlank { null })
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(openIntent(context, chatId.takeIf { !incognito } ?: ""))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build()
            NotificationManagerCompat.from(context).notify(REPLY_ID, n)
        }

        /** Removes the "Claude responded" notification, for example when you come back to the app. */
        fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(REPLY_ID)

        private fun openIntent(context: Context, chatId: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { chatId?.let { putExtra(EXTRA_CHAT, it) } }
            return PendingIntent.getActivity(context, 100, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun createChannels(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
                NotificationChannel(CHANNEL_REPLIES, "Chat replies", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Claude finished a reply while you were away" },
                NotificationChannel(CHANNEL_WORKING, "Reply in progress", NotificationManager.IMPORTANCE_MIN)
                    .apply { description = "Shows while Claude writes a reply, so it continues in the background" },
            ))
        }
    }
}

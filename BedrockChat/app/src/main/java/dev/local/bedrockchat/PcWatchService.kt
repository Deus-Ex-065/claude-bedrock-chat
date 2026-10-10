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
import dev.local.bedrockchat.data.BridgeClient
import dev.local.bedrockchat.data.BridgeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What the PC screens show now. The service uses it, and notifications use it to open a session. */
object PcScreenState {
    /** The PC session on screen while the app is in front, so the app does not notify about it. */
    @Volatile var visibleSession: String? = null

    /** A session to open, set when you tap a notification. */
    val openRequest = MutableStateFlow<String?>(null)
}

/**
 * Keeps the bridge stream open while the app is in the background and posts notifications.
 * It notifies only for sessions that you control from the phone: an approval that waits for the phone,
 * and a finished turn in a session with "Approve on this phone" on or a turn that a phone message started.
 * Turns started on the PC do not notify, because you are at the PC. It never calls Claude, so it uses no tokens.
 */
class PcWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val titles = mutableMapOf<String, String>()
    private var pcName = "the PC"

    /**
     * Android requires a visible notification while an app keeps a connection open in the background.
     * It shows the real connection state. [connected] is null before the first answer from the bridge.
     */
    private fun ongoing(connected: Boolean?) = NotificationCompat.Builder(this, CHANNEL_WATCH)
        .setSmallIcon(R.drawable.ic_notify_spark)
        .setContentTitle(when (connected) {
            true -> "Connected to $pcName"
            false -> "Reconnecting to $pcName…"
            null -> "Connecting to $pcName…"
        })
        .setContentText(
            if (connected == false) "Alerts resume when the phone reaches the PC again"
            else "Alerts on for approvals and finished replies"
        )
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(openIntent(this, null))
        .addAction(0, "Turn off alerts", PendingIntent.getService(this, 1,
            Intent(this, PcWatchService::class.java).setAction(ACTION_TURN_OFF), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private var shownConnected: Boolean? = null

    private fun showConnected(connected: Boolean) {
        if (shownConnected == connected) return
        shownConnected = connected // Also without the permission, so onStartCommand shows it when it is granted.
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) NotificationManagerCompat.from(this).notify(ONGOING_ID, ongoing(connected))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = (application as App).settings
        if (intent?.action == ACTION_TURN_OFF) settings.pcNotifications = false
        val link = settings.bridgeLink()
        if (link == null || !settings.pcNotifications) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannels(this)
        pcName = link.name
        startForeground(ONGOING_ID, ongoing(shownConnected)) // A restart keeps the known state.
        if (job?.isActive != true) job = scope.launch { watch(BridgeClient(link)) }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun watch(client: BridgeClient) {
        try {
            client.events().collect { e ->
                val sid = e.sessionId
                when (e.event) {
                    BridgeClient.CONNECTED -> showConnected(true)
                    BridgeClient.DISCONNECTED -> showConnected(false)
                    "ApprovalNeeded" -> post(client, sid, KIND_APPROVAL, CHANNEL_APPROVALS, "Approval needed",
                        "Claude wants to use ${e.tool ?: "a tool"}" + (e.input?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""))
                    "Stop" -> {
                        cancel(this, sid, KIND_APPROVAL)
                        if (e.remote || e.phoneTurn) {
                            post(client, sid, KIND_REPLY, CHANNEL_REPLIES, "Claude finished", preview(e.text))
                        }
                    }
                    // Remove the approval notification when nothing waits any more (answered on the phone or the PC).
                    "ControlChanged" -> if (runCatching { client.control(sid).pending.isEmpty() }.getOrDefault(false)) {
                        cancel(this, sid, KIND_APPROVAL)
                    }
                    "SessionEnd" -> cancel(this, sid, KIND_APPROVAL)
                }
            }
        } catch (e: BridgeException) {
            stopSelf() // No longer paired.
        }
    }

    private suspend fun post(client: BridgeClient, sid: String, kind: Int, channel: String, label: String, text: String) {
        if (PcScreenState.visibleSession == sid) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val title = titles[sid] ?: runCatching { client.sessions() }.getOrNull()
            ?.onEach { titles[it.id] = it.title }
            .let { titles[sid] }
            ?.ifBlank { null } ?: "PC session"
        val n = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notify_spark)
            .setContentTitle(title)
            .setSubText(label)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openIntent(this, sid))
            .setCategory(if (kind == KIND_APPROVAL) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_MESSAGE)
            .build()
        NotificationManagerCompat.from(this).notify(id(sid, kind), n)
    }

    companion object {
        const val EXTRA_SESSION = "pc_session"
        private const val ACTION_TURN_OFF = "dev.local.bedrockchat.TURN_OFF_ALERTS"
        private const val CHANNEL_WATCH = "pc_watch"
        private const val CHANNEL_APPROVALS = "pc_approvals"
        private const val CHANNEL_REPLIES = "pc_replies"
        private const val ONGOING_ID = 1
        private const val KIND_APPROVAL = 0
        private const val KIND_REPLY = 1
        private val PHONE_QUOTE = Regex("""\A\s*> 📱 \*\*From your phone:\*\*[^\n]*\n*""")

        /** Starts or stops the service to match the setting and the pairing. Call it while the app is in front. */
        fun sync(context: Context) {
            val settings = (context.applicationContext as App).settings
            val intent = Intent(context, PcWatchService::class.java)
            if (settings.pcNotifications && settings.bridgeLink() != null) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }

        /** Removes the notifications of a session, for example when you open it. */
        fun clear(context: Context, sid: String) {
            cancel(context, sid, KIND_APPROVAL)
            cancel(context, sid, KIND_REPLY)
        }

        private fun cancel(context: Context, sid: String, kind: Int) = NotificationManagerCompat.from(context).cancel(id(sid, kind))

        private fun id(sid: String, kind: Int) = 1000 + (sid.hashCode() and 0xFFFFFF) * 2 + kind

        /** A short preview on one line: the full reply is in the app. Cut at a word, at about 160 characters. */
        private fun preview(text: String?): String {
            val clean = PHONE_QUOTE.replace(text.orEmpty(), "")
                .replace(Regex("[*`#>|]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (clean.isEmpty()) return "The reply is ready."
            if (clean.length <= PREVIEW_LENGTH) return clean
            val cut = clean.take(PREVIEW_LENGTH)
            // End at a full sentence if one ends in the second half. It reads as finished, so it needs no "…".
            Regex("""[.!?](?=\s)""").findAll(cut).lastOrNull()?.range?.last
                ?.takeIf { it > PREVIEW_LENGTH / 2 }
                ?.let { return cut.take(it + 1) }
            val end = cut.lastIndexOf(' ').takeIf { it > PREVIEW_LENGTH / 2 } ?: PREVIEW_LENGTH
            return cut.take(end).trimEnd(',', '.', ':', ';', '-') + "…"
        }

        private const val PREVIEW_LENGTH = 160

        private fun openIntent(context: Context, sid: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { sid?.let { putExtra(EXTRA_SESSION, it) } }
            return PendingIntent.getActivity(context, sid?.hashCode() ?: 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(
                NotificationChannel(CHANNEL_APPROVALS, "Approvals", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Claude on the PC waits for Allow or Deny" },
                NotificationChannel(CHANNEL_REPLIES, "Finished replies", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Claude on the PC finished a turn that you started or control from the phone" },
                NotificationChannel(CHANNEL_WATCH, "Background connection", NotificationManager.IMPORTANCE_MIN)
                    .apply { description = "Shows while the app watches the PC in the background" },
            ))
        }
    }
}

package dev.local.bedrockchat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.local.bedrockchat.data.BridgeClient
import dev.local.bedrockchat.data.BridgeException
import dev.local.bedrockchat.data.BridgeLink
import dev.local.bedrockchat.data.PcControl
import dev.local.bedrockchat.data.PcMessage
import dev.local.bedrockchat.data.PcSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PcUiState(
    val link: BridgeLink? = null,
    val sessions: List<PcSession> = emptyList(),
    val loadingSessions: Boolean = false,
    val openId: String? = null,
    val messages: List<PcMessage> = emptyList(),
    val loadingMessages: Boolean = false,
    /** Approvals, queued messages, and whether approvals go to the phone, for the open session. */
    val control: PcControl = PcControl(),
    /** What each session is doing right now, from hook events, e.g. "Running Bash: ls". */
    val activity: Map<String, String> = emptyMap(),
    /** Set when the live connection is down; the screens still show the last data. */
    val connected: Boolean = false,
    val error: String? = null,
)

/**
 * Watches and controls Claude Code sessions on the paired PC. It never calls Bedrock itself:
 * messages and approvals go to Claude Code on the PC through the bridge.
 */
class PcViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = (app as App).settings

    private val _state = MutableStateFlow(PcUiState(link = settings.bridgeLink()))
    val state: StateFlow<PcUiState> = _state.asStateFlow()

    private var client: BridgeClient? = settings.bridgeLink()?.let(::BridgeClient)
    private var liveJob: Job? = null
    private var sessionsRefresh: Job? = null
    private var messagesRefresh: Job? = null
    private var liveUsers = 0

    private val _notifications = MutableStateFlow(settings.pcNotifications)
    /** True while the app watches the PC in the background and posts notifications. */
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    private val _askPermissions = MutableStateFlow(false)
    /** True right after pairing: the screen asks for the permissions that notifications need. */
    val askPermissions: StateFlow<Boolean> = _askPermissions.asStateFlow()
    fun permissionsAsked() { _askPermissions.value = false }

    fun setNotifications(on: Boolean) {
        settings.pcNotifications = on
        _notifications.value = on
        PcWatchService.sync(getApplication())
    }

    suspend fun pair(address: String, code: String): String? = try {
        val link = BridgeClient.pair(address, code)
        settings.saveBridgeLink(link)
        PcWatchService.sync(getApplication())
        if (settings.pcNotifications) _askPermissions.value = true
        client = BridgeClient(link)
        _state.value = PcUiState(link = link)
        refreshSessions()
        if (liveUsers > 0) startLiveJob()
        null
    } catch (e: Exception) {
        e.message ?: "Pairing failed."
    }

    fun unpair() {
        stopLiveJob()
        settings.clearBridgeLink()
        PcWatchService.sync(getApplication())
        client = null
        _state.value = PcUiState()
    }

    fun refreshSessions() {
        val c = client ?: return
        viewModelScope.launch {
            _state.update { it.copy(loadingSessions = it.sessions.isEmpty()) }
            runCatching { retrying { c.sessions() } }
                .onSuccess { list -> _state.update { it.copy(sessions = list, loadingSessions = false, error = null) } }
                .onFailure { e -> fail(e); _state.update { it.copy(loadingSessions = false) } }
        }
    }

    fun open(sessionId: String) {
        _state.update { it.copy(openId = sessionId, messages = emptyList(), control = PcControl()) }
        refreshMessages(showLoading = true)
        refreshControl()
        // Opened from a notification, the list may not be loaded yet. The header needs it for the title and status.
        if (_state.value.sessions.none { it.id == sessionId }) refreshSessions()
    }

    fun close() = _state.update { it.copy(openId = null, messages = emptyList(), control = PcControl()) }

    /** Sends a message to the open session. Returns an error to show, or null. */
    suspend fun send(text: String): String? {
        val c = client ?: return "Not paired."
        val id = _state.value.openId ?: return null
        return try {
            c.sendPrompt(id, text)
            refreshControl()
            null
        } catch (e: Exception) {
            e.message ?: "Could not send the message."
        }
    }

    fun setRemote(on: Boolean) {
        val c = client ?: return
        val id = _state.value.openId ?: return
        viewModelScope.launch {
            runCatching { c.setRemote(id, on) }
                .onSuccess { control -> _state.update { if (it.openId == id) it.copy(control = control) else it } }
                .onFailure(::fail)
        }
    }

    fun decide(requestId: String, allow: Boolean, always: Boolean = false) {
        val c = client ?: return
        viewModelScope.launch {
            runCatching { c.decide(requestId, allow, always) }.onFailure(::fail)
            refreshControl()
        }
    }

    private fun refreshControl() {
        val c = client ?: return
        val id = _state.value.openId ?: return
        viewModelScope.launch {
            runCatching { retrying { c.control(id) } }
                .onSuccess { control -> _state.update { if (it.openId == id) it.copy(control = control) else it } }
        }
    }

    private fun refreshMessages(showLoading: Boolean = false) {
        val c = client ?: return
        val id = _state.value.openId ?: return
        viewModelScope.launch {
            if (showLoading) _state.update { it.copy(loadingMessages = true) }
            runCatching { retrying { c.messages(id) } }
                .onSuccess { list ->
                    _state.update { if (it.openId == id) it.copy(messages = list, loadingMessages = false, error = null) else it }
                }
                .onFailure { e -> fail(e); _state.update { it.copy(loadingMessages = false) } }
        }
    }

    /** Short network drops are common on phones (screen wake, Wi-Fi roaming). Try again before warning. */
    private suspend fun <T> retrying(block: suspend () -> T): T {
        repeat(2) { attempt ->
            try {
                return block()
            } catch (e: BridgeException) {
                throw e // The bridge answered; trying again does not help.
            } catch (e: Exception) {
                android.util.Log.w("PcViewModel", "Bridge request failed, trying again", e)
                delay(1000L * (attempt + 1))
            }
        }
        return block()
    }

    private fun fail(e: Throwable) {
        // The app cancels the live connection when you move between PC screens. That is not a network error.
        if (e is kotlinx.coroutines.CancellationException) return
        val message = if (e is BridgeException) e.message else "Can't reach ${_state.value.link?.name ?: "the PC"}. Is the bridge running?"
        _state.update { it.copy(error = message) }
    }

    // ---- Live updates ----
    // The connection stays open only while a PC screen is visible.

    fun startLive() {
        _notifications.value = settings.pcNotifications // It can be turned off from the notification.
        liveUsers++
        if (liveUsers == 1) startLiveJob()
    }

    fun stopLive() {
        liveUsers = (liveUsers - 1).coerceAtLeast(0)
        if (liveUsers == 0) stopLiveJob()
    }

    private fun startLiveJob() {
        val c = client ?: return
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            var dropped = false
            try {
                c.events().collect { e ->
                    when (e.event) {
                        BridgeClient.CONNECTED -> {
                            _state.update { it.copy(connected = true, error = null) }
                            if (dropped) {
                                // Catch up on anything missed while the connection was down.
                                refreshSessions(); refreshMessages(); refreshControl()
                            }
                            dropped = false
                            return@collect
                        }
                        BridgeClient.DISCONNECTED -> {
                            dropped = true
                            _state.update { it.copy(connected = false) }
                            return@collect
                        }
                    }
                    _state.update {
                        val activity = if (e.activity != null) it.activity + (e.sessionId to e.activity) else it.activity - e.sessionId
                        it.copy(activity = activity, connected = true, error = null)
                    }
                    sessionsRefresh?.cancel()
                    sessionsRefresh = launch { delay(700); refreshSessions() }
                    if (e.sessionId == _state.value.openId && e.event == "ControlChanged") refreshControl()
                    if (e.sessionId == _state.value.openId && e.event in REFRESH_EVENTS) {
                        messagesRefresh?.cancel()
                        messagesRefresh = launch { delay(600); refreshMessages() }
                    }
                }
            } catch (e: Exception) {
                fail(e)
            } finally {
                _state.update { it.copy(connected = false) }
            }
        }
    }

    private fun stopLiveJob() {
        liveJob?.cancel()
        liveJob = null
    }

    private companion object {
        val REFRESH_EVENTS = setOf("UserPromptSubmit", "PostToolUse", "Stop", "SessionStart", "ControlChanged")
    }
}

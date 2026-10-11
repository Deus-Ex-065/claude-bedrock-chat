package dev.local.bedrockchat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Where the PC bridge is and the token from pairing. The address can be a LAN, VPN, or tunnel URL. */
data class BridgeLink(val baseUrl: String, val token: String, val name: String)

@Serializable
data class PcSession(
    @SerialName("session_id") val id: String,
    val title: String = "",
    val cwd: String? = null,
    @SerialName("last_modified") val lastModified: Long = 0,
    val live: Boolean = false,
    val status: String? = null,
    val activity: String? = null,
    /** True while approvals for this session go to the phone. */
    val remote: Boolean = false,
    /** Permission requests waiting for the phone. */
    val pending: Int = 0,
)

/** A tool call that waits for Allow or Deny on the phone. */
@Serializable
data class PcPermission(
    val id: String,
    val tool: String = "Tool",
    val summary: String = "",
    val input: JsonElement? = null,
    @SerialName("can_always") val canAlways: Boolean = false,
)

@Serializable
data class PcControl(
    val remote: Boolean = false,
    val pending: List<PcPermission> = emptyList(),
    /** Phone messages that wait for the session to finish its turn. */
    val queued: List<String> = emptyList(),
    /** IDs of [queued], in the same order, to delete one. Empty from an older bridge. */
    @SerialName("queued_ids") val queuedIds: List<String> = emptyList(),
    /** True after you tapped Stop, until the turn ends. */
    val stopping: Boolean = false,
    /** True while the bridge runs `claude -p --resume` for this session. */
    @SerialName("running_here") val runningHere: Boolean = false,
)

/** One message in Agent SDK shape. Content blocks are Claude API blocks: text, thinking, tool_use, tool_result. */
@Serializable
data class PcMessage(
    val type: String,
    val uuid: String? = null,
    val message: PcMessageBody,
    /** Sent from the phone. Claude got it from a hook, so it is not a user record in the transcript. */
    @SerialName("from_phone") val fromPhone: Boolean = false,
)

@Serializable
data class PcMessageBody(val role: String = "", val content: List<JsonObject> = emptyList())

@Serializable
data class PcEvent(
    val event: String,
    @SerialName("session_id") val sessionId: String,
    val activity: String? = null,
    val message: String? = null,
    /** ApprovalNeeded: the tool and a short form of its input. */
    val tool: String? = null,
    val input: String? = null,
    /** Stop: Claude's final reply for the turn. */
    val text: String? = null,
    /** Stop: a phone message started this turn. */
    @SerialName("phone_turn") val phoneTurn: Boolean = false,
    /** Stop: approvals for the session go to the phone. */
    val remote: Boolean = false,
)

class BridgeException(message: String) : IOException(message)

/** Talks to the bridge on the PC. Only messages sent from the phone reach Claude. */
class BridgeClient(private val link: BridgeLink) {

    suspend fun sessions(): List<PcSession> = get("/api/sessions?limit=40")

    suspend fun messages(sessionId: String): List<PcMessage> = get("/api/sessions/$sessionId/messages")

    suspend fun control(sessionId: String): PcControl = get("/api/sessions/$sessionId/control")

    suspend fun setRemote(sessionId: String, on: Boolean): PcControl =
        post("/api/sessions/$sessionId/remote", buildJsonObject { put("on", on) })

    /** Returns "queued" for an open session, or "started" when the bridge runs it with `claude -p`. */
    suspend fun sendPrompt(sessionId: String, text: String): String =
        post<JsonObject>("/api/sessions/$sessionId/prompt", buildJsonObject { put("text", text) })["result"]
            ?.jsonPrimitive?.content ?: "queued"

    /** Stops the current turn and removes the messages that wait to send. */
    suspend fun stop(sessionId: String): String =
        post<JsonObject>("/api/sessions/$sessionId/stop", buildJsonObject {})["result"]?.jsonPrimitive?.content ?: "stopping"

    /** Deletes a phone message that was not sent to Claude yet. */
    suspend fun unqueue(sessionId: String, queuedId: String): PcControl =
        post("/api/sessions/$sessionId/unqueue", buildJsonObject { put("id", queuedId) })

    suspend fun decide(requestId: String, allow: Boolean, always: Boolean = false) {
        post<JsonObject>("/api/permissions/$requestId", buildJsonObject {
            put("behavior", if (allow) "allow" else "deny")
            put("always", always)
        })
    }

    /** Hook events as they happen. Reconnects after drops until the collector stops. */
    fun events(): Flow<PcEvent> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            var lastId: String? = null
            while (isActive) {
                try {
                    val conn = open("/api/stream").apply {
                        readTimeout = 40_000 // The bridge sends a keep-alive every 15 s.
                        lastId?.let { setRequestProperty("Last-Event-ID", it) }
                    }
                    check(conn)
                    trySend(PcEvent(CONNECTED, ""))
                    conn.inputStream.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            if (!isActive) break
                            when {
                                line.startsWith("id: ") -> lastId = line.removePrefix("id: ")
                                line.startsWith("data: ") -> runCatching {
                                    json.decodeFromString<PcEvent>(line.removePrefix("data: "))
                                }.onSuccess { trySend(it) }
                            }
                        }
                    }
                } catch (e: BridgeException) {
                    close(e)
                    return@launch
                } catch (_: Exception) {
                    // Network drop or timeout: try again shortly.
                }
                trySend(PcEvent(DISCONNECTED, ""))
                delay(3000)
            }
        }
        awaitClose { job.cancel() }
    }

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val conn = open(path)
        check(conn)
        json.decodeFromString<T>(conn.inputStream.bufferedReader().use { it.readText() })
    }

    private suspend inline fun <reified T> post(path: String, body: JsonObject): T = withContext(Dispatchers.IO) {
        val conn = open(path).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        check(conn)
        json.decodeFromString<T>(conn.inputStream.bufferedReader().use { it.readText() })
    }

    private fun open(path: String): HttpURLConnection =
        (URL(link.baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer ${link.token}")
            setRequestProperty("X-Device-Name", DEVICE_NAME)
            // A new connection each time. Reused connections go dead when the bridge restarts,
            // and then every retry can fail on another dead one.
            setRequestProperty("Connection", "close")
        }

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        /** Stream status events that the app makes itself. They never come from the bridge. */
        const val CONNECTED = "_Connected"
        const val DISCONNECTED = "_Disconnected"

        /** Shown in the bridge's terminal, for example "OnePlus CPH2381". */
        val DEVICE_NAME: String = listOf(android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, android.os.Build.MODEL)
            .distinct().joinToString(" ")

        private fun check(conn: HttpURLConnection) {
            val code = conn.responseCode
            if (code == 401) throw BridgeException("This phone is no longer paired. Pair it again.")
            if (code !in 200..299) throw BridgeException(errorText(conn) ?: "The bridge returned $code.")
        }

        private fun errorText(conn: HttpURLConnection): String? = runCatching {
            val body = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: return null
            json.decodeFromString<JsonObject>(body)["error"]?.jsonPrimitive?.content
        }.getOrNull()

        /** Exchanges a pairing code for the token. Returns the link or throws with a readable message. */
        suspend fun pair(baseUrl: String, code: String): BridgeLink = withContext(Dispatchers.IO) {
            val base = normalize(baseUrl)
            val conn = try {
                (URL("$base/api/pair").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 15_000
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Device-Name", DEVICE_NAME)
                    outputStream.use { it.write("""{"code":"${code.filter(Char::isDigit)}"}""".toByteArray()) }
                }
            } catch (e: IOException) {
                throw BridgeException("Could not reach $base. Check that the bridge is running and the phone is on the same network.")
            }
            val status = try { conn.responseCode } catch (e: IOException) {
                throw BridgeException("Could not reach $base. Check that the bridge is running and the phone is on the same network.")
            }
            if (status != 200) throw BridgeException(errorText(conn) ?: "Pairing failed ($status).")
            val body = json.decodeFromString<Map<String, JsonElement>>(conn.inputStream.bufferedReader().use { it.readText() })
            BridgeLink(base, body.getValue("token").jsonPrimitive.content, body["name"]?.jsonPrimitive?.content ?: "PC")
        }

        /** Accepts "192.168.0.6", "192.168.0.6:8765", or a full URL. */
        fun normalize(input: String): String {
            var s = input.trim().trimEnd('/')
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            val hostPart = s.substringAfter("://")
            if (s.startsWith("http://") && ':' !in hostPart) s += ":8765"
            return s
        }
    }
}

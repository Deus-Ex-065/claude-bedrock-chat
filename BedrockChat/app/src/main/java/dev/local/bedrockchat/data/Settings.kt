package dev.local.bedrockchat.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where Bedrock may process requests. */
enum class Routing(val label: String, val description: String) {
    /** bedrock-runtime with `global.` inference profiles (the route Claude Code uses). */
    GLOBAL("Global", "Any AWS region with capacity. Best availability, base price."),
    /** bedrock-runtime with a geographic inference profile such as `us.` or `eu.`. */
    GEOGRAPHIC("Geographic", "Stays within your geography (US, EU, Japan, Australia, or Asia Pacific). About 10% more."),
    /** bedrock-mantle Messages API endpoint in the selected region. */
    IN_REGION("In-region", "Only the selected region, through the Messages API endpoint. About 10% more."),
}

/** Inference-profile prefix for geographic routing, or null if the region has none. */
fun geoPrefix(region: String): String? = when {
    region.startsWith("us-") || region.startsWith("ca-") -> "us"
    region.startsWith("eu-") -> "eu"
    region == "ap-northeast-1" || region == "ap-northeast-3" -> "jp"
    region == "ap-southeast-2" || region == "ap-southeast-4" -> "au"
    region.startsWith("ap-") -> "apac"
    else -> null
}

data class Credentials(val apiKey: String, val region: String, val routing: Routing = Routing.GLOBAL)

enum class ColorMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/**
 * App settings. The Bedrock API key is encrypted with an AES-GCM key held in the
 * Android Keystore, so it never sits in plain text on disk.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun credentials(): Credentials? {
        val encrypted = prefs.getString(KEY_API_KEY, null) ?: return null
        val region = prefs.getString(KEY_REGION, null) ?: return null
        val apiKey = runCatching { decrypt(encrypted) }.getOrNull() ?: return null
        val routing = runCatching { Routing.valueOf(prefs.getString(KEY_ROUTING, null)!!) }.getOrNull()
            // Earlier versions stored an endpoint instead; keys from before that used the Messages API endpoint.
            ?: if (prefs.getString(KEY_ENDPOINT, null) == "RUNTIME") Routing.GLOBAL else Routing.IN_REGION
        return Credentials(apiKey, region, routing)
    }

    fun saveCredentials(credentials: Credentials) {
        prefs.edit()
            .putString(KEY_API_KEY, encrypt(credentials.apiKey))
            .putString(KEY_REGION, credentials.region)
            .putString(KEY_ROUTING, credentials.routing.name)
            .remove(KEY_ENDPOINT)
            .apply()
    }

    fun signOut() {
        prefs.edit().remove(KEY_API_KEY).remove(KEY_REGION).remove(KEY_ENDPOINT).remove(KEY_ROUTING).apply()
    }

    /** The paired PC bridge. The token is encrypted like the API key. */
    fun bridgeLink(): BridgeLink? {
        val url = prefs.getString(KEY_BRIDGE_URL, null) ?: return null
        val token = prefs.getString(KEY_BRIDGE_TOKEN, null)?.let { runCatching { decrypt(it) }.getOrNull() } ?: return null
        return BridgeLink(url, token, prefs.getString(KEY_BRIDGE_NAME, null) ?: "PC")
    }

    fun saveBridgeLink(link: BridgeLink) {
        prefs.edit()
            .putString(KEY_BRIDGE_URL, link.baseUrl)
            .putString(KEY_BRIDGE_TOKEN, encrypt(link.token))
            .putString(KEY_BRIDGE_NAME, link.name)
            .apply()
    }

    /** True while the app watches the PC in the background and posts notifications. On unless you turn it off. */
    var pcNotifications: Boolean
        get() = prefs.getBoolean(KEY_PC_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_PC_NOTIFICATIONS, value).apply()

    fun clearBridgeLink() {
        prefs.edit().remove(KEY_BRIDGE_URL).remove(KEY_BRIDGE_TOKEN).remove(KEY_BRIDGE_NAME).apply()
    }

    var modelId: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var effort: Effort
        get() = runCatching { Effort.valueOf(prefs.getString(KEY_EFFORT, null)!!) }.getOrDefault(Effort.MEDIUM)
        set(value) = prefs.edit().putString(KEY_EFFORT, value.name).apply()

    var colorMode: ColorMode
        get() = runCatching { ColorMode.valueOf(prefs.getString(KEY_COLOR_MODE, null)!!) }.getOrDefault(ColorMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_COLOR_MODE, value.name).apply()

    var hapticFeedback: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    var displayName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    /** True after the first-start name question was answered or skipped. */
    var askedName: Boolean
        get() = prefs.getBoolean(KEY_ASKED_NAME, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED_NAME, value).apply()

    /** Notify when a chat reply finishes while the app is in the background. Off until the user turns it on. */
    var chatNotifications: Boolean
        get() = prefs.getBoolean(KEY_CHAT_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_CHAT_NOTIFICATIONS, value).apply()

    /** Let Claude read links (web pages and PDFs). The phone downloads them. */
    var readLinks: Boolean
        get() = prefs.getBoolean(KEY_READ_LINKS, true)
        set(value) = prefs.edit().putBoolean(KEY_READ_LINKS, value).apply()

    /** True after the "Get notified when Claude responds" card was answered or closed. */
    var askedChatNotifications: Boolean
        get() = prefs.getBoolean(KEY_ASKED_CHAT_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED_CHAT_NOTIFICATIONS, value).apply()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        return "$iv:$data"
    }

    private fun decrypt(stored: String): String {
        val (iv, data) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)))
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "bedrock_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_API_KEY = "api_key"
        const val KEY_REGION = "region"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_ROUTING = "routing"
        const val KEY_MODEL = "model"
        const val KEY_EFFORT = "effort"
        const val KEY_COLOR_MODE = "color_mode"
        const val KEY_HAPTICS = "haptics"
        const val KEY_NAME = "display_name"
        const val KEY_ASKED_NAME = "asked_name"
        const val KEY_PC_NOTIFICATIONS = "pc_notifications"
        const val KEY_CHAT_NOTIFICATIONS = "chat_notifications"
        const val KEY_READ_LINKS = "read_links"
        const val KEY_ASKED_CHAT_NOTIFICATIONS = "asked_chat_notifications"
        const val KEY_BRIDGE_URL = "bridge_url"
        const val KEY_BRIDGE_TOKEN = "bridge_token"
        const val KEY_BRIDGE_NAME = "bridge_name"
    }
}

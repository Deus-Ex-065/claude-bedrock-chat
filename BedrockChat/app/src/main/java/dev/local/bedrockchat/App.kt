package dev.local.bedrockchat

import android.app.Application
import dev.local.bedrockchat.data.ChatStore
import dev.local.bedrockchat.data.MemoryStore
import dev.local.bedrockchat.data.Settings

class App : Application() {
    lateinit var settings: Settings
        private set
    lateinit var store: ChatStore
        private set
    lateinit var memory: MemoryStore
        private set

    override fun onCreate() {
        super.onCreate()
        // The text selection menu that apps can add items to (Quote). Set explicitly; the default can vary by version.
        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
        androidx.compose.foundation.ComposeFoundationFlags.isNewContextMenuEnabled = true
        settings = Settings(this)
        store = ChatStore(this)
        memory = MemoryStore(filesDir)
    }
}

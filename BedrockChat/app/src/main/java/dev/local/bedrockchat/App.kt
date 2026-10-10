package dev.local.bedrockchat

import android.app.Application
import dev.local.bedrockchat.data.ChatStore
import dev.local.bedrockchat.data.Settings

class App : Application() {
    lateinit var settings: Settings
        private set
    lateinit var store: ChatStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        store = ChatStore(this)
    }
}

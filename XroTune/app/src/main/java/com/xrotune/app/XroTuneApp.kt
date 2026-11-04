package com.xrotune.app

import android.app.Application
import com.xrotune.app.auth.SessionStore
import com.xrotune.app.data.repo.MusicRepository

/**
 * Manual dependency container. Small enough that a DI framework would be
 * more ceremony than help.
 */
class XroTuneApp : Application() {

    val repository: MusicRepository by lazy { MusicRepository() }
    val sessionStore: SessionStore by lazy { SessionStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: XroTuneApp
            private set
    }
}

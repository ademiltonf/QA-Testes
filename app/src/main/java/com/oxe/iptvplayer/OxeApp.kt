package com.oxe.iptvplayer

import android.app.Application

/** Começa junto com o app: religa o controle pelo celular e os lembretes. */
class OxeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannel(this)
        if (Prefs.remoteEnabled(this)) RemoteServer.start(this)
    }
}

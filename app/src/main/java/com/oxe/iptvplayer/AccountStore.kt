package com.oxe.iptvplayer

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Guarda a lista/conta usada para entrar direto da próxima vez. */
object AccountStore {
    private const val PREFS = "oxe"
    private const val KEY = "account"

    fun load(ctx: Context): Account? {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return null
        return try {
            val o = JSONObject(raw)
            Account(
                kind = Account.Kind.valueOf(o.getString("kind")),
                name = o.optString("name"),
                server = o.optString("server"),
                username = o.optString("username"),
                password = o.optString("password"),
                url = o.optString("url")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun save(ctx: Context, a: Account) {
        val o = JSONObject()
            .put("kind", a.kind.name)
            .put("name", a.name)
            .put("server", a.server)
            .put("username", a.username)
            .put("password", a.password)
            .put("url", a.url)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, o.toString()).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        playlistFile(ctx).delete()
    }

    fun playlistFile(ctx: Context) = File(ctx.filesDir, "playlist.m3u")
}

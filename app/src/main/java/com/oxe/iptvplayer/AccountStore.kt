package com.oxe.iptvplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Guarda todas as listas adicionadas e qual está em uso. */
object AccountStore {
    private const val PREFS = "oxe"
    private const val KEY_LIST = "accounts"
    private const val KEY_ACTIVE = "active"
    private const val KEY_OLD = "account" // versão anterior (uma lista só)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Account> {
        migrate(ctx)
        val raw = prefs(ctx).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::fromJson) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun activeId(ctx: Context): String? = prefs(ctx).getString(KEY_ACTIVE, null)

    fun active(ctx: Context): Account? {
        val all = list(ctx)
        val id = activeId(ctx)
        return all.firstOrNull { it.id == id } ?: all.firstOrNull()
    }

    /** Adiciona (ou atualiza) uma lista e passa a usá-la. */
    fun add(ctx: Context, a: Account) {
        write(ctx, list(ctx).filter { it.id != a.id } + a)
        setActive(ctx, a.id)
    }

    fun setActive(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_ACTIVE, id).apply()
    }

    fun remove(ctx: Context, id: String) {
        write(ctx, list(ctx).filter { it.id != id })
        playlistFile(ctx, id).delete()
        if (activeId(ctx) == id) prefs(ctx).edit().remove(KEY_ACTIVE).apply()
    }

    fun playlistFile(ctx: Context, id: String) = File(ctx.filesDir, "playlist_$id.m3u")

    private fun write(ctx: Context, accounts: List<Account>) {
        val arr = JSONArray()
        accounts.forEach { arr.put(toJson(it)) }
        prefs(ctx).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun toJson(a: Account) = JSONObject()
        .put("id", a.id)
        .put("kind", a.kind.name)
        .put("name", a.name)
        .put("server", a.server)
        .put("username", a.username)
        .put("password", a.password)
        .put("url", a.url)

    private fun fromJson(o: JSONObject): Account? = try {
        Account(
            kind = Account.Kind.valueOf(o.getString("kind")),
            name = o.optString("name"),
            server = o.optString("server"),
            username = o.optString("username"),
            password = o.optString("password"),
            url = o.optString("url"),
            id = o.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() }
        )
    } catch (e: Exception) {
        null
    }

    /** Traz a lista salva pela versão anterior do app. */
    private fun migrate(ctx: Context) {
        val p = prefs(ctx)
        val old = p.getString(KEY_OLD, null) ?: return
        p.edit().remove(KEY_OLD).apply()
        val acc = try { fromJson(JSONObject(old)) } catch (e: Exception) { null } ?: return
        if (acc.kind == Account.Kind.M3U_FILE) {
            File(ctx.filesDir, "playlist.m3u").renameTo(playlistFile(ctx, acc.id))
        }
        write(ctx, listOf(acc))
        setActive(ctx, acc.id)
    }
}

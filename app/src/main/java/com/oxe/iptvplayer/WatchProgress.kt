package com.oxe.iptvplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** "Continuar assistindo": guarda onde a pessoa parou em filmes e episódios. */
object WatchProgress {
    private const val PREFS = "oxe_progress"
    private const val MAX_ITEMS = 30
    private const val MIN_POSITION = 15_000L // menos de 15s não conta

    data class Item(
        val url: String,
        val title: String,
        val image: String?,
        val position: Long,
        val duration: Long,
        val updatedAt: Long
    ) {
        val percent: Int get() = if (duration > 0) ((position * 100) / duration).toInt().coerceIn(0, 100) else 0
    }

    private fun key(ctx: Context) =
        "p_" + (Repository.account?.id ?: AccountStore.activeId(ctx) ?: "default")

    @Synchronized
    fun list(ctx: Context): List<Item> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(ctx), null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    Item(
                        url = o.getString("url"),
                        title = o.optString("title"),
                        image = o.optString("image").nullIfBlank(),
                        position = o.optLong("pos"),
                        duration = o.optLong("dur"),
                        updatedAt = o.optLong("at")
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(ctx: Context, url: String): Item? = list(ctx).firstOrNull { it.url == url }

    @Synchronized
    fun save(ctx: Context, item: PlayItem, position: Long, duration: Long) {
        if (duration <= 0 || position < MIN_POSITION) return
        // Assistiu quase tudo? Sai da lista.
        if (position > duration * 95 / 100) {
            remove(ctx, item.url)
            return
        }
        val list = list(ctx).filter { it.url != item.url }.toMutableList()
        list.add(0, Item(item.url, item.title, item.image, position, duration, System.currentTimeMillis()))
        write(ctx, list.take(MAX_ITEMS))
    }

    @Synchronized
    fun remove(ctx: Context, url: String) {
        write(ctx, list(ctx).filter { it.url != url })
    }

    private fun write(ctx: Context, list: List<Item>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("url", it.url)
                    .put("title", it.title)
                    .put("image", it.image ?: "")
                    .put("pos", it.position)
                    .put("dur", it.duration)
                    .put("at", it.updatedAt)
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(ctx), arr.toString()).apply()
    }

    fun formatTime(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

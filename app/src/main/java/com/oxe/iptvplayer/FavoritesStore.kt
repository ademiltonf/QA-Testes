package com.oxe.iptvplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Favoritos (canais, filmes e séries), separados por lista. */
object FavoritesStore {
    private const val PREFS = "oxe_fav"
    private val cache = HashMap<String, MutableList<Entry>>()

    private fun key(ctx: Context): String =
        "fav_" + (Repository.account?.id ?: AccountStore.activeId(ctx) ?: "default")

    @Synchronized
    private fun all(ctx: Context): MutableList<Entry> {
        val k = key(ctx)
        return cache.getOrPut(k) { load(ctx, k) }
    }

    fun list(ctx: Context, type: ContentType): List<Entry> = all(ctx).filter { it.type == type }

    fun count(ctx: Context): Int = all(ctx).size

    fun isFavorite(ctx: Context, e: Entry): Boolean =
        all(ctx).any { it.type == e.type && it.id == e.id }

    /** Liga/desliga o favorito. Retorna true se foi adicionado. */
    @Synchronized
    fun toggle(ctx: Context, e: Entry): Boolean {
        val list = all(ctx)
        val idx = list.indexOfFirst { it.type == e.type && it.id == e.id }
        val added = if (idx >= 0) {
            list.removeAt(idx)
            false
        } else {
            list.add(0, e)
            true
        }
        save(ctx, key(ctx), list)
        return added
    }

    private fun load(ctx: Context, k: String): MutableList<Entry> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(k, null)
            ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    Entry(
                        id = o.getString("id"),
                        name = o.optString("name"),
                        image = o.optString("image").nullIfBlank(),
                        categoryId = o.optString("cat"),
                        type = ContentType.valueOf(o.getString("type")),
                        url = o.optString("url").nullIfBlank()
                    )
                }
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun save(ctx: Context, k: String, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("image", e.image ?: "")
                    .put("cat", e.categoryId)
                    .put("type", e.type.name)
                    .put("url", e.url ?: "")
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(k, arr.toString()).apply()
    }
}

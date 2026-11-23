package com.oxe.iptvplayer

import android.content.Context
import java.io.IOException

/** Guarda a lista aberta e o que já foi carregado. */
object Repository {

    var account: Account? = null
        private set
    var expiry: String? = null
        private set

    /** O que o player vai tocar (canais da categoria, episódios da temporada...). */
    var playQueue: List<PlayItem> = emptyList()
    var currentSeries: Entry? = null

    private var source: ContentSource? = null
    private val cache = HashMap<ContentType, Catalog>()
    private val epgCache = HashMap<String, Pair<Long, List<Program>>>()

    /** Reabre a última lista salva, se o app foi fechado. */
    fun ensure(ctx: Context): Boolean {
        if (source != null) return true
        val acc = AccountStore.active(ctx) ?: return false
        open(ctx, acc)
        return true
    }

    fun open(ctx: Context, acc: Account) {
        val file = AccountStore.playlistFile(ctx.applicationContext, acc.id)
        account = acc
        expiry = null
        synchronized(cache) { cache.clear() }
        synchronized(epgCache) { epgCache.clear() }
        source = when (acc.kind) {
            Account.Kind.XTREAM -> XtreamSource(acc)
            Account.Kind.M3U_URL -> M3uSource { Http.get(acc.url) }
            Account.Kind.M3U_FILE -> M3uSource { file.readText() }
        }
    }

    fun validate(): String? = requireSource().validate().also { expiry = it }

    fun catalog(type: ContentType): Catalog {
        synchronized(cache) { cache[type] }?.let { return it }
        val c = requireSource().catalog(type)
        synchronized(cache) { cache[type] = c }
        return c
    }

    fun episodes(series: Entry): List<Episode> = requireSource().episodes(series)

    val supportsEpg: Boolean get() = source?.supportsEpg == true

    /** Programação do canal, guardada por 5 minutos. */
    fun epg(entry: Entry): List<Program> {
        val now = System.currentTimeMillis()
        synchronized(epgCache) { epgCache[entry.id] }?.let { (time, list) ->
            if (now - time < 5 * 60_000) return list
        }
        val list = try { source?.epg(entry).orEmpty() } catch (e: Exception) { emptyList() }
        synchronized(epgCache) { epgCache[entry.id] = now to list }
        return list
    }

    /** O programa de agora e o próximo. */
    fun nowAndNext(entry: Entry): Pair<Program?, Program?> {
        val now = System.currentTimeMillis()
        val list = epg(entry)
        val i = list.indexOfFirst { it.start <= now && now < it.end }
        return if (i >= 0) list[i] to list.getOrNull(i + 1)
        else null to list.firstOrNull { it.start > now }
    }

    fun refresh() {
        synchronized(cache) { cache.clear() }
        synchronized(epgCache) { epgCache.clear() }
        source?.reset()
    }

    fun close() {
        source = null
        account = null
        expiry = null
        synchronized(cache) { cache.clear() }
    }

    private fun requireSource(): ContentSource =
        source ?: throw IOException("Nenhuma lista conectada")
}

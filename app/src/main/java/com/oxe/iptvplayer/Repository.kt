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

    /** Reabre a última lista salva, se o app foi fechado. */
    fun ensure(ctx: Context): Boolean {
        if (source != null) return true
        val acc = AccountStore.load(ctx) ?: return false
        open(ctx, acc)
        return true
    }

    fun open(ctx: Context, acc: Account) {
        val file = AccountStore.playlistFile(ctx.applicationContext)
        account = acc
        expiry = null
        synchronized(cache) { cache.clear() }
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

    fun refresh() {
        synchronized(cache) { cache.clear() }
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

package com.oxe.iptvplayer

import android.view.View

enum class ContentType { LIVE, MOVIE, SERIES }

data class Category(val id: String, val name: String)

/** Um canal, filme ou série. Séries não têm url: os episódios são buscados depois. */
data class Entry(
    val id: String,
    val name: String,
    val image: String?,
    val categoryId: String,
    val type: ContentType,
    val url: String? = null
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
    val image: String?
)

data class PlayItem(val title: String, val url: String, val image: String? = null)

data class Account(
    val kind: Kind,
    val name: String,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = ""
) {
    enum class Kind { XTREAM, M3U_URL, M3U_FILE }
}

class Catalog(val categories: List<Category>, val entries: List<Entry>)

/** Efeito de "zoom" quando o item recebe foco (controle remoto da TV). */
fun View.focusZoom(scale: Float = 1.06f) {
    setOnFocusChangeListener { v, hasFocus ->
        val s = if (hasFocus) scale else 1f
        v.animate().scaleX(s).scaleY(s).setDuration(150).start()
    }
}

fun String?.nullIfBlank(): String? =
    if (this.isNullOrBlank() || this == "null") null else this

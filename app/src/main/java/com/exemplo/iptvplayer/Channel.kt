package com.exemplo.iptvplayer

/** User-Agent enviado ao servidor. Alguns provedores exigem um específico. */
const val USER_AGENT = "IPTVPlayer/1.0 (Android)"

data class Channel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String?
)

/** Guarda a lista em reprodução para o player poder trocar de canal. */
object ChannelStore {
    var playlist: List<Channel> = emptyList()
}

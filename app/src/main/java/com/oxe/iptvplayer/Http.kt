package com.oxe.iptvplayer

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Http {
    const val USER_AGENT = "OXEIPTVPLAYER/1.0 (Android)"

    /** Baixa um texto seguindo redirecionamentos (inclusive http -> https). */
    fun get(address: String): String {
        var current = address
        repeat(5) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Redirecionamento inválido")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IOException("O servidor respondeu com erro $code")
                return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Muitos redirecionamentos")
    }
}

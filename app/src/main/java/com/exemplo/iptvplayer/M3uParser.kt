package com.exemplo.iptvplayer

/**
 * Lê listas no formato M3U / M3U8:
 *
 * #EXTM3U
 * #EXTINF:-1 tvg-logo="http://..." group-title="Esportes",Nome do Canal
 * http://servidor/stream.m3u8
 */
object M3uParser {

    private const val NO_GROUP = "Sem categoria"
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var name: String? = null
        var group = NO_GROUP
        var logo: String? = null

        text.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("﻿")
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }

                    // O nome vem depois da primeira vírgula que aparece após os atributos
                    val attrsEnd = line.lastIndexOf('"').let { if (it < 0) 0 else it }
                    val comma = line.indexOf(',', attrsEnd)
                    val title = if (comma >= 0) line.substring(comma + 1).trim() else ""

                    name = title.ifEmpty { attrs["tvg-name"] ?: "Canal" }
                    group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: NO_GROUP
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    group = line.substringAfter(":").trim().ifEmpty { NO_GROUP }
                }

                line.isEmpty() || line.startsWith("#") -> Unit

                else -> {
                    channels += Channel(name ?: line, line, group, logo)
                    name = null
                    group = NO_GROUP
                    logo = null
                }
            }
        }
        return channels
    }
}

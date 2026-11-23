package com.oxe.iptvplayer

data class M3uItem(val name: String, val url: String, val group: String, val logo: String?)

/**
 * Lê listas M3U / M3U8:
 * #EXTINF:-1 tvg-logo="http://..." group-title="Esportes",Nome do Canal
 * http://servidor/stream.m3u8
 */
object M3uParser {

    private const val NO_GROUP = "Sem categoria"
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<M3uItem> {
        val items = mutableListOf<M3uItem>()
        var name: String? = null
        var group = NO_GROUP
        var logo: String? = null

        text.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("﻿")
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val attrsEnd = line.lastIndexOf('"').let { if (it < 0) 0 else it }
                    val comma = line.indexOf(',', attrsEnd)
                    val title = if (comma >= 0) line.substring(comma + 1).trim() else ""

                    name = title.ifEmpty { attrs["tvg-name"] ?: "Sem nome" }
                    group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: NO_GROUP
                    logo = cleanImageUrl(attrs["tvg-logo"])
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    group = line.substringAfter(":").trim().ifEmpty { NO_GROUP }

                line.isEmpty() || line.startsWith("#") -> Unit

                else -> {
                    items += M3uItem(name ?: line, line, group, logo)
                    name = null
                    group = NO_GROUP
                    logo = null
                }
            }
        }
        return items
    }
}

package com.oxe.iptvplayer

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** De onde vêm os canais, filmes e séries. */
interface ContentSource {
    /** Confere se a lista funciona. Retorna a data de validade da conta, se houver. */
    fun validate(): String?
    fun catalog(type: ContentType): Catalog
    fun episodes(series: Entry): List<Episode>
    fun reset() {}

    /** Grade de programação do canal (se a lista tiver). */
    val supportsEpg: Boolean get() = false
    fun epg(entry: Entry): List<Program> = emptyList()
}

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

private fun jsonArray(text: String): JSONArray =
    text.trim().let { if (it.startsWith("[")) JSONArray(it) else JSONArray() }

// ---------------------------------------------------------------------------
// Xtream Codes (servidor + usuário + senha)
// ---------------------------------------------------------------------------
class XtreamSource(account: Account) : ContentSource {

    private val base: String = account.server.trim()
        .removeSuffix("/player_api.php")
        .trimEnd('/')
        .let { if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it" }
    private val user = account.username.trim()
    private val pass = account.password.trim()

    private fun api(action: String? = null, extra: String = ""): String {
        val u = URLEncoder.encode(user, "UTF-8")
        val p = URLEncoder.encode(pass, "UTF-8")
        val a = if (action != null) "&action=$action" else ""
        return Http.get("$base/player_api.php?username=$u&password=$p$a$extra")
    }

    override fun validate(): String? {
        val text = api().trim()
        if (!text.startsWith("{")) throw IOException("Servidor inválido ou fora do ar")
        val info = JSONObject(text).optJSONObject("user_info")
            ?: throw IOException("Resposta inválida do servidor")
        if (info.optInt("auth", 0) != 1) throw IOException("Usuário ou senha incorretos")
        val status = info.optString("status")
        if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
            throw IOException("Conta não está ativa ($status)")
        }
        val exp = info.optString("exp_date").toLongOrNull() ?: return null
        return SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(exp * 1000))
    }

    override fun catalog(type: ContentType): Catalog {
        val (catAction, listAction) = when (type) {
            ContentType.LIVE -> "get_live_categories" to "get_live_streams"
            ContentType.MOVIE -> "get_vod_categories" to "get_vod_streams"
            ContentType.SERIES -> "get_series_categories" to "get_series"
        }
        val categories = jsonArray(api(catAction)).objects().map {
            Category(it.optString("category_id"), it.optString("category_name"))
        }
        val entries = jsonArray(api(listAction)).objects().map { o ->
            val name = o.optString("name")
            val cat = o.optString("category_id")
            when (type) {
                ContentType.LIVE -> {
                    val id = o.optString("stream_id")
                    Entry(id, name, o.optString("stream_icon").nullIfBlank(), cat, type,
                        "$base/live/$user/$pass/$id.ts")
                }
                ContentType.MOVIE -> {
                    val id = o.optString("stream_id")
                    val ext = o.optString("container_extension").nullIfBlank() ?: "mp4"
                    Entry(id, name, o.optString("stream_icon").nullIfBlank(), cat, type,
                        "$base/movie/$user/$pass/$id.$ext")
                }
                ContentType.SERIES ->
                    Entry(o.optString("series_id"), name, o.optString("cover").nullIfBlank(), cat, type)
            }
        }
        return Catalog(categories, entries)
    }

    override val supportsEpg: Boolean get() = true

    override fun epg(entry: Entry): List<Program> {
        if (entry.type != ContentType.LIVE) return emptyList()
        val text = api("get_short_epg", "&stream_id=${entry.id}&limit=4").trim()
        if (!text.startsWith("{")) return emptyList()
        val arr = JSONObject(text).optJSONArray("epg_listings") ?: return emptyList()
        return arr.objects().mapNotNull { o ->
            val start = o.optString("start_timestamp").toLongOrNull() ?: return@mapNotNull null
            val end = o.optString("stop_timestamp").toLongOrNull()
                ?: o.optString("end_timestamp").toLongOrNull()
                ?: return@mapNotNull null
            Program(decode(o.optString("title")), start * 1000, end * 1000)
        }.sortedBy { it.start }
    }

    /** O Xtream manda os títulos em Base64. */
    private fun decode(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim().ifEmpty { s }
    } catch (e: Exception) {
        s
    }

    override fun episodes(series: Entry): List<Episode> {
        val text = api("get_series_info", "&series_id=${series.id}").trim()
        if (!text.startsWith("{")) return emptyList()
        val root = JSONObject(text)
        val out = mutableListOf<Episode>()

        fun addAll(arr: JSONArray, seasonKey: Int?) {
            arr.objects().forEach { e ->
                val id = e.optString("id")
                val ext = e.optString("container_extension").nullIfBlank() ?: "mp4"
                val season = e.optInt("season", seasonKey ?: 1)
                val number = e.optInt("episode_num", out.size + 1)
                val image = e.optJSONObject("info")?.optString("movie_image").nullIfBlank()
                out += Episode(
                    id = id,
                    title = e.optString("title").nullIfBlank() ?: "Episódio $number",
                    season = season,
                    number = number,
                    url = "$base/series/$user/$pass/$id.$ext",
                    image = image
                )
            }
        }

        when (val raw = root.opt("episodes")) {
            is JSONObject -> raw.keys().forEach { k -> raw.optJSONArray(k)?.let { addAll(it, k.toIntOrNull()) } }
            is JSONArray -> for (i in 0 until raw.length()) raw.optJSONArray(i)?.let { addAll(it, null) }
        }
        return out.sortedWith(compareBy({ it.season }, { it.number }))
    }
}

// ---------------------------------------------------------------------------
// Lista M3U (link ou arquivo) — separa sozinho em canais, filmes e séries
// ---------------------------------------------------------------------------
class M3uSource(private val loader: () -> String) : ContentSource {

    private var data: Map<ContentType, Catalog>? = null
    private var seriesMap: Map<String, List<Episode>> = emptyMap()

    override fun validate(): String? {
        if (ensure().values.all { it.entries.isEmpty() }) {
            throw IOException("A lista está vazia ou não é uma lista M3U válida")
        }
        return null
    }

    override fun catalog(type: ContentType): Catalog =
        ensure()[type] ?: Catalog(emptyList(), emptyList())

    override fun episodes(series: Entry): List<Episode> {
        ensure()
        return seriesMap[series.id].orEmpty()
    }

    @Synchronized
    override fun reset() {
        data = null
    }

    @Synchronized
    private fun ensure(): Map<ContentType, Catalog> {
        data?.let { return it }
        val items = M3uParser.parse(loader())

        val live = mutableListOf<Entry>()
        val movies = mutableListOf<Entry>()
        val shows = LinkedHashMap<String, Entry>()
        val episodes = LinkedHashMap<String, MutableList<Episode>>()

        items.forEachIndexed { i, item ->
            when (classify(item)) {
                ContentType.LIVE ->
                    live += Entry("l$i", item.name, item.logo, item.group, ContentType.LIVE, item.url)
                ContentType.MOVIE ->
                    movies += Entry("m$i", item.name, item.logo, item.group, ContentType.MOVIE, item.url)
                ContentType.SERIES -> {
                    val m = SXE.find(item.name)
                    val show = (if (m != null) item.name.substring(0, m.range.first) else item.name)
                        .trim().trimEnd('-', '|', ':', '.').trim()
                        .ifEmpty { item.name }
                    val key = "${item.group}|$show"
                    val list = episodes.getOrPut(key) { mutableListOf() }
                    list += Episode(
                        id = "e$i",
                        title = item.name,
                        season = m?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                        number = m?.groupValues?.get(2)?.toIntOrNull() ?: (list.size + 1),
                        url = item.url,
                        image = item.logo
                    )
                    if (key !in shows) {
                        shows[key] = Entry(key, show, item.logo, item.group, ContentType.SERIES)
                    }
                }
            }
        }

        seriesMap = episodes.mapValues { (_, v) -> v.sortedWith(compareBy({ it.season }, { it.number })) }
        fun cats(list: List<Entry>) = list.map { it.categoryId }.distinct().map { Category(it, it) }
        val series = shows.values.toList()

        val result = mapOf(
            ContentType.LIVE to Catalog(cats(live), live),
            ContentType.MOVIE to Catalog(cats(movies), movies),
            ContentType.SERIES to Catalog(cats(series), series)
        )
        data = result
        return result
    }

    private fun classify(item: M3uItem): ContentType {
        val url = item.url.lowercase()
        val group = item.group
        return when {
            "/series/" in url || SXE.containsMatchIn(item.name) -> ContentType.SERIES
            "/movie/" in url || MOVIE_EXT.containsMatchIn(url) -> ContentType.MOVIE
            MOVIE_WORDS.containsMatchIn(group) && !group.contains("24") -> ContentType.MOVIE
            else -> ContentType.LIVE
        }
    }

    companion object {
        private val SXE = Regex("""(?i)\bS(\d{1,2})\s*[-.]?\s*E(\d{1,3})""")
        private val MOVIE_WORDS = Regex("""(?i)(filmes?|movies?|\bvod\b|cinema|lan[çc]amentos)""")
        private val MOVIE_EXT = Regex("""\.(mp4|mkv|avi|mov|wmv)(\?|$)""")
    }
}

package com.oxe.iptvplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Temporadas e episódios de uma série. */
class SeriesActivity : AppCompatActivity() {

    companion object {
        fun start(ctx: Context, series: Entry) {
            Repository.currentSeries = series
            ctx.startActivity(Intent(ctx, SeriesActivity::class.java))
        }
    }

    private lateinit var series: Entry
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var info: TextView

    private var allEpisodes: List<Episode> = emptyList()
    private var current: List<Episode> = emptyList()

    private val seasonAdapter = CategoryAdapter(vertical = false) { cat ->
        cat.id.toIntOrNull()?.let { showSeason(it) }
    }
    private val episodeAdapter = EpisodeAdapter { pos -> play(pos) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val s = Repository.currentSeries
        if (!Repository.ensure(this) || s == null) {
            finish()
            return
        }
        series = s
        setContentView(R.layout.activity_series)

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<TextView>(R.id.series_title).text = series.name
        findViewById<ImageView>(R.id.cover).apply {
            clipToOutline = true
            load(series.image) {
                placeholder(R.drawable.ph_poster)
                error(R.drawable.ph_poster)
                fallback(R.drawable.ph_poster)
                crossfade(true)
            }
        }
        info = findViewById(R.id.series_info)
        progress = findViewById(R.id.progress)
        emptyText = findViewById(R.id.empty_text)

        findViewById<RecyclerView>(R.id.season_list).apply {
            layoutManager = LinearLayoutManager(this@SeriesActivity, RecyclerView.HORIZONTAL, false)
            adapter = seasonAdapter
        }
        findViewById<RecyclerView>(R.id.episode_list).apply {
            layoutManager = LinearLayoutManager(this@SeriesActivity)
            adapter = episodeAdapter
        }

        load()
    }

    private fun load() {
        progress.isVisible = true
        lifecycleScope.launch {
            try {
                allEpisodes = withContext(Dispatchers.IO) { Repository.episodes(series) }
                val seasons = allEpisodes.map { it.season }.distinct().sorted()
                info.text = getString(R.string.seasons_count, seasons.size, allEpisodes.size)
                seasonAdapter.submit(seasons.map { Category(it.toString(), getString(R.string.season_n, it)) })
                if (seasons.isEmpty()) {
                    emptyText.setText(R.string.nothing_found)
                    emptyText.isVisible = true
                } else {
                    showSeason(seasons.first())
                }
            } catch (e: Exception) {
                emptyText.text = getString(R.string.load_error, e.message ?: e.javaClass.simpleName)
                emptyText.isVisible = true
            } finally {
                progress.isVisible = false
            }
        }
    }

    private fun showSeason(season: Int) {
        current = allEpisodes.filter { it.season == season }
        episodeAdapter.submit(current)
    }

    private fun play(position: Int) {
        Repository.playQueue = current.map {
            PlayItem("${series.name} · T${it.season} E${it.number}", it.url, it.image)
        }
        startActivity(PlayerActivity.intent(this, position, live = false))
    }
}

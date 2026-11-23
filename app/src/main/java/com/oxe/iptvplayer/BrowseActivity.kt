package com.oxe.iptvplayer

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tela de Canais, Filmes ou Séries: categorias + grade. */
class BrowseActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TYPE = "type"

        fun start(ctx: Context, type: ContentType) {
            ctx.startActivity(Intent(ctx, BrowseActivity::class.java).putExtra(EXTRA_TYPE, type.name))
        }
    }

    private lateinit var type: ContentType
    private lateinit var search: EditText
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var entryAdapter: EntryAdapter

    private var catalog: Catalog? = null
    private var selectedCategory: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Repository.ensure(this)) {
            finish()
            return
        }
        type = runCatching { ContentType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "") }
            .getOrDefault(ContentType.LIVE)
        setContentView(R.layout.activity_browse)

        findViewById<TextView>(R.id.screen_title).setText(
            when (type) {
                ContentType.LIVE -> R.string.live
                ContentType.MOVIE -> R.string.movies
                ContentType.SERIES -> R.string.series
            }
        )
        findViewById<android.view.View>(R.id.back_button).setOnClickListener { finish() }

        search = findViewById(R.id.search_input)
        progress = findViewById(R.id.progress)
        emptyText = findViewById(R.id.empty_text)

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        categoryAdapter = CategoryAdapter(vertical = landscape) { cat ->
            selectedCategory = cat.id
            applyFilter()
        }
        findViewById<RecyclerView>(R.id.category_list).apply {
            layoutManager = LinearLayoutManager(
                this@BrowseActivity,
                if (landscape) RecyclerView.VERTICAL else RecyclerView.HORIZONTAL,
                false
            )
            adapter = categoryAdapter
        }

        val gridWidthDp = resources.configuration.screenWidthDp - (if (landscape) 260 else 0)
        val itemDp = if (type == ContentType.LIVE) 150 else 125
        val spans = (gridWidthDp / itemDp).coerceAtLeast(2)

        entryAdapter = EntryAdapter(type) { pos -> open(pos) }
        findViewById<RecyclerView>(R.id.grid).apply {
            layoutManager = GridLayoutManager(this@BrowseActivity, spans)
            adapter = entryAdapter
        }

        search.doAfterTextChanged { applyFilter() }
        load()
    }

    private fun load() {
        progress.isVisible = true
        emptyText.isVisible = false
        lifecycleScope.launch {
            try {
                val c = withContext(Dispatchers.IO) { Repository.catalog(type) }
                catalog = c
                val used = c.entries.map { it.categoryId }.toSet()
                val cats = listOf(Category("", getString(R.string.all))) +
                    c.categories.filter { it.id in used }
                categoryAdapter.submit(cats)
                selectedCategory = ""
                applyFilter()
            } catch (e: Exception) {
                emptyText.text = getString(R.string.load_error, e.message ?: e.javaClass.simpleName)
                emptyText.isVisible = true
            } finally {
                progress.isVisible = false
            }
        }
    }

    private fun applyFilter() {
        val c = catalog ?: return
        val q = search.text.toString().trim()
        val list = c.entries.filter {
            (selectedCategory.isEmpty() || it.categoryId == selectedCategory) &&
                (q.isEmpty() || it.name.contains(q, ignoreCase = true))
        }
        entryAdapter.submit(list)
        emptyText.setText(R.string.nothing_found)
        emptyText.isVisible = list.isEmpty()
    }

    private fun open(position: Int) {
        val items = entryAdapter.items
        val e = items.getOrNull(position) ?: return
        when (type) {
            ContentType.LIVE -> {
                Repository.playQueue = items.map { PlayItem(it.name, it.url.orEmpty(), it.image) }
                startActivity(PlayerActivity.intent(this, position, live = true))
            }
            ContentType.MOVIE -> {
                Repository.playQueue = listOf(PlayItem(e.name, e.url.orEmpty(), e.image))
                startActivity(PlayerActivity.intent(this, 0, live = false))
            }
            ContentType.SERIES -> SeriesActivity.start(this, e)
        }
    }
}

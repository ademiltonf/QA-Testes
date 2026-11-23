package com.oxe.iptvplayer

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Tela de Canais, Filmes ou Séries (categorias + grade).
 * Também é usada para "Meus favoritos".
 */
class BrowseActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_FAV = "favorites"
        private const val FAV_ID = "__fav__"

        fun start(ctx: Context, type: ContentType) {
            ctx.startActivity(Intent(ctx, BrowseActivity::class.java).putExtra(EXTRA_TYPE, type.name))
        }

        fun startFavorites(ctx: Context) {
            ctx.startActivity(Intent(ctx, BrowseActivity::class.java).putExtra(EXTRA_FAV, true))
        }
    }

    private var favoritesMode = false
    private var landscape = false
    private lateinit var type: ContentType
    private lateinit var search: EditText
    private lateinit var progress: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var grid: RecyclerView
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var entryAdapter: EntryAdapter

    private var catalog: Catalog? = null
    private val epgLimit = Semaphore(4)
    private var selectedCategory: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Repository.ensure(this)) {
            finish()
            return
        }
        favoritesMode = intent.getBooleanExtra(EXTRA_FAV, false)
        type = runCatching { ContentType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "") }
            .getOrDefault(ContentType.LIVE)
        setContentView(R.layout.activity_browse)

        findViewById<TextView>(R.id.screen_title).setText(
            when {
                favoritesMode -> R.string.my_favorites
                type == ContentType.LIVE -> R.string.live
                type == ContentType.MOVIE -> R.string.movies
                else -> R.string.series
            }
        )
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }

        search = findViewById(R.id.search_input)
        progress = findViewById(R.id.progress)
        emptyText = findViewById(R.id.empty_text)
        grid = findViewById(R.id.grid)

        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        categoryAdapter = CategoryAdapter(vertical = landscape) { cat -> onCategory(cat) }
        findViewById<RecyclerView>(R.id.category_list).apply {
            layoutManager = LinearLayoutManager(
                this@BrowseActivity,
                if (landscape) RecyclerView.VERTICAL else RecyclerView.HORIZONTAL,
                false
            )
            adapter = categoryAdapter
        }

        setupGrid()
        search.doAfterTextChanged { applyFilter() }

        if (favoritesMode) {
            // Começa pela primeira seção que tiver favoritos
            type = listOf(ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES)
                .firstOrNull { FavoritesStore.list(this, it).isNotEmpty() } ?: ContentType.LIVE
            val sections = listOf(
                Category(ContentType.LIVE.name, getString(R.string.live)),
                Category(ContentType.MOVIE.name, getString(R.string.movies)),
                Category(ContentType.SERIES.name, getString(R.string.series))
            )
            categoryAdapter.submit(sections)
            categoryAdapter.select(sections.indexOfFirst { it.id == type.name })
            setupGrid()
            applyFilter()
        } else {
            load()
        }
    }

    override fun onResume() {
        super.onResume()
        // Atualiza as estrelinhas (pode ter favoritado dentro do player)
        if (::entryAdapter.isInitialized) applyFilter()
    }

    private fun setupGrid() {
        val gridWidthDp = resources.configuration.screenWidthDp - (if (landscape) 260 else 0)
        val itemDp = if (type == ContentType.LIVE) 150 else 125
        val spans = (gridWidthDp / itemDp).coerceAtLeast(2)
        entryAdapter = EntryAdapter(
            type,
            isFavorite = { FavoritesStore.isFavorite(this, it) },
            onClick = { open(it) },
            onLongClick = { toggleFavorite(it) },
            epgLoader = if (type == ContentType.LIVE && Repository.supportsEpg) ::loadNowPlaying else null
        )
        grid.layoutManager = GridLayoutManager(this, spans)
        grid.adapter = entryAdapter
    }

    private fun load() {
        progress.isVisible = true
        emptyText.isVisible = false
        lifecycleScope.launch {
            try {
                val c = withContext(Dispatchers.IO) { Repository.catalog(type) }
                catalog = c
                val used = c.entries.map { it.categoryId }.toSet()
                val cats = listOf(
                    Category("", getString(R.string.all)),
                    Category(FAV_ID, getString(R.string.favorites_chip))
                ) + c.categories.filter { it.id in used }
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

    private fun onCategory(cat: Category) {
        if (favoritesMode) {
            type = runCatching { ContentType.valueOf(cat.id) }.getOrDefault(ContentType.LIVE)
            setupGrid()
        } else {
            selectedCategory = cat.id
        }
        applyFilter()
    }

    private fun showingFavorites() = favoritesMode || selectedCategory == FAV_ID

    private fun applyFilter() {
        val source: List<Entry> = if (showingFavorites()) {
            FavoritesStore.list(this, type)
        } else {
            val c = catalog ?: return
            if (selectedCategory.isEmpty()) c.entries
            else c.entries.filter { it.categoryId == selectedCategory }
        }
        val q = search.text.toString().trim()
        val list = if (q.isEmpty()) source else source.filter { it.name.contains(q, ignoreCase = true) }
        entryAdapter.submit(list)

        emptyText.setText(
            if (showingFavorites() && q.isEmpty()) R.string.no_favorites else R.string.nothing_found
        )
        emptyText.isVisible = list.isEmpty()
    }

    private fun loadNowPlaying(entry: Entry, done: (String?) -> Unit) =
        lifecycleScope.launch {
            val now = epgLimit.withPermit {
                withContext(Dispatchers.IO) { Repository.nowAndNext(entry).first }
            }
            done(now?.let { getString(R.string.epg_now_short, it.title) })
        }

    private fun toggleFavorite(position: Int) {
        val e = entryAdapter.items.getOrNull(position) ?: return
        val added = FavoritesStore.toggle(this, e)
        Toast.makeText(
            this,
            if (added) R.string.fav_added else R.string.fav_removed,
            Toast.LENGTH_SHORT
        ).show()
        if (showingFavorites()) applyFilter() else entryAdapter.notifyItemChanged(position)
    }

    private fun open(position: Int) {
        val items = entryAdapter.items
        val e = items.getOrNull(position) ?: return
        when (type) {
            ContentType.LIVE -> {
                Repository.playQueue = items.map { PlayItem(it.name, it.url.orEmpty(), it.image, it) }
                startActivity(PlayerActivity.intent(this, position, live = true))
            }
            ContentType.MOVIE -> {
                Repository.playQueue = listOf(PlayItem(e.name, e.url.orEmpty(), e.image, e))
                startActivity(PlayerActivity.intent(this, 0, live = false))
            }
            ContentType.SERIES -> SeriesActivity.start(this, e)
        }
    }
}

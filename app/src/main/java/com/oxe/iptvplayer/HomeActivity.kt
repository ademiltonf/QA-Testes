package com.oxe.iptvplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private class Tile(
        val type: ContentType,
        val viewId: Int,
        @StringRes val label: Int,
        @DrawableRes val icon: Int,
        @DrawableRes val wallpaper: Int
    )

    private val tiles = listOf(
        Tile(ContentType.LIVE, R.id.tile_live, R.string.live, R.drawable.ic_live_tv, R.drawable.art_live),
        Tile(ContentType.MOVIE, R.id.tile_movies, R.string.movies, R.drawable.ic_movie, R.drawable.art_movies),
        Tile(ContentType.SERIES, R.id.tile_series, R.string.series, R.drawable.ic_series, R.drawable.art_series)
    )

    private val posterIds = listOf(
        R.id.poster1, R.id.poster2, R.id.poster3, R.id.poster4, R.id.poster5, R.id.poster6
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Repository.ensure(this)) {
            WelcomeActivity.restart(this)
            finish()
            return
        }
        setContentView(R.layout.activity_home)

        findViewById<TextView>(R.id.account_name).text = Repository.account?.name.orEmpty()

        tiles.forEach { t ->
            val tile = findViewById<View>(t.viewId)
            tile.clipToOutline = true
            tile.focusZoom(1.04f)
            tile.findViewById<ImageView>(R.id.tile_image).setImageResource(t.wallpaper)
            tile.findViewById<ImageView>(R.id.tile_icon).setImageResource(t.icon)
            tile.findViewById<TextView>(R.id.tile_label).setText(t.label)
            tile.setOnClickListener { BrowseActivity.start(this, t.type) }
        }

        findViewById<View>(R.id.refresh_button).setOnClickListener {
            Repository.refresh()
            loadTiles()
        }
        findViewById<View>(R.id.fav_bar).apply {
            focusZoom(1.02f)
            setOnClickListener { BrowseActivity.startFavorites(this@HomeActivity) }
        }
        findViewById<View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        loadTiles()
        loadExpiry()
        findViewById<View>(R.id.tile_live).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        // Teste acabou (ou aparelho bloqueado)? Volta para a tela de ativação.
        if (!License.allowedOffline(this)) {
            LicenseActivity.lock(this)
            finish()
            return
        }
        // Confere a licença no servidor em segundo plano
        lifecycleScope.launch {
            val s = withContext(Dispatchers.IO) { runCatching { License.check(this@HomeActivity) }.getOrNull() }
            if (s != null && !s.allowed) {
                LicenseActivity.lock(this@HomeActivity)
                finish()
            }
        }
        findViewById<TextView>(R.id.fav_count)?.text =
            getString(R.string.items_count, FavoritesStore.count(this))
    }

    private fun loadTiles() {
        tiles.forEach { t ->
            val tile = findViewById<View>(t.viewId)
            val count = tile.findViewById<TextView>(R.id.tile_count)
            count.setText(R.string.loading_short)
            lifecycleScope.launch {
                try {
                    val catalog = withContext(Dispatchers.IO) { Repository.catalog(t.type) }
                    count.text = getString(R.string.items_count, catalog.entries.size)
                    if (t.type != ContentType.LIVE) showPosters(tile, catalog)
                } catch (e: Exception) {
                    count.setText(R.string.load_failed)
                }
            }
        }
    }

    /** Usa os pôsteres da própria lista como papel de parede do bloco. */
    private fun showPosters(tile: View, catalog: Catalog) {
        val images = catalog.entries.asSequence()
            .take(400)
            .mapNotNull { it.image }
            .toList()
            .shuffled()
            .take(posterIds.size)
        val container = tile.findViewById<View>(R.id.tile_posters)
        if (images.size < posterIds.size) {
            container.isVisible = false
            return
        }
        posterIds.forEachIndexed { i, id ->
            tile.findViewById<ImageView>(id).load(images[i]) {
                placeholder(R.drawable.ph_poster)
                error(R.drawable.ph_poster)
                crossfade(true)
            }
        }
        container.alpha = 0f
        container.isVisible = true
        container.animate().alpha(1f).setDuration(600).start()
    }

    private fun loadExpiry() {
        if (Repository.account?.kind != Account.Kind.XTREAM) return
        val view = findViewById<TextView>(R.id.expiry_text)
        lifecycleScope.launch {
            val exp = Repository.expiry ?: withContext(Dispatchers.IO) {
                runCatching { Repository.validate() }.getOrNull()
            }
            if (exp != null) {
                view.text = getString(R.string.expiry, exp)
                view.isVisible = true
            }
        }
    }
}

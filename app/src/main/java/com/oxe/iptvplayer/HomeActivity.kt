package com.oxe.iptvplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private val tiles = listOf(
        Triple(ContentType.LIVE, R.id.tile_live, R.id.count_live),
        Triple(ContentType.MOVIE, R.id.tile_movies, R.id.count_movies),
        Triple(ContentType.SERIES, R.id.tile_series, R.id.count_series)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Repository.ensure(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_home)

        findViewById<TextView>(R.id.account_name).text = Repository.account?.name.orEmpty()

        tiles.forEach { (type, tileId, _) ->
            val tile = findViewById<View>(tileId)
            tile.focusZoom(1.04f)
            tile.setOnClickListener { BrowseActivity.start(this, type) }
        }

        findViewById<View>(R.id.refresh_button).setOnClickListener {
            Repository.refresh()
            loadCounts()
        }
        findViewById<View>(R.id.logout_button).setOnClickListener { confirmLogout() }

        loadCounts()
        loadExpiry()
        findViewById<View>(R.id.tile_live).requestFocus()
    }

    private fun loadCounts() {
        tiles.forEach { (type, _, countId) ->
            val countView = findViewById<TextView>(countId)
            countView.setText(R.string.loading_short)
            lifecycleScope.launch {
                countView.text = try {
                    val catalog = withContext(Dispatchers.IO) { Repository.catalog(type) }
                    getString(R.string.items_count, catalog.entries.size)
                } catch (e: Exception) {
                    getString(R.string.load_failed)
                }
            }
        }
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

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(R.string.logout_confirm)
            .setPositiveButton(R.string.logout) { _, _ ->
                Repository.close()
                AccountStore.clear(this)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

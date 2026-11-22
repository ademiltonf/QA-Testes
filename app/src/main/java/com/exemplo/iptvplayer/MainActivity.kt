package com.exemplo.iptvplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var searchInput: EditText
    private lateinit var groupSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var list: RecyclerView

    private val adapter = ChannelAdapter { position -> openPlayer(position) }
    private var allChannels: List<Channel> = emptyList()
    private var selectedGroup: String? = null

    private val prefs by lazy { getSharedPreferences("iptv", MODE_PRIVATE) }

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) loadFromUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.url_input)
        searchInput = findViewById(R.id.search_input)
        groupSpinner = findViewById(R.id.group_spinner)
        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        list = findViewById(R.id.channel_list)

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<Button>(R.id.load_button).setOnClickListener { loadFromUrl() }
        findViewById<Button>(R.id.file_button).setOnClickListener {
            try {
                pickFile.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                // Muitas TV Box não têm seletor de arquivos
                toast(getString(R.string.no_file_picker))
            }
        }

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadFromUrl(); true
            } else false
        }

        searchInput.doAfterTextChanged { applyFilter() }

        groupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedGroup = if (pos == 0) null else parent?.getItemAtPosition(pos) as? String
                applyFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // Abre automaticamente a última lista usada
        val saved = prefs.getString(KEY_LAST_URL, null)
        if (!saved.isNullOrBlank()) {
            urlInput.setText(saved)
            loadFromUrl()
        }
    }

    private fun loadFromUrl() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            toast(getString(R.string.enter_url)); return
        }
        prefs.edit().putString(KEY_LAST_URL, url).apply()
        load { downloadText(url) }
    }

    private fun loadFromUri(uri: Uri) = load {
        contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error(getString(R.string.file_error))
    }

    private fun load(source: suspend () -> String) {
        progress.visibility = View.VISIBLE
        status.text = getString(R.string.loading)
        lifecycleScope.launch {
            try {
                val channels = withContext(Dispatchers.IO) { M3uParser.parse(source()) }
                showChannels(channels)
            } catch (e: Exception) {
                status.text = getString(R.string.load_error, e.message ?: e.javaClass.simpleName)
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    /** Baixa a lista seguindo redirecionamentos (inclusive http -> https). */
    private fun downloadText(address: String): String {
        var current = address
        repeat(5) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: error("Redirecionamento inválido")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code !in 200..299) error("HTTP $code")
                return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } finally {
                conn.disconnect()
            }
        }
        error("Muitos redirecionamentos")
    }

    private fun showChannels(channels: List<Channel>) {
        allChannels = channels
        val groups = channels.map { it.group }.distinct()
        val items = listOf(getString(R.string.all_groups)) + groups
        groupSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        selectedGroup = null
        applyFilter()

        // Na TV Box, já deixa o foco no primeiro canal
        if (channels.isNotEmpty()) list.post { list.getChildAt(0)?.requestFocus() }
    }

    private fun applyFilter() {
        val query = searchInput.text.toString().trim()
        val filtered = allChannels.filter {
            (selectedGroup == null || it.group == selectedGroup) &&
                (query.isEmpty() || it.name.contains(query, ignoreCase = true))
        }
        adapter.submit(filtered)
        status.text = getString(R.string.channel_count, filtered.size, allChannels.size)
    }

    private fun openPlayer(position: Int) {
        ChannelStore.playlist = adapter.items
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_INDEX, position)
        )
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val KEY_LAST_URL = "last_url"
    }
}

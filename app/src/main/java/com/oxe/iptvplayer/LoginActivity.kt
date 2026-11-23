package com.oxe.iptvplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private enum class Mode { XTREAM, M3U_URL, SINGLE }

    private var mode: Mode? = null

    private lateinit var options: View
    private lateinit var form: View
    private lateinit var subtitle: View
    private lateinit var formTitle: TextView
    private lateinit var nameInput: EditText
    private lateinit var serverInput: EditText
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var enterButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importFile(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)
        options = findViewById(R.id.options)
        form = findViewById(R.id.form)
        subtitle = findViewById(R.id.subtitle)
        formTitle = findViewById(R.id.form_title)
        nameInput = findViewById(R.id.name_input)
        serverInput = findViewById(R.id.server_input)
        userInput = findViewById(R.id.user_input)
        passInput = findViewById(R.id.pass_input)
        urlInput = findViewById(R.id.url_input)
        enterButton = findViewById(R.id.enter_button)
        progress = findViewById(R.id.progress)
        errorText = findViewById(R.id.error_text)

        val optXtream = findViewById<View>(R.id.opt_xtream)
        val optM3u = findViewById<View>(R.id.opt_m3u)
        val optFile = findViewById<View>(R.id.opt_file)
        val optSingle = findViewById<View>(R.id.opt_single)
        listOf(optXtream, optM3u, optFile, optSingle).forEach { it.focusZoom() }

        optXtream.setOnClickListener { showForm(Mode.XTREAM) }
        optM3u.setOnClickListener { showForm(Mode.M3U_URL) }
        optSingle.setOnClickListener { showForm(Mode.SINGLE) }
        optFile.setOnClickListener {
            try {
                pickFile.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                showError(getString(R.string.no_file_picker))
            }
        }

        enterButton.setOnClickListener { submit() }
        findViewById<Button>(R.id.back_button).setOnClickListener { showOptions() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (form.isVisible) showOptions() else finish()
            }
        })

        optXtream.requestFocus()
    }

    private fun showForm(m: Mode) {
        mode = m
        options.isVisible = false
        subtitle.isVisible = false
        form.isVisible = true
        errorText.isVisible = false

        formTitle.setText(
            when (m) {
                Mode.XTREAM -> R.string.opt_xtream
                Mode.M3U_URL -> R.string.opt_m3u
                Mode.SINGLE -> R.string.opt_single
            }
        )
        val xtream = m == Mode.XTREAM
        serverInput.isVisible = xtream
        userInput.isVisible = xtream
        passInput.isVisible = xtream
        urlInput.isVisible = !xtream
        nameInput.isVisible = m != Mode.SINGLE
        urlInput.setHint(if (m == Mode.SINGLE) R.string.hint_stream_url else R.string.hint_m3u_url)
        enterButton.setText(if (m == Mode.SINGLE) R.string.play else R.string.enter)

        (if (m == Mode.SINGLE) urlInput else nameInput).requestFocus()
    }

    private fun showOptions() {
        mode = null
        form.isVisible = false
        options.isVisible = true
        subtitle.isVisible = true
        errorText.isVisible = false
        findViewById<View>(R.id.opt_xtream).requestFocus()
    }

    private fun submit() {
        val name = nameInput.text.toString().trim()
        when (mode) {
            Mode.SINGLE -> {
                val url = urlInput.text.toString().trim()
                if (url.isEmpty()) return showError(getString(R.string.err_fill))
                Repository.playQueue = listOf(PlayItem(getString(R.string.opt_single), url))
                startActivity(PlayerActivity.intent(this, 0, live = false))
            }

            Mode.XTREAM -> {
                val server = serverInput.text.toString().trim()
                val user = userInput.text.toString().trim()
                val pass = passInput.text.toString().trim()
                if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                    return showError(getString(R.string.err_fill))
                }
                connect(
                    Account(
                        Account.Kind.XTREAM, name.ifEmpty { getString(R.string.opt_xtream) },
                        server = server, username = user, password = pass
                    )
                )
            }

            Mode.M3U_URL -> {
                val url = urlInput.text.toString().trim()
                if (url.isEmpty()) return showError(getString(R.string.err_fill))
                connect(
                    Account(
                        Account.Kind.M3U_URL, name.ifEmpty { getString(R.string.default_list_name) },
                        url = url
                    )
                )
            }

            null -> Unit
        }
    }

    private fun connect(acc: Account) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Repository.open(this@LoginActivity, acc)
                    Repository.validate()
                }
                AccountStore.add(this@LoginActivity, acc)
                goHome()
            } catch (e: Exception) {
                Repository.close()
                if (acc.kind == Account.Kind.M3U_FILE) {
                    AccountStore.playlistFile(this@LoginActivity, acc.id).delete()
                }
                setLoading(false)
                showError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun importFile(uri: Uri) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val acc = Account(Account.Kind.M3U_FILE, getString(R.string.local_file))
                withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IOException(getString(R.string.file_error))
                    input.use { inp ->
                        AccountStore.playlistFile(this@LoginActivity, acc.id).outputStream().use { out ->
                            inp.copyTo(out)
                        }
                    }
                }
                connect(acc)
            } catch (e: Exception) {
                setLoading(false)
                showError(e.message ?: getString(R.string.file_error))
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.isVisible = loading
        enterButton.isEnabled = !loading
        if (loading) errorText.isVisible = false
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.isVisible = true
    }

    private fun goHome() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
}

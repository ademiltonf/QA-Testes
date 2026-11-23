package com.oxe.iptvplayer

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/** Teste de velocidade da internet dentro do app. */
class SpeedTestActivity : AppCompatActivity() {

    companion object {
        private const val BASE = "https://speed.cloudflare.com"
        private const val DOWNLOAD_MS = 10_000L
        private const val UPLOAD_MS = 8_000L
    }

    private lateinit var phase: TextView
    private lateinit var speed: TextView
    private lateinit var bar: ProgressBar
    private lateinit var downText: TextView
    private lateinit var upText: TextView
    private lateinit var pingText: TextView
    private lateinit var advice: TextView
    private lateinit var startButton: Button
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speedtest)
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        phase = findViewById(R.id.speed_phase)
        speed = findViewById(R.id.speed_value)
        bar = findViewById(R.id.speed_bar)
        downText = findViewById(R.id.result_down)
        upText = findViewById(R.id.result_up)
        pingText = findViewById(R.id.result_ping)
        advice = findViewById(R.id.speed_advice)
        startButton = findViewById(R.id.start_button)
        startButton.setOnClickListener { start() }
        startButton.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }

    private fun start() {
        job?.cancel()
        startButton.isEnabled = false
        advice.isVisible = false
        downText.text = "—"; upText.text = "—"; pingText.text = "—"
        job = lifecycleScope.launch {
            try {
                phase.setText(R.string.speed_ping)
                speed.text = "…"
                val ping = withContext(Dispatchers.IO) { measurePing() }
                pingText.text = getString(R.string.speed_ms, ping)

                phase.setText(R.string.speed_download)
                val down = measure(DOWNLOAD_MS, upload = false)
                downText.text = getString(R.string.speed_mbps, down)

                phase.setText(R.string.speed_upload)
                val up = measure(UPLOAD_MS, upload = true)
                upText.text = getString(R.string.speed_mbps, up)

                phase.setText(R.string.speed_done)
                speed.text = "%.0f".format(down)
                bar.progress = 0
                advice.text = adviceFor(down)
                advice.isVisible = true
            } catch (e: Exception) {
                phase.text = getString(R.string.speed_error, e.message ?: "")
                speed.text = "—"
            } finally {
                startButton.isEnabled = true
                startButton.setText(R.string.speed_again)
            }
        }
    }

    /** Menor tempo de resposta em 5 tentativas (ms). */
    private fun measurePing(): Long {
        var best = Long.MAX_VALUE
        repeat(6) { i ->
            val t0 = SystemClock.elapsedRealtime()
            val c = open("$BASE/__down?bytes=0")
            try {
                c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
            val dt = SystemClock.elapsedRealtime() - t0
            if (i > 0) best = minOf(best, dt) // a 1ª conexão é mais lenta
        }
        return best
    }

    /** Mede download ou upload com 4 conexões ao mesmo tempo. Retorna Mbps. */
    private suspend fun measure(durationMs: Long, upload: Boolean): Double {
        val bytes = AtomicLong(0)
        val start = SystemClock.elapsedRealtime()
        val deadline = start + durationMs
        coroutineScope {
            val ui = launch {
                while (isActive) {
                    delay(250)
                    val elapsed = (SystemClock.elapsedRealtime() - start).coerceAtLeast(1)
                    val mbps = bytes.get() * 8.0 / elapsed / 1000.0
                    speed.text = "%.0f".format(mbps)
                    bar.progress = ((elapsed * 100) / durationMs).toInt().coerceIn(0, 100)
                }
            }
            val workers = (1..4).map {
                launch(Dispatchers.IO) {
                    while (isActive && SystemClock.elapsedRealtime() < deadline) {
                        try {
                            if (upload) uploadChunk(bytes, deadline) else downloadChunk(bytes, deadline)
                        } catch (e: Exception) {
                            if (SystemClock.elapsedRealtime() < deadline) delay(200)
                        }
                    }
                }
            }
            workers.forEach { it.join() }
            ui.cancel()
        }
        val elapsed = (SystemClock.elapsedRealtime() - start).coerceAtLeast(1)
        return bytes.get() * 8.0 / elapsed / 1000.0
    }

    private fun downloadChunk(counter: AtomicLong, deadline: Long) {
        val c = open("$BASE/__down?bytes=50000000")
        try {
            c.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (SystemClock.elapsedRealtime() < deadline) {
                    val n = input.read(buf)
                    if (n < 0) break
                    counter.addAndGet(n.toLong())
                }
            }
        } finally {
            c.disconnect()
        }
    }

    private fun uploadChunk(counter: AtomicLong, deadline: Long) {
        val size = 4 * 1024 * 1024
        val c = open("$BASE/__up")
        c.requestMethod = "POST"
        c.doOutput = true
        c.setFixedLengthStreamingMode(size)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        try {
            val buf = ByteArray(64 * 1024)
            c.outputStream.use { out ->
                var sent = 0
                while (sent < size) {
                    val n = minOf(buf.size, size - sent)
                    out.write(buf, 0, n)
                    sent += n
                    counter.addAndGet(n.toLong())
                    if (SystemClock.elapsedRealtime() >= deadline) break
                }
            }
            if (SystemClock.elapsedRealtime() < deadline) c.responseCode
        } finally {
            c.disconnect()
        }
    }

    private fun open(address: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            useCaches = false
            setRequestProperty("User-Agent", Http.USER_AGENT)
        }

    private fun adviceFor(mbps: Double): String = getString(
        when {
            mbps < 5 -> R.string.speed_advice_low
            mbps < 12 -> R.string.speed_advice_720
            mbps < 30 -> R.string.speed_advice_1080
            else -> R.string.speed_advice_great
        }
    )
}

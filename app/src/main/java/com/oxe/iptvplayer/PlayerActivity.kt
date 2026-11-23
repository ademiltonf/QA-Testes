package com.oxe.iptvplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_LIVE = "live"
        private const val STATE_POSITION = "position"

        fun intent(ctx: Context, index: Int, live: Boolean): Intent =
            Intent(ctx, PlayerActivity::class.java)
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_LIVE, live)
    }

    private lateinit var playerView: PlayerView
    private lateinit var channelInfo: TextView
    private lateinit var errorText: TextView
    private lateinit var prevButton: View
    private lateinit var nextButton: View

    private var player: ExoPlayer? = null
    private var index = 0
    private var live = false
    private var resumePosition = 0L
    private val queue get() = Repository.playQueue

    private val handler = Handler(Looper.getMainLooper())
    private val hideInfo = Runnable { channelInfo.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (queue.isEmpty()) {
            finish()
            return
        }
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        playerView = findViewById(R.id.player_view)
        channelInfo = findViewById(R.id.channel_info)
        errorText = findViewById(R.id.error_text)
        prevButton = findViewById(R.id.prev_button)
        nextButton = findViewById(R.id.next_button)

        live = intent.getBooleanExtra(EXTRA_LIVE, false)
        index = (savedInstanceState?.getInt(EXTRA_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0))
            .coerceIn(queue.indices)
        resumePosition = savedInstanceState?.getLong(STATE_POSITION) ?: 0L

        prevButton.setOnClickListener { change(-1) }
        nextButton.setOnClickListener { change(+1) }
        val canSwitch = queue.size > 1
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                val v = if (canSwitch) visibility else View.GONE
                prevButton.visibility = v
                nextButton.visibility = v
            }
        )
    }

    override fun onStart() {
        super.onStart()
        if (queue.isNotEmpty() && ::playerView.isInitialized) initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_INDEX, index)
        outState.putLong(STATE_POSITION, player?.currentPosition ?: resumePosition)
    }

    private fun initPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { p ->
                playerView.player = p
                p.playWhenReady = true
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        errorText.text = getString(R.string.play_error, error.errorCodeName)
                        errorText.visibility = View.VISIBLE
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) errorText.visibility = View.GONE
                    }
                })
            }
        playCurrent()
    }

    private fun releasePlayer() {
        player?.let { if (!live) resumePosition = it.currentPosition }
        player?.release()
        player = null
        if (::playerView.isInitialized) playerView.player = null
    }

    private fun playCurrent() {
        val item = queue[index]
        val builder = MediaItem.Builder().setUri(item.url)
        if (item.url.lowercase().contains(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        errorText.visibility = View.GONE
        player?.run {
            if (!live && resumePosition > 0) setMediaItem(builder.build(), resumePosition)
            else setMediaItem(builder.build())
            prepare()
        }
        showInfo(item)
    }

    private fun change(delta: Int) {
        if (queue.size < 2) return
        index = (index + delta + queue.size) % queue.size
        resumePosition = 0L
        playCurrent()
    }

    private fun showInfo(item: PlayItem) {
        channelInfo.text = if (live) getString(R.string.channel_info, index + 1, item.title) else item.title
        channelInfo.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        handler.postDelayed(hideInfo, 4_000)
    }

    /** Controle remoto: ▲▼ e CH+/CH- trocam de canal. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && ::playerView.isInitialized) {
            val controlsHidden = !playerView.isControllerFullyVisible
            when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    change(+1); return true
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    change(-1); return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> if (live && controlsHidden) {
                    change(+1); return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> if (live && controlsHidden) {
                    change(-1); return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (controlsHidden) {
                    playerView.showController(); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

package com.oxe.iptvplayer

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible

/** Mostra o QR Code para usar o celular como controle remoto. */
class RemoteActivity : AppCompatActivity() {

    private lateinit var toggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote)
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        toggle = findViewById(R.id.remote_toggle)
        toggle.setOnClickListener {
            if (RemoteServer.running) {
                RemoteServer.stop()
                Prefs.setRemoteEnabled(this, false)
            } else {
                RemoteServer.start(this)
                Prefs.setRemoteEnabled(this, true)
            }
            render()
        }
        // Ao abrir esta tela, liga o controle
        RemoteServer.start(this)
        Prefs.setRemoteEnabled(this, true)
        render()
        toggle.requestFocus()
    }

    private fun render() {
        val on = RemoteServer.running
        val url = if (on) RemoteServer.url() else null
        val qr = findViewById<ImageView>(R.id.remote_qr)
        val urlText = findViewById<TextView>(R.id.remote_url)
        val status = findViewById<TextView>(R.id.remote_status)

        toggle.setText(if (on) R.string.remote_turn_off else R.string.remote_turn_on)
        when {
            !on -> {
                status.setText(R.string.remote_off)
                qr.isVisible = false
                urlText.isVisible = false
            }
            url == null -> {
                status.setText(R.string.remote_no_wifi)
                qr.isVisible = false
                urlText.isVisible = false
            }
            else -> {
                status.setText(R.string.remote_on)
                qr.setImageBitmap(License.qrCode(url, 600))
                qr.isVisible = true
                urlText.text = url
                urlText.isVisible = true
            }
        }
    }
}

package com.oxe.iptvplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/** Primeira tela: papel de parede + botão "Adicionar IPTV". */
class WelcomeActivity : AppCompatActivity() {

    companion object {
        /** Volta para o começo do app, apagando as telas anteriores. */
        fun restart(ctx: Context) {
            ctx.startActivity(
                Intent(ctx, WelcomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Já tem lista? Vai direto para o início.
        if (Repository.ensure(this)) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_welcome)
        val add = findViewById<Button>(R.id.add_button)
        add.focusZoom(1.05f)
        add.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
        add.requestFocus()
    }
}

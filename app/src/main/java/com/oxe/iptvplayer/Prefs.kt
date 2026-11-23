package com.oxe.iptvplayer

import android.content.Context
import androidx.appcompat.app.AlertDialog

/** Preferências gerais do app (qualidade do vídeo). */
object Prefs {
    private const val PREFS = "oxe_prefs"
    private const val KEY_QUALITY = "quality"

    /** 0 = automática; senão, altura máxima do vídeo (1080, 720...). */
    val QUALITIES = intArrayOf(0, 1080, 720, 480, 360)
    private val LABELS = intArrayOf(R.string.q_auto, R.string.q_1080, R.string.q_720, R.string.q_480, R.string.q_360)

    fun quality(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_QUALITY, 0)

    fun setQuality(ctx: Context, q: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_QUALITY, q).apply()
    }

    fun qualityLabel(ctx: Context, q: Int): String {
        val i = QUALITIES.indexOf(q).coerceAtLeast(0)
        return ctx.getString(LABELS[i])
    }

    fun showQualityDialog(ctx: Context, title: String, onChosen: (Int) -> Unit) {
        val labels = LABELS.map { ctx.getString(it) }.toTypedArray()
        val current = QUALITIES.indexOf(quality(ctx)).coerceAtLeast(0)
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                setQuality(ctx, QUALITIES[which])
                dialog.dismiss()
                onChosen(QUALITIES[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

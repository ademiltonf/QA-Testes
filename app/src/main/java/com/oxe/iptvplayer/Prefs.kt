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

    // ---------------- Legendas ----------------
    /** 0 = desligadas, 1 = normal, 2 = grande, 3 = extra grande */
    private const val KEY_SUBS = "subtitles"
    private val SUB_LABELS = intArrayOf(R.string.subs_off, R.string.subs_normal, R.string.subs_large, R.string.subs_xlarge)

    fun subtitleMode(ctx: Context): Int = p(ctx).getInt(KEY_SUBS, 1)

    fun subtitleLabel(ctx: Context, mode: Int = subtitleMode(ctx)): String =
        ctx.getString(SUB_LABELS[mode.coerceIn(0, 3)])

    fun showSubtitleDialog(ctx: Context, onChosen: (Int) -> Unit) {
        val labels = SUB_LABELS.map { ctx.getString(it) }.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle(R.string.subtitles)
            .setSingleChoiceItems(labels, subtitleMode(ctx)) { d, which ->
                p(ctx).edit().putInt(KEY_SUBS, which).apply()
                d.dismiss()
                onChosen(which)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- Atualização automática ----------------
    private const val KEY_AUTO = "auto_update_hours"
    private const val KEY_LAST = "last_refresh"
    private val AUTO_HOURS = intArrayOf(0, 3, 6, 12, 24)
    private val AUTO_LABELS = intArrayOf(R.string.auto_off, R.string.auto_3h, R.string.auto_6h, R.string.auto_12h, R.string.auto_24h)

    fun autoUpdateHours(ctx: Context): Int = p(ctx).getInt(KEY_AUTO, 6)

    fun autoUpdateLabel(ctx: Context): String =
        ctx.getString(AUTO_LABELS[AUTO_HOURS.indexOf(autoUpdateHours(ctx)).coerceAtLeast(0)])

    fun lastRefresh(ctx: Context): Long = p(ctx).getLong(KEY_LAST, 0)

    fun markRefreshed(ctx: Context) {
        p(ctx).edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
    }

    /** Está na hora de atualizar sozinho? */
    fun refreshDue(ctx: Context): Boolean {
        val h = autoUpdateHours(ctx)
        return h > 0 && System.currentTimeMillis() - lastRefresh(ctx) > h * 3_600_000L
    }

    fun showAutoUpdateDialog(ctx: Context, onChosen: () -> Unit) {
        val labels = AUTO_LABELS.map { ctx.getString(it) }.toTypedArray()
        val current = AUTO_HOURS.indexOf(autoUpdateHours(ctx)).coerceAtLeast(0)
        AlertDialog.Builder(ctx)
            .setTitle(R.string.auto_update)
            .setSingleChoiceItems(labels, current) { d, which ->
                p(ctx).edit().putInt(KEY_AUTO, AUTO_HOURS[which]).apply()
                d.dismiss()
                onChosen()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- Controle pelo celular ----------------
    private const val KEY_REMOTE = "remote_enabled"

    fun remoteEnabled(ctx: Context): Boolean = p(ctx).getBoolean(KEY_REMOTE, false)

    fun setRemoteEnabled(ctx: Context, on: Boolean) {
        p(ctx).edit().putBoolean(KEY_REMOTE, on).apply()
    }

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

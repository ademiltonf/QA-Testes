package com.oxe.iptvplayer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lembretes: avisa 5 minutos antes de um programa começar. */
object Reminders {
    private const val PREFS = "oxe_reminders"
    private const val KEY = "list"
    private const val CHANNEL_ID = "lembretes"
    const val ADVANCE = 5 * 60_000L

    data class Reminder(
        val id: Int,
        val program: String,
        val channel: String,
        val start: Long,
        val url: String,
        val image: String?
    )

    private val timeFormat get() = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))

    fun describe(r: Reminder): String = "${timeFormat.format(Date(r.start))} · ${r.program} (${r.channel})"

    @Synchronized
    fun list(ctx: Context): List<Reminder> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o ->
                    Reminder(
                        o.getInt("id"), o.optString("program"), o.optString("channel"),
                        o.optLong("start"), o.optString("url"), o.optString("image").nullIfBlank()
                    )
                }
            }.filter { it.start > System.currentTimeMillis() - 60 * 60_000L } // some depois que passou
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun find(ctx: Context, url: String, start: Long): Reminder? =
        list(ctx).firstOrNull { it.url == url && it.start == start }

    @Synchronized
    fun add(ctx: Context, program: Program, channel: PlayItem): Reminder {
        val r = Reminder(
            id = (channel.url + program.start).hashCode(),
            program = program.title,
            channel = channel.title,
            start = program.start,
            url = channel.url,
            image = channel.image
        )
        write(ctx, list(ctx).filter { it.id != r.id } + r)
        schedule(ctx, r)
        return r
    }

    @Synchronized
    fun remove(ctx: Context, r: Reminder) {
        write(ctx, list(ctx).filter { it.id != r.id })
        alarmManager(ctx).cancel(alarmIntent(ctx, r))
    }

    /** Remarca todos (depois de reiniciar o aparelho). */
    fun rescheduleAll(ctx: Context) {
        list(ctx).forEach { schedule(ctx, it) }
    }

    private fun write(ctx: Context, list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("program", it.program).put("channel", it.channel)
                    .put("start", it.start).put("url", it.url).put("image", it.image ?: "")
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private fun alarmManager(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun alarmIntent(ctx: Context, r: Reminder): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("id", r.id)
            .putExtra("program", r.program)
            .putExtra("channel", r.channel)
            .putExtra("url", r.url)
            .putExtra("image", r.image)
        return PendingIntent.getBroadcast(
            ctx, r.id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedule(ctx: Context, r: Reminder) {
        val at = (r.start - ADVANCE).coerceAtLeast(System.currentTimeMillis() + 2_000)
        if (r.start < System.currentTimeMillis()) return
        val am = alarmManager(ctx)
        val pi = alarmIntent(ctx, r)
        if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.set(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.reminders), NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    fun notify(ctx: Context, id: Int, program: String, channel: String, url: String, image: String?) {
        ensureChannel(ctx)
        val open = Intent(ctx, PlayerActivity::class.java)
            .putExtras(PlayerActivity.directExtras(url, channel, image, live = true))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_live_tv)
            .setColor(0xFFFF7A00.toInt())
            .setContentTitle(ctx.getString(R.string.reminder_title, program))
            .setContentText(ctx.getString(R.string.reminder_text, channel))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // sem permissão de notificação: o aviso na tela abaixo ainda aparece
        }
        // Se o app estiver aberto (TV), mostra também um aviso na tela
        Toast.makeText(
            ctx,
            ctx.getString(R.string.reminder_title, program) + "\n" + ctx.getString(R.string.reminder_text, channel),
            Toast.LENGTH_LONG
        ).show()
    }
}

/** Recebe o alarme e mostra o aviso. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.notify(
            context,
            intent.getIntExtra("id", 0),
            intent.getStringExtra("program").orEmpty(),
            intent.getStringExtra("channel").orEmpty(),
            intent.getStringExtra("url").orEmpty(),
            intent.getStringExtra("image")
        )
    }
}

/** Depois de reiniciar o aparelho, remarca os lembretes e liga o controle pelo celular. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminders.rescheduleAll(context)
        }
    }
}

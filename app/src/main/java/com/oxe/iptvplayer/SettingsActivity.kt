package com.oxe.iptvplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Configurações: relógio, licença, reprodução, ferramentas e listas. */
class SettingsActivity : AppCompatActivity() {

    private val adapter = AccountAdapter(
        onUse = { useAccount(it) },
        onDelete = { confirmDelete(it) }
    )

    /** Prepara uma linha de opção (ícone + título) e devolve o campo de valor. */
    private fun row(id: Int, @DrawableRes icon: Int, @StringRes title: Int, onClick: () -> Unit): TextView {
        val v = findViewById<View>(id)
        v.findViewById<ImageView>(R.id.row_icon).setImageResource(icon)
        v.findViewById<TextView>(R.id.row_title).setText(title)
        v.focusZoom(1.02f)
        v.setOnClickListener { onClick() }
        return v.findViewById(R.id.row_value)
    }

    private lateinit var licenseValue: TextView
    private lateinit var remoteValue: TextView
    private lateinit var remindersValue: TextView
    private lateinit var autoValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.add_list_button).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        findViewById<TextView>(R.id.version_text).text =
            "${getString(R.string.app_name)} · versão ${BuildConfigInfo.versionName(this)}"

        // Licença (só no PRO)
        licenseValue = row(R.id.license_row, R.drawable.ic_check_circle, R.string.license) {
            LicenseActivity.manage(this)
        }
        findViewById<View>(R.id.license_row).isVisible = BuildConfig.PRO

        // Reprodução
        val qualityValue = row(R.id.quality_row, R.drawable.ic_hd_orange, R.string.quality) {}
        qualityValue.text = Prefs.qualityLabel(this, Prefs.quality(this))
        findViewById<View>(R.id.quality_row).setOnClickListener {
            Prefs.showQualityDialog(this, getString(R.string.quality)) { q ->
                qualityValue.text = Prefs.qualityLabel(this, q)
            }
        }

        val subsValue = row(R.id.subs_row, R.drawable.ic_subtitles, R.string.subtitles) {}
        subsValue.text = Prefs.subtitleLabel(this)
        findViewById<View>(R.id.subs_row).setOnClickListener {
            Prefs.showSubtitleDialog(this) { subsValue.text = Prefs.subtitleLabel(this) }
        }

        autoValue = row(R.id.auto_row, R.drawable.ic_sync, R.string.auto_update) {
            Prefs.showAutoUpdateDialog(this) { showAuto() }
        }

        // Ferramentas
        row(R.id.speed_row, R.drawable.ic_speed, R.string.speed_test) {
            startActivity(Intent(this, SpeedTestActivity::class.java))
        }.setText(R.string.speed_row_hint)
        remoteValue = row(R.id.remote_row, R.drawable.ic_phone_remote, R.string.remote) {
            startActivity(Intent(this, RemoteActivity::class.java))
        }
        remindersValue = row(R.id.reminders_row, R.drawable.ic_bell, R.string.reminders) { showReminders() }

        findViewById<RecyclerView>(R.id.account_list).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = this@SettingsActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
        showAuto()
        remoteValue.setText(if (RemoteServer.running) R.string.remote_value_on else R.string.remote_value_off)
        remindersValue.text = getString(R.string.items_count, Reminders.list(this).size)
        if (BuildConfig.PRO) {
            showLicense()
            // Consulta o servidor e atualiza o status da licença
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { runCatching { License.check(this@SettingsActivity) } }
                showLicense()
            }
        }
    }

    private fun showAuto() {
        val last = Prefs.lastRefresh(this)
        val time = if (last > 0) SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(last)) else "—"
        autoValue.text = "${Prefs.autoUpdateLabel(this)}\n" + getString(R.string.auto_last, time)
    }

    private fun showLicense() {
        val sub = findViewById<View>(R.id.license_row).findViewById<TextView>(R.id.row_sub)
        sub.text = "${getString(R.string.lic_device_code)}: ${License.deviceId(this)}"
        sub.isVisible = true
        val s = License.cached(this)
        licenseValue.text = when {
            s == null -> ""
            s.isPaid && s.plan == "vitalicio" -> getString(R.string.lic_active_lifetime)
            s.isPaid -> getString(
                R.string.lic_active_until,
                SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(s.activeUntil ?: 0))
            )
            s.allowed -> resources.getQuantityString(R.plurals.lic_trial_left, s.trialDaysLeft, s.trialDaysLeft)
            else -> getString(R.string.lic_trial_over)
        }
    }

    /** Lista de lembretes: tocar em um para cancelar. */
    private fun showReminders() {
        val list = Reminders.list(this).sortedBy { it.start }
        if (list.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.reminders)
                .setMessage(R.string.reminders_empty)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.reminders_tap_cancel)
            .setItems(list.map { Reminders.describe(it) }.toTypedArray()) { _, which ->
                val r = list[which]
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.reminder_cancel_confirm, r.program))
                    .setPositiveButton(R.string.remove) { _, _ ->
                        Reminders.remove(this, r)
                        remindersValue.text = getString(R.string.items_count, Reminders.list(this).size)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.back, null)
            .show()
    }

    private fun reload() {
        adapter.submit(AccountStore.list(this), AccountStore.active(this)?.id)
    }

    private fun useAccount(acc: Account) {
        if (acc.id == AccountStore.active(this)?.id) {
            finish()
            return
        }
        AccountStore.setActive(this, acc.id)
        Repository.close()
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }

    private fun confirmDelete(acc: Account) {
        AlertDialog.Builder(this)
            .setTitle(R.string.remove)
            .setMessage(getString(R.string.remove_confirm, acc.name))
            .setPositiveButton(R.string.remove) { _, _ ->
                val wasActive = acc.id == AccountStore.active(this)?.id
                AccountStore.remove(this, acc.id)
                if (!wasActive) {
                    reload()
                    return@setPositiveButton
                }
                Repository.close()
                val next = AccountStore.list(this).firstOrNull()
                if (next != null) {
                    AccountStore.setActive(this, next.id)
                    startActivity(
                        Intent(this, HomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                } else {
                    WelcomeActivity.restart(this)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

class AccountAdapter(
    private val onUse: (Account) -> Unit,
    private val onDelete: (Account) -> Unit
) : RecyclerView.Adapter<AccountAdapter.VH>() {

    private var items: List<Account> = emptyList()
    private var activeId: String? = null

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Account>, active: String?) {
        items = list
        activeId = active
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.account_card)
        val icon: ImageView = view.findViewById(R.id.account_icon)
        val title: TextView = view.findViewById(R.id.account_title)
        val subtitle: TextView = view.findViewById(R.id.account_subtitle)
        val status: TextView = view.findViewById(R.id.account_status)
        val delete: View = view.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_account, parent, false)
        return VH(view).also { it.card.focusZoom(1.02f) }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val acc = items[position]
        val ctx = holder.itemView.context
        val active = acc.id == activeId
        holder.title.text = acc.name
        holder.subtitle.text = when (acc.kind) {
            Account.Kind.XTREAM -> "${ctx.getString(R.string.kind_xtream)} · ${acc.username}"
            Account.Kind.M3U_URL -> ctx.getString(R.string.kind_m3u)
            Account.Kind.M3U_FILE -> ctx.getString(R.string.kind_file)
        }
        holder.icon.setImageResource(if (active) R.drawable.ic_check_circle else R.drawable.ic_list)
        holder.status.setText(if (active) R.string.in_use else R.string.tap_to_use)
        holder.status.setTextColor(
            if (active) 0xFFFF7A00.toInt() else 0xFFA8A8A8.toInt()
        )
        holder.card.setOnClickListener { onUse(acc) }
        holder.delete.setOnClickListener { onDelete(acc) }
    }
}

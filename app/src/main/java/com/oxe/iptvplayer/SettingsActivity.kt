package com.oxe.iptvplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Configurações: relógio, trocar de lista, adicionar e remover listas. */
class SettingsActivity : AppCompatActivity() {

    private val adapter = AccountAdapter(
        onUse = { useAccount(it) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.add_list_button).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        val qualityValue = findViewById<TextView>(R.id.quality_value)
        qualityValue.text = Prefs.qualityLabel(this, Prefs.quality(this))
        findViewById<View>(R.id.quality_row).apply {
            focusZoom(1.02f)
            setOnClickListener {
                Prefs.showQualityDialog(this@SettingsActivity, getString(R.string.quality)) { q ->
                    qualityValue.text = Prefs.qualityLabel(this@SettingsActivity, q)
                }
            }
        }

        findViewById<RecyclerView>(R.id.account_list).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = this@SettingsActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
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

package com.oxe.iptvplayer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.Job

/** Lista de categorias (ou temporadas) em formato de "chips". */
class CategoryAdapter(
    private val vertical: Boolean,
    private val onSelect: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var items: List<Category> = emptyList()
    private var selected = 0

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Category>) {
        items = list
        selected = 0
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun select(index: Int) {
        if (index !in items.indices) return
        selected = index
        notifyDataSetChanged()
    }

    class VH(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false) as TextView
        if (!vertical) {
            tv.layoutParams = tv.layoutParams.apply { width = ViewGroup.LayoutParams.WRAP_CONTENT }
        }
        return VH(tv)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.text.text = items[position].name
        holder.text.isSelected = position == selected
        holder.text.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p == RecyclerView.NO_POSITION) return@setOnClickListener
            val old = selected
            selected = p
            notifyItemChanged(old)
            notifyItemChanged(p)
            onSelect(items[p])
        }
    }
}

/** Grade de canais (logo) ou de filmes/séries (pôster). */
class EntryAdapter(
    private val type: ContentType,
    private val isFavorite: (Entry) -> Boolean,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
    /** Busca "o que está passando agora" de um canal (só canais ao vivo). */
    private val epgLoader: ((Entry, (String?) -> Unit) -> Job?)? = null
) : RecyclerView.Adapter<EntryAdapter.VH>() {

    var items: List<Entry> = emptyList()
        private set

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Entry>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.image)
        val title: TextView = view.findViewById(R.id.title)
        val badge: View = view.findViewById(R.id.fav_badge)
        val epgNow: TextView? = view.findViewById(R.id.epg_now)
        var epgJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (type == ContentType.LIVE) R.layout.item_channel else R.layout.item_poster
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        view.focusZoom()
        val holder = VH(view)
        holder.image.clipToOutline = true
        return holder
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.title.text = e.name
        val placeholder = if (type == ContentType.LIVE) R.drawable.ph_channel else R.drawable.ph_poster
        holder.image.load(e.image) {
            placeholder(placeholder)
            error(placeholder)
            fallback(placeholder)
            crossfade(true)
        }
        holder.badge.visibility = if (isFavorite(e)) View.VISIBLE else View.GONE
        holder.epgJob?.cancel()
        holder.epgNow?.let { epgView ->
            epgView.visibility = View.GONE
            epgView.tag = e.id
            holder.epgJob = epgLoader?.invoke(e) { text ->
                if (epgView.tag == e.id && !text.isNullOrBlank()) {
                    epgView.text = text
                    epgView.visibility = View.VISIBLE
                }
            }
        }
        holder.itemView.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
        holder.itemView.setOnLongClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onLongClick(p)
            true
        }
    }
}

/** Lista de episódios de uma temporada. */
class EpisodeAdapter(
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    private var items: List<Episode> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Episode>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.image)
        val number: TextView = view.findViewById(R.id.number)
        val title: TextView = view.findViewById(R.id.title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false)
        view.focusZoom(1.02f)
        val holder = VH(view)
        holder.image.clipToOutline = true
        return holder
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ep = items[position]
        holder.number.text = holder.itemView.context.getString(R.string.episode_n, ep.number)
        holder.title.text = ep.title
        holder.image.load(ep.image) {
            placeholder(R.drawable.ph_poster)
            error(R.drawable.ph_poster)
            fallback(R.drawable.ph_poster)
            crossfade(true)
        }
        holder.itemView.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
    }
}

/** Fileira "Continuar assistindo" da tela inicial. */
class ContinueAdapter(
    private val onClick: (WatchProgress.Item) -> Unit,
    private val onLongClick: (WatchProgress.Item) -> Unit
) : RecyclerView.Adapter<ContinueAdapter.VH>() {

    private var items: List<WatchProgress.Item> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<WatchProgress.Item>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.image)
        val title: TextView = view.findViewById(R.id.title)
        val time: TextView = view.findViewById(R.id.time)
        val progress: android.widget.ProgressBar = view.findViewById(R.id.progress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_continue, parent, false)
        view.focusZoom(1.05f)
        return VH(view).also { it.image.clipToOutline = true }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.title.text = it.title
        holder.time.text = "${WatchProgress.formatTime(it.position)} / ${WatchProgress.formatTime(it.duration)}"
        holder.progress.progress = it.percent
        holder.image.load(it.image) {
            placeholder(R.drawable.ph_poster)
            error(R.drawable.ph_poster)
            fallback(R.drawable.ph_poster)
        }
        holder.itemView.setOnClickListener { _ ->
            items.getOrNull(holder.bindingAdapterPosition)?.let(onClick)
        }
        holder.itemView.setOnLongClickListener { _ ->
            items.getOrNull(holder.bindingAdapterPosition)?.let(onLongClick)
            true
        }
    }
}

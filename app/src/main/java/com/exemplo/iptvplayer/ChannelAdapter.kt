package com.exemplo.iptvplayer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class ChannelAdapter(
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    var items: List<Channel> = emptyList()
        private set

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Channel>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logo)
        val name: TextView = view.findViewById(R.id.name)
        val group: TextView = view.findViewById(R.id.group)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val channel = items[position]
        holder.name.text = channel.name
        holder.group.text = channel.group
        holder.logo.load(channel.logo) {
            placeholder(R.drawable.ic_tv)
            error(R.drawable.ic_tv)
            fallback(R.drawable.ic_tv)
            crossfade(true)
        }
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(pos)
        }
    }
}

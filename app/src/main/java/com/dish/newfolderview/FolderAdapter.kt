package com.dish.newfolderview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Renders folder items into the responsive grid. Tapping an item launches it.
 */
class FolderAdapter(
    private val items: List<FolderItem>,
    private val onClick: (FolderItem) -> Unit
) : RecyclerView.Adapter<FolderAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.item_icon)
        val label: TextView = view.findViewById(R.id.item_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tile, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.label.text = item.label

        val drawable = IconLoader.load(holder.itemView.context, item)
        if (drawable != null) {
            holder.icon.setImageDrawable(drawable)
        } else {
            holder.icon.setImageResource(R.mipmap.ic_launcher) // bundled fallback
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}

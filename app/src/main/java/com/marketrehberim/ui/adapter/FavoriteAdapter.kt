package com.marketrehberim.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.marketrehberim.data.model.Item
import com.marketrehberim.databinding.ItemFavoriteBinding
import com.marketrehberim.ui.theme.MarketPalette

class FavoriteAdapter(
    private val onClick: (Item) -> Unit,
) : ListAdapter<Item, FavoriteAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemFavoriteBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            binding.name.text = item.name
            binding.price.text = item.formattedPrice
            binding.from.text = item.from
            binding.marketDot.backgroundTintList = ColorStateList.valueOf(
                MarketPalette.colorFor(binding.root.context, item.from)
            )
            Glide.with(binding.root).load(item.image).into(binding.image)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFavoriteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(a: Item, b: Item) = a.name == b.name && a.from == b.from
            override fun areContentsTheSame(a: Item, b: Item) = a == b
        }
    }
}

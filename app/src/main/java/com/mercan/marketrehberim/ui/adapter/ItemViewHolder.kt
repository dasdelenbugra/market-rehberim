package com.mercan.marketrehberim.ui.adapter

import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.mercan.marketrehberim.data.model.Item
import com.mercan.marketrehberim.databinding.ItemLayoutBinding

class ItemViewHolder(private val binding: ItemLayoutBinding) :
    RecyclerView.ViewHolder(binding.root) {
    fun bind(item: Item) {
        binding.name.text = item.name
        binding.price.text = "${item.price} ₺"
        binding.from.text = item.from
        Glide.with(binding.root).load(item.image).into(binding.image)
    }
}
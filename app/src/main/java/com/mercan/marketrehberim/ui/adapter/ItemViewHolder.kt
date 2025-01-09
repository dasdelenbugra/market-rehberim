package com.mercan.marketrehberim.ui.adapter

import android.graphics.BitmapFactory
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.mercan.marketrehberim.data.model.Item
import com.mercan.marketrehberim.databinding.ItemLayoutBinding
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ItemViewHolder(private val binding: ItemLayoutBinding) :
    RecyclerView.ViewHolder(binding.root) {
    @OptIn(ExperimentalEncodingApi::class)
    fun bind(item: Item) {
        binding.name.text = item.name
        binding.price.text = item.price

        if (item.imageUrl.startsWith("https")) {
            // Glide
            Glide.with(binding.root).load(item.imageUrl).into(binding.image)
        } else {
            // Base64
            val base64Image = item.imageUrl.split(",")[1]
            val decodedString = Base64.decode(base64Image, 0)
            val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            binding.image.setImageBitmap(bitmap)
        }
    }
}
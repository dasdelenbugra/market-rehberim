package com.marketrehberim.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.marketrehberim.data.remote.dto.MarketBasket
import com.marketrehberim.databinding.ItemMarketBasketBinding
import com.marketrehberim.ui.theme.MarketPalette

class MarketBasketAdapter :
    ListAdapter<MarketBasket, MarketBasketAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemMarketBasketBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: MarketBasket) {
            val context = binding.root.context
            binding.market.text = row.market
            binding.total.text = "%.2f ₺".format(row.total)
            binding.coverage.text = "${row.foundCount} / ${row.totalCount} ürün bulundu"
            binding.marketDot.backgroundTintList =
                ColorStateList.valueOf(MarketPalette.colorFor(context, row.market))

            // Sepeti tam karşılamayan market soluklaşır ama listede kalır:
            // "3/4 ama çok ucuz" bilgisiyle kararı kullanıcı verir.
            binding.root.alpha = if (row.complete) 1f else 0.6f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMarketBasketBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MarketBasket>() {
            override fun areItemsTheSame(a: MarketBasket, b: MarketBasket) = a.market == b.market
            override fun areContentsTheSame(a: MarketBasket, b: MarketBasket) = a == b
        }
    }
}

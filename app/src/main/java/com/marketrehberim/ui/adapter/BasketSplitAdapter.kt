package com.marketrehberim.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.marketrehberim.R
import com.marketrehberim.data.remote.dto.OptimalLine
import com.marketrehberim.databinding.ItemBasketSplitBinding
import com.marketrehberim.ui.theme.MarketPalette

/** En ucuz dağıtımın satırları: ürün → market → fiyat. */
class BasketSplitAdapter : ListAdapter<OptimalLine, BasketSplitAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemBasketSplitBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(line: OptimalLine) {
            val context = binding.root.context
            binding.name.text = line.name

            val market = line.market
            binding.market.text = market ?: context.getString(R.string.not_found)
            binding.marketDot.backgroundTintList =
                ColorStateList.valueOf(MarketPalette.colorFor(context, market))
            // Bulunamayan ürünün noktası anlamsız; satır soluklaşır.
            binding.marketDot.visibility = if (market == null) View.INVISIBLE else View.VISIBLE
            binding.root.alpha = if (market == null) 0.55f else 1f

            binding.price.text = line.price
                ?.toDoubleOrNull()
                ?.let { "%.2f ₺".format(it) }
                ?: "—"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemBasketSplitBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<OptimalLine>() {
            override fun areItemsTheSame(a: OptimalLine, b: OptimalLine) = a.name == b.name
            override fun areContentsTheSame(a: OptimalLine, b: OptimalLine) = a == b
        }
    }
}

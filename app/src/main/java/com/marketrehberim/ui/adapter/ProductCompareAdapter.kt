package com.marketrehberim.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.marketrehberim.data.model.Item
import com.marketrehberim.databinding.ItemCompareBinding
import com.marketrehberim.ui.theme.MarketPalette

/**
 * Aynı ürünün marketlere göre fiyatı. Çubuk uzunluğu en pahalıya orantılı;
 * en ucuz satır yeşile boyanır.
 */
class ProductCompareAdapter : ListAdapter<Item, ProductCompareAdapter.VH>(DIFF) {

    private var highestPrice: Double = 0.0
    private var lowestPrice: Double = Double.MAX_VALUE

    /** Ölçek, listeyle birlikte verilir; her bağlamada yeniden taranmaz. */
    fun submit(items: List<Item>) {
        val prices = items.map { it.priceValue }.filter { it != Double.MAX_VALUE }
        highestPrice = prices.maxOrNull() ?: 0.0
        lowestPrice = prices.minOrNull() ?: Double.MAX_VALUE
        submitList(items)
    }

    inner class VH(private val binding: ItemCompareBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Item) {
            val context = binding.root.context
            binding.market.text = item.from
            binding.price.text = item.formattedPrice
            binding.marketDot.backgroundTintList =
                ColorStateList.valueOf(MarketPalette.colorFor(context, item.from))

            val isCheapest = item.priceValue == lowestPrice && item.priceValue != Double.MAX_VALUE
            val gainColor = MaterialColors.getColor(
                binding.root, com.google.android.material.R.attr.colorPrimary
            )
            val neutralColor = MaterialColors.getColor(
                binding.root, com.google.android.material.R.attr.colorOutlineVariant
            )
            val priceColor = MaterialColors.getColor(
                binding.root, com.google.android.material.R.attr.colorTertiary
            )

            binding.price.setTextColor(if (isCheapest) gainColor else priceColor)
            binding.bar.setIndicatorColor(if (isCheapest) gainColor else neutralColor)
            binding.bar.trackColor = MaterialColors.getColor(
                binding.root, com.google.android.material.R.attr.colorSurfaceContainerHighest
            )
            binding.bar.progress = progressOf(item)
        }

        /**
         * Çubuk en pahalıya göre ölçeklenir ama tabandan başlar (min %30), yoksa
         * ucuz ürün görünmez bir çizgiye iner ve karşılaştırma okunmaz olur.
         */
        private fun progressOf(item: Item): Int {
            if (highestPrice <= 0.0 || item.priceValue == Double.MAX_VALUE) return 100
            val ratio = item.priceValue / highestPrice
            return (BASE + (100 - BASE) * ratio).toInt().coerceIn(BASE, 100)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCompareBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private const val BASE = 30

        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(a: Item, b: Item) = a.from == b.from && a.name == b.name
            override fun areContentsTheSame(a: Item, b: Item) = a == b
        }
    }
}

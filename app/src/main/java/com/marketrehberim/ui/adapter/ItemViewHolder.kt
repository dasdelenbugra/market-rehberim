package com.marketrehberim.ui.adapter

import android.content.res.ColorStateList
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.marketrehberim.R
import com.marketrehberim.data.model.Item
import com.marketrehberim.databinding.ItemLayoutBinding
import com.marketrehberim.ui.theme.MarketPalette

class ItemViewHolder(
    private val binding: ItemLayoutBinding,
    private val onClick: (Item) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    /**
     * @param cheapestPrice listedeki en düşük fiyat; satırın en ucuza göre kaç lira
     *   pahalı olduğunu göstermek için kullanılır.
     */
    fun bind(item: Item, isCheapest: Boolean, cheapestPrice: Double) {
        val context = binding.root.context

        binding.name.text = item.name
        binding.price.text = item.formattedPrice
        binding.from.text = item.from
        binding.marketDot.backgroundTintList =
            ColorStateList.valueOf(MarketPalette.colorFor(context, item.from))

        binding.tvCheapest.visibility = if (isCheapest) View.VISIBLE else View.GONE

        // En ucuz satır yeşil kontur + yeşil fiyat; diğerleri nötr turuncu kalır.
        // Renk artık sıra bildiriyor, her satırda aynı vurguyu tekrar etmiyor.
        val gainColor =
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
        val priceColor =
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSecondary)

        binding.price.setTextColor(if (isCheapest) gainColor else priceColor)
        binding.card.strokeColor = gainColor
        binding.card.strokeWidth = if (isCheapest) {
            context.resources.getDimensionPixelSize(R.dimen.cheapest_stroke)
        } else {
            0
        }

        bindDifference(item, isCheapest, cheapestPrice)

        Glide.with(binding.root).load(item.image).into(binding.image)
        binding.root.setOnClickListener { onClick(item) }
    }

    /** "En ucuza göre kaç lira fazla" — listeyi taranabilir yapan bilgi. */
    private fun bindDifference(item: Item, isCheapest: Boolean, cheapestPrice: Double) {
        val diff = item.priceValue - cheapestPrice
        val showable = !isCheapest &&
            item.priceValue != Double.MAX_VALUE &&
            cheapestPrice != Double.MAX_VALUE &&
            diff > 0.0

        if (showable) {
            binding.tvDiff.text = binding.root.context.getString(R.string.price_diff, diff)
            binding.tvDiff.visibility = View.VISIBLE
        } else {
            binding.tvDiff.visibility = View.GONE
        }
    }
}

package com.marketrehberim.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.marketrehberim.R
import com.marketrehberim.data.remote.dto.ProductGroup
import com.marketrehberim.databinding.ItemProductGroupBinding
import com.marketrehberim.databinding.ItemRelatedHeaderBinding
import com.marketrehberim.ui.state.SearchRow
import com.marketrehberim.ui.theme.MarketPalette

/**
 * Arama sonucu listesi: ürün grupları + "ilgili ürünler" başlığı.
 *
 * Eskiden liste düz `Item` satırlarıydı ve aynı ürün her markette bir kez
 * tekrar ediyordu; "muz" araması 34 satır dönüyor, bunların 11'i aynı iki muzun
 * farklı marketleri oluyordu. Artık satır = ürün, marketler satırın içinde
 * özetleniyor.
 */
class ProductGroupAdapter(
    private val onProductClick: (ProductGroup) -> Unit = {},
    private val onRelatedToggle: () -> Unit = {},
) : ListAdapter<SearchRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    /**
     * Ana bölümdeki en düşük **karşılaştırma** fiyatı (birim fiyat öncelikli,
     * bkz. ProductGroup.comparablePriceValue). "EN UCUZ" rozeti yalnız burada
     * anlamlı: ilgili ürünler bölümündeki gofretle muzu kıyaslamak yanlış
     * olurdu; paket fiyatıyla kıyaslamak da 350 gr'a rozet takıyordu.
     */
    private var cheapestMain: Double = Double.MAX_VALUE

    override fun onCurrentListChanged(
        previousList: MutableList<SearchRow>,
        currentList: MutableList<SearchRow>,
    ) {
        super.onCurrentListChanged(previousList, currentList)
        cheapestMain = currentList
            .asSequence()
            // Başlıktan sonrası "ilgili ürünler"; rozet yarışına girmezler.
            .takeWhile { it !is SearchRow.RelatedHeader }
            .filterIsInstance<SearchRow.Product>()
            .map { it.group.comparablePriceValue }
            .minOrNull() ?: Double.MAX_VALUE
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SearchRow.Product -> TYPE_PRODUCT
        is SearchRow.RelatedHeader -> TYPE_HEADER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemRelatedHeaderBinding.inflate(inflater, parent, false), onRelatedToggle)
        } else {
            GroupHolder(ItemProductGroupBinding.inflate(inflater, parent, false), onProductClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is SearchRow.Product -> (holder as GroupHolder).bind(row.group, cheapestMain)
            is SearchRow.RelatedHeader -> (holder as HeaderHolder).bind(row)
        }
    }

    class GroupHolder(
        private val binding: ItemProductGroupBinding,
        private val onClick: (ProductGroup) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: ProductGroup, cheapestMain: Double) {
            val context = binding.root.context

            binding.name.text = group.name
            binding.from.text = group.bestMarket
            binding.price.text = format(group.bestPrice)
            binding.marketDot.backgroundTintList =
                ColorStateList.valueOf(MarketPalette.colorFor(context, group.bestMarket))

            val isCheapest = group.comparablePriceValue == cheapestMain &&
                group.comparablePriceValue != Double.MAX_VALUE
            binding.tvCheapest.visibility = if (isCheapest) View.VISIBLE else View.GONE
            binding.price.setTextColor(
                MaterialColors.getColor(
                    binding.root,
                    if (isCheapest) com.google.android.material.R.attr.colorPrimary
                    else com.google.android.material.R.attr.colorTertiary,
                )
            )

            bindSpread(group)
            bindUnitPrice(group)

            Glide.with(binding.root).load(group.image).into(binding.image)
            binding.root.setOnClickListener { onClick(group) }
        }

        /**
         * "5 markette · 79,00 – 94,90 ₺" — uygulamanın asıl vaadi olan
         * karşılaştırma. Tek markette bulunan ürün için aralık yazılmaz;
         * "1 markette · 79,00 – 79,00 ₺" gürültüden ibaret olurdu.
         */
        private fun bindSpread(group: ProductGroup) {
            val context = binding.root.context
            binding.tvMarketSpread.text = if (group.marketCount > 1) {
                context.getString(
                    R.string.market_spread,
                    group.marketCount,
                    group.bestPrice.toDoubleOrNull() ?: 0.0,
                    group.maxPrice.toDoubleOrNull() ?: 0.0,
                )
            } else {
                context.getString(R.string.market_single)
            }
        }

        private fun bindUnitPrice(group: ProductGroup) {
            val value = group.unitPrice?.toDoubleOrNull()
            val unit = group.unit
            if (value == null || unit == null) {
                binding.tvUnitPrice.visibility = View.GONE
                return
            }
            binding.tvUnitPrice.text = "%.2f ₺/%s".format(value, unit)
            binding.tvUnitPrice.visibility = View.VISIBLE
        }

        private fun format(price: String): String =
            price.toDoubleOrNull()?.let { "%.2f ₺".format(it) } ?: "$price ₺"
    }

    class HeaderHolder(
        private val binding: ItemRelatedHeaderBinding,
        private val onToggle: () -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: SearchRow.RelatedHeader) {
            binding.tvRelatedTitle.text =
                binding.root.context.getString(R.string.related_products, row.count)
            // Kapalıyken aşağı, açıkken yukarı bakar. Dokunuşta ok atlamak
            // yerine dönsün; geri dönüşümde yarım kalmış animasyon iptal edilir.
            val target = if (row.expanded) 180f else 0f
            binding.ivChevron.animate().cancel()
            if (binding.ivChevron.rotation != target) {
                binding.ivChevron.animate().rotation(target).setDuration(200L).start()
            }
            binding.root.setOnClickListener { onToggle() }
        }
    }

    companion object {
        private const val TYPE_PRODUCT = 0
        private const val TYPE_HEADER = 1

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SearchRow>() {
            override fun areItemsTheSame(oldItem: SearchRow, newItem: SearchRow): Boolean =
                when {
                    oldItem is SearchRow.Product && newItem is SearchRow.Product ->
                        oldItem.group.name == newItem.group.name
                    oldItem is SearchRow.RelatedHeader && newItem is SearchRow.RelatedHeader -> true
                    else -> false
                }

            override fun areContentsTheSame(oldItem: SearchRow, newItem: SearchRow): Boolean =
                oldItem == newItem
        }
    }
}

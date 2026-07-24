package com.marketrehberim.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.marketrehberim.data.model.Item
import com.marketrehberim.databinding.ItemLayoutBinding

class ItemAdapter(
    private val onClick: (Item) -> Unit = {},
) : ListAdapter<Item, ItemViewHolder>(DIFF_CALLBACK) {

    /**
     * Listedeki en düşük fiyat. Her satırda yeniden taramak yerine liste
     * değiştiğinde bir kez hesaplanır.
     */
    private var cheapestPrice: Double = Double.MAX_VALUE

    override fun onCurrentListChanged(
        previousList: MutableList<Item>,
        currentList: MutableList<Item>,
    ) {
        super.onCurrentListChanged(previousList, currentList)
        cheapestPrice = currentList.minOfOrNull { it.priceValue } ?: Double.MAX_VALUE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        return ItemViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        // Listedeki en düşük fiyatlı kayıt "en ucuz" olarak işaretlenir; kalanlar
        // aradaki farkı gösterir.
        val item = getItem(position)
        val isCheapest = item.priceValue == cheapestPrice && item.priceValue != Double.MAX_VALUE
        holder.bind(item, isCheapest, cheapestPrice)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem.name == newItem.name && oldItem.from == newItem.from

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem == newItem
        }
    }
}

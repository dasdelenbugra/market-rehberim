package com.marketrehberim.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marketrehberim.data.model.Item

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,     // "$market::$name" — market + ürün benzersizliği
    val name: String,
    val price: String,
    val image: String,
    val market: String,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toItem(): Item = Item(name = name, price = price, image = image, from = market)

    companion object {
        fun idOf(item: Item): String = "${item.from}::${item.name}"

        fun from(item: Item): FavoriteEntity = FavoriteEntity(
            id = idOf(item),
            name = item.name,
            price = item.price,
            image = item.image,
            market = item.from,
        )
    }
}

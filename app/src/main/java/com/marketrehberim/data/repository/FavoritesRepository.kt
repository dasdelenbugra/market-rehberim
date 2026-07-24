package com.marketrehberim.data.repository

import com.marketrehberim.data.local.dao.FavoriteDao
import com.marketrehberim.data.local.entity.FavoriteEntity
import com.marketrehberim.data.model.Item
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FavoritesRepository @Inject constructor(
    private val dao: FavoriteDao,
) {
    val favorites: Flow<List<Item>> =
        dao.observeAll().map { list -> list.map { it.toItem() } }

    fun isFavorite(item: Item): Flow<Boolean> =
        dao.observeIsFavorite(FavoriteEntity.idOf(item))

    suspend fun toggle(item: Item, makeFavorite: Boolean) {
        if (makeFavorite) dao.insert(FavoriteEntity.from(item))
        else dao.deleteById(FavoriteEntity.idOf(item))
    }

    /** Fiyat takibi için tek seferlik anlık liste. */
    suspend fun snapshot(): List<Item> = dao.getAll().map { it.toItem() }

    /** Takip bir düşüş bildirdikten sonra referans fiyatı tazeler. */
    suspend fun updatePrice(item: Item, price: String) =
        dao.updatePrice(FavoriteEntity.idOf(item), price)
}

package com.marketrehberim.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bir aramada kullanıcının seçtiği ürünün kaydı. Kazanç, seçilen fiyat ile aynı
 * aramadaki en yüksek fiyatın farkı: "bu ürünü en pahalı yerden almasaydın ne
 * kadar kalırdı".
 *
 * Uygulamanın tüm vaadi bu rakam olduğu için yerelde tutuluyor; sunucuya
 * gönderilmiyor.
 */
@Entity(tableName = "savings")
data class SavingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val itemName: String,
    val market: String,
    val chosenPrice: Double,
    val highestPrice: Double,
    val savedAt: Long = System.currentTimeMillis(),
)

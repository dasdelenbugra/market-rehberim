package com.marketrehberim.data.repository

import com.marketrehberim.data.local.dao.SavingDao
import com.marketrehberim.data.local.entity.SavingEntity
import com.marketrehberim.data.model.Item
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import javax.inject.Inject

/** Anasayfadaki tasarruf şeridini besleyen özet. */
data class SavingSummary(
    val total: Double = 0.0,
    val count: Int = 0,
)

class SavingsRepository @Inject constructor(
    private val dao: SavingDao,
) {
    /** İçinde bulunulan takvim ayının kazancı. */
    val thisMonth: Flow<SavingSummary>
        get() {
            val since = startOfMonth()
            return combine(
                dao.observeTotalSince(since),
                dao.observeCountSince(since),
            ) { total, count -> SavingSummary(total, count) }
        }

    /**
     * Kullanıcı bir sonucu açtığında çağrılır. Karşılaştırılacak alternatif yoksa
     * ya da seçilen zaten en pahalıysa kayıt açılmaz — sıfır kazancı "kazanç"
     * diye biriktirmenin anlamı yok.
     */
    suspend fun record(query: String, chosen: Item, highestPrice: Double) {
        val chosenPrice = chosen.priceValue
        if (chosenPrice == Double.MAX_VALUE || highestPrice == Double.MAX_VALUE) return
        if (highestPrice <= chosenPrice) return

        dao.insert(
            SavingEntity(
                query = query,
                itemName = chosen.name,
                market = chosen.from,
                chosenPrice = chosenPrice,
                highestPrice = highestPrice,
            )
        )
    }

    suspend fun clear() = dao.clear()

    /**
     * Ayın ilk gününün 00:00'ı. minSdk 25 olduğundan java.time yerine Calendar;
     * desugaring bağımlılığı eklemeye değmez.
     */
    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

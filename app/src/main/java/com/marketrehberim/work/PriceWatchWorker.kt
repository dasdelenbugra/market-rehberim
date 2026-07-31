package com.marketrehberim.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marketrehberim.data.local.CityStore
import com.marketrehberim.data.model.Item
import com.marketrehberim.data.repository.FavoritesRepository
import com.marketrehberim.data.repository.ItemRepository
import com.marketrehberim.notification.PriceAlerts
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Günde bir kez favori ürünleri backend'de yeniden arar; aynı marketteki fiyat
 * kayıtlı fiyatın altına indiyse bildirim gönderir ve referans fiyatı tazeler.
 *
 * Referansın tazelenmesi önemli: yoksa bir kez ucuzlayan ürün her gün yeniden
 * "ucuzladı" diye bildirilir.
 */
@HiltWorker
class PriceWatchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val favorites: FavoritesRepository,
    private val items: ItemRepository,
    private val cityStore: CityStore,
    private val alerts: PriceAlerts,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // İzin yoksa ağı hiç yormayalım; kullanıcı izni verince bir sonraki tur çalışır.
        if (!alerts.canNotify()) return Result.success()

        val watched = favorites.snapshot()
        if (watched.isEmpty()) return Result.success()

        val city = cityStore.cityKey
        watched.forEach { favorite -> checkOne(city, favorite) }
        return Result.success()
    }

    private suspend fun checkOne(city: String, favorite: Item) {
        val oldPrice = favorite.priceValue
        if (oldPrice == Double.MAX_VALUE) return

        // Hata durumunda boş liste: bir sonraki günlük tur yeniden dener.
        val results = items.search(city, favorite.name).getOrNull()?.items.orEmpty()
        if (results.isEmpty()) return

        // Yalnız aynı marketteki aynı ürün: başka bir marketin daha ucuz benzeri
        // "bu ürün ucuzladı" demek değil, o ayrı bir karşılaştırma.
        val current = results
            .filter { it.from.equals(favorite.from, ignoreCase = true) }
            .filter { it.name.equals(favorite.name, ignoreCase = true) }
            .map { it.priceValue }
            .filter { it != Double.MAX_VALUE }
            .minOrNull() ?: return

        if (current >= oldPrice) return

        if (alerts.notifyPriceDrop(favorite, oldPrice = oldPrice, newPrice = current)) {
            favorites.updatePrice(favorite, "%.2f".format(java.util.Locale.US, current))
        }
    }

    companion object {
        private const val UNIQUE_NAME = "price_watch"

        /**
         * Günlük periyot. WorkManager zaten pil/ağ durumuna göre erteler; sık
         * kontrol scraping yapan backend'i de gereksiz yorar.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceWatchWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                // KEEP: her açılışta yeniden kurulup sayaç sıfırlanmasın.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

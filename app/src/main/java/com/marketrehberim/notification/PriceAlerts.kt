package com.marketrehberim.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.marketrehberim.R
import com.marketrehberim.data.model.Item
import com.marketrehberim.ui.view.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Favori bir ürün ucuzladığında kullanıcıyı haberdar eder.
 *
 * Kanal tek: "fiyat düşüşü". Kullanıcı ayarlardan sadece bunu kapatabilsin diye
 * ayrı tutuldu; ileride başka bildirim türü eklenirse kendi kanalını alır.
 */
@Singleton
class PriceAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * @return bildirim gösterilebildiyse true. Android 13+ üzerinde izin yoksa
     *   sessizce false döner — takip işi bu yüzden başarısız sayılmamalı.
     */
    fun notifyPriceDrop(item: Item, oldPrice: Double, newPrice: Double): Boolean {
        if (!canNotify()) return false

        ensureChannel()

        val drop = abs(oldPrice - newPrice)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            item.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_splash_storefront)
            .setContentTitle(context.getString(R.string.price_drop_title, item.name))
            .setContentText(
                context.getString(R.string.price_drop_text, item.from, newPrice, drop)
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.price_drop_text, item.from, newPrice, drop)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        // Ürün başına sabit kimlik: aynı ürün için ikinci bildirim birikmez, tazeler.
        //
        // try/catch gereksiz görünse de değil: yukarıdaki `canNotify()` ile bu satır
        // arasında kullanıcı izni geri alabilir (işçi arka planda dakikalarca
        // çalışabiliyor). O durumda takip sessizce başarısız olmalı, uygulama
        // çökmemeli. Lint de zaten bu kontrolü izleyemediği için burayı işaretliyordu.
        return try {
            NotificationManagerCompat.from(context).notify(item.hashCode(), notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_price_drop),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_price_drop_desc)
            }
        )
    }

    private companion object {
        const val CHANNEL_ID = "price_drop"
    }
}

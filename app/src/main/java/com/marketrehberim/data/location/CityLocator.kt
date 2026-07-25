package com.marketrehberim.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Cihaz konumunu ile (il/adminArea) çevirir. Konum iznini çağıran taraf
 * kontrol eder; buraya izin verilmiş kabul edilerek gelinir. Konum ya da
 * geocoder başarısız olursa `null` döner — çağıran tarafta listeye düşülür.
 */
@Singleton
class CityLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun currentProvince(): String? {
        val location = awaitLocation() ?: return null
        return reverseProvince(location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitLocation(): Location? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
        cont.invokeOnCancellation { cts.cancel() }
    }

    /** Enlem/boylamdan il adını (adminArea) döndürür. Geocoder senkron çağrı
     *  olduğundan IO'ya alınır; hata durumunda null. */
    private suspend fun reverseProvince(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale("tr", "TR"))
                    .getFromLocation(lat, lon, 1)
                    ?.firstOrNull()
                    ?.adminArea
            }.getOrNull()
        }
}

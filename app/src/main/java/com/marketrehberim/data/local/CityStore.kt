package com.marketrehberim.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kullanıcının seçtiği şehri kalıcı tutar (SharedPreferences).
 * Şehir, birleşik aramanın ve crowdsourced verinin kapsamını belirler.
 */
@Singleton
class CityStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("market_prefs", Context.MODE_PRIVATE)

    var cityKey: String
        get() = prefs.getString(KEY, DEFAULT_KEY) ?: DEFAULT_KEY
        set(value) = prefs.edit().putString(KEY, value).apply()

    var cityLabel: String
        get() = prefs.getString(LABEL, DEFAULT_LABEL) ?: DEFAULT_LABEL
        set(value) = prefs.edit().putString(LABEL, value).apply()

    fun set(key: String, label: String) {
        cityKey = key
        cityLabel = label
    }

    companion object {
        private const val KEY = "city_key"
        private const val LABEL = "city_label"
        private const val DEFAULT_KEY = "tokat"
        private const val DEFAULT_LABEL = "Tokat"
    }
}

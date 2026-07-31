package com.marketrehberim.di

import com.marketrehberim.BuildConfig
import com.marketrehberim.data.remote.ItemRemoteSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * Zaman aşımları bilinçli olarak asimetrik:
     *
     * - **connect 8 sn** — TCP el sıkışması ya hızlı olur ya hiç olmaz. Eskiden
     *   60 sn'ydi ve OkHttp bunu bir de yeniden denediğinden, backend kapalıyken
     *   kullanıcı hata mesajını görene kadar ~35 sn iskelet ekrana bakıyordu.
     * - **read 45 sn** — backend ulusal kaynağı canlı tarıyor; önbellek soğukken
     *   yanıt saniyeler sürebilir, burada cimrilik yapmak aramayı boşuna düşürür.
     * - **call 60 sn** — tek bir isteğin toplam üst sınırı; sepet optimizasyonu
     *   gibi ağır uçlar sonsuza kadar asılı kalmasın.
     */
    @Singleton
    @Provides
    fun provideRetrofit(): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
    }

    @Singleton
    @Provides
    fun provideItemRemoteSource(retrofit: Retrofit): ItemRemoteSource =
        retrofit.create(ItemRemoteSource::class.java)
}
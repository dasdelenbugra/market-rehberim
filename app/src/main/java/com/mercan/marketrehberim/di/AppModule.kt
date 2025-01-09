package com.mercan.marketrehberim.di

import com.mercan.marketrehberim.data.remote.ItemRemoteSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun provideRetrofit(): Retrofit = Retrofit.Builder().baseUrl("http://0.0.0.0:5454/")
        .addConverterFactory(GsonConverterFactory.create()).build()

    @Singleton
    @Provides
    fun provideItemRemoteSource(retrofit: Retrofit): ItemRemoteSource =
        retrofit.create(ItemRemoteSource::class.java)
}
package com.mercan.marketrehberim.data.remote

import com.mercan.marketrehberim.data.model.Item
import retrofit2.http.GET
import retrofit2.http.Path

interface ItemRemoteSource {
    @GET("migros/{itemName}")
    suspend fun fetchMigros(@Path("itemName") itemName: String): List<Item>

    @GET("a101/{itemName}")
    suspend fun fetchA101(@Path("itemName") itemName: String): List<Item>

    @GET("erenler/{itemName}")
    suspend fun fetchErenler(@Path("itemName") itemName: String): List<Item>
}
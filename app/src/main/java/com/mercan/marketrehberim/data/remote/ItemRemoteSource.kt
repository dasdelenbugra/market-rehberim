package com.mercan.marketrehberim.data.remote

import com.mercan.marketrehberim.data.model.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject

class ItemRemoteSource @Inject constructor() {
    suspend fun fetchItems(itemName: String): List<Item> {
        val document = withContext(Dispatchers.IO) {
            Jsoup.connect("https://www.erenlercep.com/index.php?route=product/search&sort=p.price&order=ASC&search=$itemName")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3")
                .get()
        }

        val itemsRawData = document.select(".product-thumb").toMutableList()
        val items = itemsRawData.map {
            val name = it.select(".name").text()
            val description = "Erenler"
            val price = it.select(".price-normal").text()
            val imageUrl = it.select(".img-responsive").attr("data-src")

            Item(name, description, price, imageUrl)
        }

        return items
    }
}
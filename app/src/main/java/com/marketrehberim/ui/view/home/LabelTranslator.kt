package com.marketrehberim.ui.view.home

import java.util.Locale

/**
 * ML Kit etiketinin ne kadar işe yaradığı.
 *
 * İki başarısızlığı ayırmak gerekiyor: model gıda gördüğünü biliyor ama hangi
 * ürün olduğunu ayırt edemiyor ([GenericFood]) — ya da sahnede gıdayla ilgili
 * hiçbir şey yok ([None]). Kullanıcıya söylenecek şey ikisinde farklı.
 */
sealed interface LabelMatch {
    /** Aranabilir Türkçe karşılık bulundu. */
    data class Product(val query: String) : LabelMatch

    /** "Food" / "Fruit" gibi bir üst kategori: gıda evet, hangi ürün belirsiz. */
    data object GenericFood : LabelMatch

    /** Gıdayla ilgisi olmayan etiket ("Bicycle", "Sunset", "Shetland sheepdog"). */
    data object None : LabelMatch
}

/**
 * ML Kit görüntü etiketleyicisinin İngilizce etiketlerini Türkçe market
 * sorgusuna çevirir.
 *
 * Tablonun neden bu kadar kısa olduğu
 * -----------------------------------
 * Varsayılan model (`mlkit_label_default_model`, image-labeling AAR'ı içinde
 * gömülü) **sabit 447 etiketlik** bir sözlükle çalışıyor ve bu sözlük ürün
 * değil sahne/nesne etiketlemek için hazırlanmış: `Team`, `Bonfire`, `Comics`,
 * `Ferris wheel`, `Shetland sheepdog`... Gıda tarafında `Banana`, `Apple`,
 * `Tomato`, `Milk`, `Cheese`, `Egg` gibi etiketler **yok**. Elma `Fruit`,
 * domates `Vegetable`, süt en iyi ihtimalle `Food` olarak dönüyor.
 *
 * Bu yüzden tabloya yalnızca sözlükte gerçekten bulunan ve markette karşılığı
 * olan etiketler girer. Modelin üretemeyeceği bir etiketi çevirmek ölü kod
 * üretir — üstelik test edildiğinde geçtiği için çalıştığı sanılır.
 *
 * Sözlüğü yeniden çıkarmak için: image-labeling AAR'ındaki
 * `assets/mlkit_label_default_model/...tflite` dosyası metadata'lı bir TFLite
 * modeli; sonuna eklenmiş zip arşivinde `0-labels-en.txt` duruyor.
 */
object LabelTranslator {

    /**
     * Sözlükte var **ve** markette karşılığı olan etiketler.
     *
     * Gıda dışı etiketler (`Toy`, `Umbrella`, `Bicycle`, `Clock`...) tek tek
     * kaynağa sorularak elendi. Sonuç sayısına bakmak yetmiyor: kaynak benzer
     * yazılışa takılıyor ve alakasız sorgular bile dolu sonuç döndürüyor —
     * `minder` → "Kinder Country", `kask` → "Tatdo Sade Kase", `mum` → "Muz
     * Kremalı Gofret", `mont` → "Mon Amour Oje". Ölçüt sonuç sayısı değil, ilk
     * sonucun gerçekten istenen ürün olması; bunu geçen dört etiket eklendi.
     */
    private val products = mapOf(
        "bread" to "ekmek",
        "coffee" to "kahve",
        "cappuccino" to "kahve",
        "juice" to "meyve suyu",
        "cola" to "kola",
        "wine" to "şarap",
        "cookie" to "bisküvi",
        "cake" to "kek",
        "pie" to "turta",
        "pizza" to "pizza",
        "gelato" to "dondurma",
        "cheeseburger" to "hamburger",
        "hot dog" to "sosis",
        "couscous" to "kuskus",
        "sushi" to "suşi",

        // Gıda dışı, elemeyi geçenler
        "laundry" to "çamaşır deterjanı",
        "lipstick" to "ruj",
        "tableware" to "tabak",
    )

    /**
     * Gıda olduğunu söyleyen ama ürünü belirtmeyen etiketler.
     *
     * Bunlar bilerek aranmıyor. "Fruit" görüp `meyve` aramak, kaynakta
     * "meyve suyu"/"meyveli yoğurt" döndürüyor ve kullanıcı elmanın fotoğrafını
     * çekip karşısında meyve suyu görüyordu — tanıyamadığını söylemek dürüst.
     */
    private val genericFood = setOf(
        "food",
        "fruit",
        "vegetable",
        "cuisine",
        "meal",
        "lunch",
        "supper",
        "fast food",
        "eating",
        "picnic",
        "alcohol",
    )

    /**
     * Küçültme Locale.ROOT ile yapılır: cihaz dili Türkçeyken `lowercase()`
     * "I" harfini "ı"ya çevirir ("Ice" → "ıce"), tablodaki İngilizce anahtarlar
     * tutmaz ve eşleşme sessizce kaçardı.
     */
    fun match(label: String): LabelMatch {
        val key = label.trim().lowercase(Locale.ROOT)
        products[key]?.let { return LabelMatch.Product(it) }
        return if (key in genericFood) LabelMatch.GenericFood else LabelMatch.None
    }
}

package com.marketrehberim.ui.state



/**
 * Hata türü. Metin değil tür taşınır: kullanıcıya gösterilecek cümleyi
 * ViewModel değil Fragment seçer (string kaynakları oradan erişilebilir) ve
 * "Failed to connect to /10.0.2.2:5454" gibi ham istisna metinleri ekrana
 * sızmaz.
 */
enum class SearchError {
    /** Cihaz ağa çıkamadı ya da backend'e ulaşılamadı. Tekrar denemek anlamlı. */
    NETWORK,

    /** Backend yanıt verdi ama hata kodu döndü. */
    SERVER,

    UNKNOWN,
}

sealed class UIItemState {
    data object Idle : UIItemState()
    data object Loading : UIItemState()
    /** Gösterilecek satırlar: ürün grupları + "ilgili ürünler" başlığı. */
    data class Success(val rows: List<SearchRow>) : UIItemState()
    data class Error(val kind: SearchError) : UIItemState()
}

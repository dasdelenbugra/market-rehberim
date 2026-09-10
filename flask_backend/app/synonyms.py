"""Kullanıcının dediği ile katalogun yazdığı arasındaki köprü.

Çözdüğü sorun: kaynak katalogu tek bir terim seçiyor ve kullanıcının yaygın
kullandığı eş anlamlısını hiç tanımıyor. "çöp poşeti" araması `numberOfFound=0`
dönüyordu — katalogda yalnız "çöp torbası" var. Kullanıcı boş ekran görüyor ve
ürünün olmadığını sanıyor; oysa 74 tane var.

Bu bir sıralama sorunu değil, kelime dağarcığı sorunu: alaka mantığı ne kadar
iyi olursa olsun elinde sıralayacak sonuç yok.

Eşleşmeler tahminle değil ölçümle seçildi — 157 yaygın terim canlı kaynağa
sorulup sıfır/zayıf dönenler tespit edildi, sonra her biri için katalogun
kullandığı karşılık doğrulandı. Sayılar aşağıdaki yorumlarda.

Kapsam dışı bilinen sınır: eşleşme **tüm sorguya** bakar, parçasına değil.
"büyük çöp poşeti" yazan kullanıcı hâlâ boş sonuç alır. Parça değiştirme
denenmedi çünkü kısa terimler ("jilet") başka kelimelerin içinde geçip
sorguyu sessizce bozabilir; ölçülmemiş bir kural eklemektense dar kalmak
yeğ. Ölçüm: `tools/check_relevance.py`.

Karşılığı olmadığı ölçülen ve bilerek eklenmeyen terim: "pırasa" — katalogda
gerçekten yok, eş anlamlı sorunu değil. Uydurma bir eşleşme kullanıcıyı
alakasız ürüne götürürdü.
"""
from __future__ import annotations

from app.text import fold

#: kullanıcının terimi -> katalogun terimi.
#: Sağdaki sayı, ölçümde hedefin döndürdüğü sonuç adedi (kaynak: 0 ya da <5).
_SYNONYMS_RAW = {
    "çöp poşeti": "çöp torbası",                        # 0 -> 74
    "ağartıcı": "çamaşır suyu",                          # 0 -> 94
    "jilet": "tıraş bıçağı",                             # 0 -> 94
    "meşrubat": "gazlı içecek",                          # 0 -> 353
    "gargara": "ağız bakım suyu",                        # 0 -> 25
    "kesme şeker": "küp şeker",                          # 0 -> 22
    "çökelek": "lor",                                    # 0 -> 15
    "hıyar": "salatalık",                                # 1 -> 31
    "yağlı kağıt": "pişirme kağıdı",                     # 2 -> 22
    "cam sil": "cam temizleyici",                        # 2 -> 6
    "bulaşık makinesi deterjanı": "bulaşık makinesi tableti",  # 1 -> 52
    "wc kağıdı": "tuvalet kağıdı",                       # katalog terimi
    "çöp poşedi": "çöp torbası",                         # sık yazım hatası
}

#: Arama anahtarı `fold`'lanmış hâlidir: büyük/küçük harf, Türkçe karakter ve
#: boşluk farkları eşleşmeyi kaçırmasın ("Çöp Poşeti" = "cop poseti").
_SYNONYMS = {fold(user_term): catalogue_term for user_term, catalogue_term in _SYNONYMS_RAW.items()}


def canonical(query: str) -> str:
    """Sorguyu katalogun tanıdığı terime çevirir; karşılığı yoksa olduğu gibi döner.

    Idempotenttir: hedef terimlerin kendisi haritada anahtar değil, bu yüzden
    iki kez uygulamak zarar vermez. Sorgu hem kaynağa giderken hem alaka
    hesaplanırken çevrildiği için bu önemli.

    >>> canonical("Çöp Poşeti")
    'çöp torbası'
    >>> canonical("makarna")
    'makarna'
    """
    if not query:
        return query
    return _SYNONYMS.get(fold(query), query)

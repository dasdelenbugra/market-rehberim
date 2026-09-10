"""Kullanıcının dediği ile katalogun yazdığı arasındaki köprü.

Çözdüğü sorun: kaynak katalogu tek bir terim seçiyor ve kullanıcının yaygın
kullandığı eş anlamlısını hiç tanımıyor. "çöp poşeti" araması `numberOfFound=0`
dönüyordu — katalogda yalnız "çöp torbası" var. Kullanıcı boş ekran görüyor ve
ürünün olmadığını sanıyor; oysa 74 tane var.

Bu bir sıralama sorunu değil, kelime dağarcığı sorunu: alaka mantığı ne kadar
iyi olursa olsun elinde sıralayacak sonuç yok.

Eşleşmeler tahminle değil ölçümle seçildi — iki turda 325 yaygın terim (gıda;
sonra kozmetik, kişisel bakım, bebek, ev temizlik) canlı kaynağa sorulup
sıfır/zayıf dönenler tespit edildi, sonra her biri için katalogun kullandığı
karşılık doğrulandı. Sayılar aşağıdaki yorumlarda.

Ölçerken dikkat: `numberOfFound` iyi eşleşmenin kanıtı değil, kaynak bulanık
arama yapıyor. "mandal" 47 sonuç döndürüyor ama ürünler "**Manda** Sütlü
Yoğurt"; "güve" ketçaba, "bardak" çay poşetine düşüyor. Yeni bir eşleşme
eklerken sayıya değil dönen ürün adlarına bakın.

Kapsam dışı bilinen sınır: eşleşme **tüm sorguya** bakar, parçasına değil.
"büyük çöp poşeti" yazan kullanıcı hâlâ boş sonuç alır. Parça değiştirme
denenmedi çünkü kısa terimler ("jilet") başka kelimelerin içinde geçip
sorguyu sessizce bozabilir; ölçülmemiş bir kural eklemektense dar kalmak
yeğ. Ölçüm: `tools/check_relevance.py`.

Bilerek eklenmeyenler — sıfır sonuç veriyorlar ama eş anlamlı sorunu değiller,
market o ürünü gerçekten taşımıyor: pırasa, naftalin, fondöten, göz kremi,
toka, saç lastiği, paspas, süpürge, çöp kovası, elbise askısı, çamaşır
mandalı, ampul, plastik bardak, mama sandalyesi, ütü kolası. İkinci turdaki 23
sıfırın yalnız 6'sı gerçek eş anlamlı boşluğu çıktı; gerisi katalog kapsamı.
Uydurma bir eşleşme kullanıcıyı istemediği ürüne götürür — boş sonuç, yanlış
sonuçtan iyidir.

"sinek ilacı" da eklenmedi: en yakın aday "böcek ilacı" ama dönen ürün bir
vücut losyonu, aranan şey değil. Aynı reyondan olması aynı ürün olduğu
anlamına gelmiyor.
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
    # Kozmetik / kişisel bakım / ev bakım taraması (168 terim)
    "rimel": "maskara",                                  # 0 -> 4
    "tıkanıklık açıcı": "lavabo açıcı",                  # 0 -> 15
    "gider açıcı": "lavabo açıcı",                       # 1 -> 15
    "klozet temizleyici": "tuvalet temizleyici",         # 0 -> 7
    "after shave": "tıraş sonrası",                      # 1 -> 5
    "tıraş losyonu": "tıraş sonrası",                    # 1 -> 5
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

"""Boş sonuç dönen aramaya "bunu mu demek istediniz" önerisi.

Neden dar kapsamlı
------------------
Kaynağın kendi araması sanılandan çok toleranslı. Ölçüldü:

* Diakritiksiz yazım **tamamen** çalışıyor — "sut" ile "süt" birebir aynı 494
  sonucu veriyor. Bu yüzden burada Türkçe karakter düzeltmesi yok; olmayan bir
  soruna çözüm olurdu.
* Yazım hatalarının çoğunu da kaynak yakalıyor: denenen 12 hatalı yazımın 11'i
  sonuç döndürdü ("makrna", "yoğrut", "deterjn" dahil).

Geriye kaynağın da kaçırdığı dar bir kesim kalıyor ("cikoolata" gibi). Bu modül
yalnızca onu hedefler ve **yalnız sonuç boşken** çalışır.

Kelime dağarcığı
----------------
Ayrı bir sözlük tutulmuyor: `price_history` tablosunda biriken gerçek ürün
adlarından çıkarılıyor. Korpus uygulama kullanıldıkça kendiliğinden büyüyor ve
katalogun bugünkü kelimelerini yansıtıyor — elle yazılmış bir liste eskirdi.
Veritabanı henüz boşken (ilk kurulum) eş anlamlı sözlüğünün hedef terimleri
tohum olarak kullanılır, böylece mekanizma ilk günden ölü durmaz.

Susmak konuşmaktan iyidir
-------------------------
Yanlış öneri, öneri vermemekten kötüdür: "pırasa" arayana "pirinç" demek
kullanıcıyı istemediği ürüne yollar. Bu yüzden eşik yüksek tutuldu. Ölçümde
12 vakanın 12'si doğru çıktı; markette gerçekten bulunmayan altı ürünün
(pırasa, süpürge, naftalin, ampul, paspas, fondöten) hiçbirine öneri
üretilmedi.
"""
from __future__ import annotations

import difflib
import re
from collections import Counter

from app import cache, db
from app.synonyms import catalogue_terms
from app.text import fold, lower_tr

#: Dağarcık ne kadar sıklıkla yeniden kurulsun (saniye). Ürün adları gün içinde
#: yavaş değişiyor; her istekte 1400 satır taramanın anlamı yok.
_VOCAB_TTL = 1800.0
_VOCAB_KEY = "suggest:vocab"

_TOKEN = re.compile(r"[0-9a-zçğıöşü]+", re.IGNORECASE)

#: Ürün adında geçen ama ürünü adlandırmayan kelimeler: ambalaj, miktar, sıfat.
#: Dağarcıkta kalsalardı "büyük" ya da "paket" öneri olarak dönebilirdi.
_STOPWORDS = {
    "adet", "paket", "kutu", "litre", "gram", "boy", "sade", "tam", "yagli",
    "buyuk", "kucuk", "orta", "ile", "cesitleri", "gibi", "yeni", "ekstra",
}

#: Bundan kısa kelimeler dağarcığa girmez: üç harfli kelimelerde bir harflik
#: fark bile anlamı tamamen değiştiriyor ("bal"/"tal"/"dal").
_MIN_WORD_LEN = 4

#: `difflib` benzerlik eşiği. Ölçümle seçildi: daha düşük değerlerde "deterjaan"
#: sorgusu "deterjan" yerine "deterjani"ye kayıyordu. Yükseltmek öneri sayısını
#: azaltır ama yanlış öneriyi de engeller — istediğimiz denge bu yönde.
_SIMILARITY = 0.9


def _tokens(raw: str) -> list[str]:
    """Ürün adını kelimelere ayırır; Türkçe harfler korunur."""
    return _TOKEN.findall(raw)


def _build_vocabulary() -> dict[str, tuple[str, int]]:
    """`fold`lanmış kelime -> (gösterim biçimi, katalogdaki sıklığı).

    Anahtar `fold`lu çünkü karşılaştırma diakritikten bağımsız olmalı; değer
    okunur biçimde çünkü kullanıcıya "cikolata" değil "çikolata" gösterilir.
    """
    vocabulary: dict[str, tuple[str, int]] = {}
    counts: Counter = Counter()
    display: dict[str, str] = {}

    sources = list(db.distinct_product_names()) + list(catalogue_terms())
    for text in sources:
        for token in _tokens(text):
            key = fold(token)
            if len(key) < _MIN_WORD_LEN or key in _STOPWORDS or key.isdigit():
                continue
            counts[key] += 1
            display.setdefault(key, lower_tr(token))

    for key, count in counts.items():
        vocabulary[key] = (display[key], count)
    return vocabulary


def vocabulary() -> dict[str, tuple[str, int]]:
    """Önbelleğe alınmış kelime dağarcığı."""
    return cache.get_or_set(_VOCAB_KEY, ttl=_VOCAB_TTL, producer=_build_vocabulary)


def closest(query: str) -> str | None:
    """Sorguya yakın bir katalog kelimesi; yeterince yakın yoksa `None`.

    Yalnız tek kelimelik sorgularda çalışır. Çok kelimeli bir sorguda hangi
    kelimenin yanlış yazıldığı belirsiz ve yanlış kelimeyi düzeltmek sorguyu
    büsbütün bozar; ölçülmemiş bir kural eklemektense sessiz kalmak yeğ.
    """
    tokens = _tokens(query or "")
    if len(tokens) != 1:
        return None

    key = fold(tokens[0])
    if len(key) < _MIN_WORD_LEN:
        return None

    known = vocabulary()
    if key in known:
        # Kelime katalogda var; sorun yazımda değil (ürün o an bulunamamış
        # olabilir). Bilineni "düzeltmek" kullanıcıyı yanıltırdı.
        return None

    matches = difflib.get_close_matches(key, known.keys(), n=3, cutoff=_SIMILARITY)
    if not matches:
        return None

    # Eşit yakınlıktakiler arasından katalogda daha sık geçeni seçilir: daha
    # yaygın kelime, kullanıcının kastetme ihtimali daha yüksek olandır.
    best = max(matches, key=lambda m: known[m][1])
    return known[best][0]

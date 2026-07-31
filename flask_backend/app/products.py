"""Arama sonuçlarını ürüne göre gruplar ve sorguya alakasına göre sıralar.

Çözdüğü sorun: kaynak "muz" sorgusuna 34 satır döndürüyor ve bunlar iki ayrı
eksende karışık geliyor — *hangi ürün* (muz / muzlu gofret / muzlu süt) ve
*hangi market* (aynı muz beş markette). Düz, yalnız fiyata göre sıralı bir liste
bu iki ekseni tek boyuta eziyordu; sonuç olarak "EN UCUZ" rozeti 8 ₺'lik bir
gofrete gidiyor ve "+X ₺" farkı gofretle muzun birbirinin alternatifi olduğunu
ima ediyordu.

İki ayrı iş yapılır:

1. **Gruplama** — aynı ürün adı tek satırda toplanır, marketler onun altında
   karşılaştırılır. Uygulamanın asıl vaadi bu ("aynı ürün, hangi market ucuz").
2. **Alaka sıralaması** — Türkçe ad tamlamalarında **baş isim sonda** durur:
   "Yerli Muz" muzdur, "Muz Aromalı Süt" süttür. Sorgu kelimesi adın baş ismiyse
   ürün aramanın asıl hedefidir; ortada geçiyorsa yalnızca ilgilidir. İstemci bu
   ayrımı kullanıp ilgili ürünleri ayrı bir bölüme koyar.
"""
from __future__ import annotations

import re

from app.models import Item
from app.text import _TR_FOLD

# Alaka katmanları. Sayı küçüldükçe alaka artar; istemci 0'ı ana liste,
# 1'i "ilgili ürünler" bölümü olarak gösterir.
RELEVANCE_HEAD = 0    # sorgu, ürün adının baş ismi ("Yerli Muz")
RELEVANCE_RELATED = 1  # sorgu adın içinde ama baş isim değil ("Muz Aromalı Süt")

# Baş ismi ararken atılacak son ekler: miktar ("1 Kg", "200 Ml") ve ambalaj
# sözcükleri. Bunlar bırakılırsa "Yerli Muz 1 Kg" adının başı "kg" sanılır.
_TRAILING_NOISE = {
    "kg", "g", "gr", "gram", "ml", "cl", "l", "lt", "litre",
    "adet", "li", "lu", "lı", "lü", "x", "paket", "kutu", "pk",
}

_TOKEN = re.compile(r"[0-9a-zçğıöşü]+", re.IGNORECASE)

# Baş isim eşleşmesi tek başına yetmiyor: "Algida Carte d'Or İsveç Karameli Muz"
# adının da başı "muz" ama ürün dondurma. Gerçek veride aranan ürünün adı kısa
# ("Yerli Muz", "Sek Süt", "De Cecco Fettuccine Makarna" — en fazla dört anlamlı
# kelime); sonunda aroma olarak "muz" geçen ürünler belirgin biçimde uzun.
_MAX_HEAD_TOKENS = 4

# Aroma listesi: "Çilek & Muz", "Karamel/Muz". Bağlaçtan hemen sonra gelen
# kelime ürünün kendisi değil, tadıdır.
_FLAVOUR_LIST = re.compile(r"[&/+]\s*[0-9a-zçğıöşü]+\s*$|(?:\bve|\bile)\s+[0-9a-zçğıöşü]+\s*$", re.IGNORECASE)


def _is_flavour_list(name: str) -> bool:
    """Ad, sondaki kelimeyi bir aroma sıralamasının parçası olarak sunuyor mu?

    Miktar eki ("80 Ml") kesilerek bakılır; aksi halde bağlaç hep sondan uzakta
    kalır ve desen hiç tutmaz.
    """
    trimmed = name.translate(_TR_FOLD).lower()
    # Sondaki miktarı at: "... cilek & muz 80 ml" -> "... cilek & muz"
    trimmed = re.sub(r"[\d.,]+\s*[a-z]{0,5}\s*$", "", trimmed).strip()
    return _FLAVOUR_LIST.search(trimmed) is not None


def _tokens(raw: str) -> list[str]:
    """Türkçe harfleri ASCII'ye indirerek kelimelere ayırır.

    `text.fold` tüm dizgiyi tek anahtara ezdiği için burada kullanılamaz;
    kelime sınırlarına ihtiyacımız var.
    """
    return [t.lower() for t in _TOKEN.findall(raw.translate(_TR_FOLD))]


def _content_tokens(raw: str) -> list[str]:
    """Miktar ve ambalaj gürültüsü atılmış kelimeler.

    Sondan başlayarak temizlenir: addaki "2 Li Paket Çay" ifadesinin başındaki
    "li" atılmamalı, yalnız sondaki "1 Kg" gibi ekler atılmalı.
    """
    tokens = _tokens(raw)
    while tokens and (tokens[-1] in _TRAILING_NOISE or tokens[-1].isdigit()):
        tokens.pop()
    return tokens


def _matches(token: str, query_token: str) -> bool:
    """Kelime sorguyu karşılıyor mu?

    Türkçe sondan eklemeli: "muz" sorgusu "Muzlu" kelimesini de karşılamalı.
    Ama izin verilen ek, sorgu kısaldıkça daralmalı — sabit 3 harf sınırıyla
    "su" sorgusu "sucuk"a uyuyor ve sucuk, su araması için ana sonuç oluyordu.

    İki harfli sorguda hiç ek kabul edilmez; daha uzunlarda ek, sorgunun
    kendisinden uzun olamaz ve hiçbir durumda 3 harfi geçemez.
    """
    if token == query_token:
        return True
    if len(query_token) < 3:
        return False
    extra = len(token) - len(query_token)
    return token.startswith(query_token) and 0 < extra <= min(3, len(query_token) - 1)


def relevance(name: str, query: str) -> int | None:
    """Ürün adının sorguya alaka katmanı.

    @return RELEVANCE_HEAD, RELEVANCE_RELATED ya da hiç geçmiyorsa None

    >>> relevance("Yerli Muz 1 Kg", "muz")
    0
    >>> relevance("Muz Aromalı Süt 200 Ml", "muz")
    1
    """
    query_tokens = _tokens(query)
    if not query_tokens:
        return None

    content = _content_tokens(name)
    if not content:
        return None

    # Baş isim adın son içerik kelimesidir. Sorgu birden çok kelimeyse
    # ("yerli muz") son kelimesi baş isimle kıyaslanır.
    if (
        _matches(content[-1], query_tokens[-1])
        and len(content) <= _MAX_HEAD_TOKENS
        and not _is_flavour_list(name)
    ):
        return RELEVANCE_HEAD

    if any(_matches(token, q) for token in content for q in query_tokens):
        return RELEVANCE_RELATED

    # Kaynak bu satırı sorguya karşılık döndürdü ama ad eşleşmiyor (eş anlamlı,
    # marka adı vb.). Elenmez — kaynağın bildiği bizim bilmediğimiz bir ilişki
    # olabilir; yalnızca en sona düşer.
    return RELEVANCE_RELATED


def group(items: list[Item], query: str) -> list[dict]:
    """Aynı ürünü tek gruba toplar, alaka ve fiyata göre sıralar.

    Gruplama anahtarı ürün adının normalleştirilmiş hali: kaynak aynı ürünü
    farklı marketlerde birebir aynı adla döndürüyor ("Yerli Muz 1 Kg" beş
    markette). Bulanık eşleştirme denenmedi — yanlış birleştirilen iki ürün,
    ayrı kalan iki ürüne göre çok daha yanıltıcı olur.
    """
    buckets: dict[str, list[Item]] = {}
    for item in items:
        key = " ".join(_tokens(item.name))
        if not key:
            continue
        buckets.setdefault(key, []).append(item)

    groups = []
    for members in buckets.values():
        members.sort(key=lambda it: it.price_value)
        best = members[0]

        # Görsel: fiyatı en düşük olanınki. Kaynakta bazı satırların görseli boş
        # geliyor, o yüzden ilk dolu olan yedek alınır.
        image = next((m.image for m in members if m.image), "")

        payload = best.to_dict()
        # `or` KULLANMA: RELEVANCE_HEAD == 0 ve falsy'dir; `x or RELATED` en
        # alakalı ürünleri sessizce "ilgili"ye düşürüyordu — tam da düzeltmeye
        # çalıştığımız hatanın aynısı.
        tier = relevance(best.name, query)
        groups.append(
            {
                "name": best.name,
                "image": image,
                "unitPrice": payload["unitPrice"],
                "unit": payload["unit"],
                "bestPrice": best.price,
                "bestMarket": best.source,
                "maxPrice": members[-1].price,
                "marketCount": len(members),
                "relevance": RELEVANCE_RELATED if tier is None else tier,
                "offers": [m.to_dict() for m in members],
            }
        )

    # Önce alaka, sonra fiyat. Alaka eşitken en ucuz ürün üstte kalsın.
    groups.sort(key=lambda g: (g["relevance"], float(g["bestPrice"])))
    return groups

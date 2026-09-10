"""Türkçe metin normalizasyonu — karşılaştırma anahtarları için.

Tek bir yerde durmasının sebebi: `str.lower()` Türkçe'de güvenilir değil.
Python'da "İ".lower() birleşik bir karakter üretir ("i" + U+0307), bu da
"İSTANBUL".lower() == "istanbul" karşılaştırmasını sessizce False yapar.
Şehir anahtarı ve market adı eşleştirmesi bu tuzağa aynı şekilde düşüyordu.
"""
from __future__ import annotations

_TR_FOLD = str.maketrans(
    "İIıŞşĞğÜüÖöÇç",
    "IIiSsGgUuOoCc",
)


#: Türkçe küçük harf çevirisi. `str.lower()` tek başına yetmiyor: "İ" için
#: birleşik bir karakter ("i" + U+0307), "I" için de "i" üretiyor — oysa
#: Türkçe'de "I"nın küçüğü "ı"dır. Gösterime giren metinlerde bu fark görünür
#: ("İçim" -> "i̇çim" gibi bozuk bir çıktı).
_TR_LOWER = str.maketrans("İI", "iı")


def lower_tr(raw: str) -> str:
    """Türkçe kurallarına göre küçük harfe çevirir.

    `fold`'dan farkı: harfleri ASCII'ye indirmez, kelimeyi okunur bırakır.
    Karşılaştırma için `fold`, kullanıcıya gösterim için bu kullanılır.
    """
    return raw.translate(_TR_LOWER).lower()


def fold(raw: str) -> str:
    """Karşılaştırma anahtarı üretir: 'Migros Ticaret A.Ş.' → 'migrosticaretas'.

    Türkçe harfler ASCII'ye indirgenir, küçük harfe çevrilir, harf/rakam
    dışındaki her şey atılır.
    """
    if not raw:
        return ""
    folded = raw.translate(_TR_FOLD).lower()
    return "".join(ch for ch in folded if ch.isalnum())

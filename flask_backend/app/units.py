"""Ürün adından miktar çıkarımı ve birim fiyat hesabı.

Neden gerekli: kaynak yalnız paket fiyatı veriyor ve aramada yan yana duran
"Süt 200 Ml — 22,50 ₺" ile "Süt 1 L — 89,00 ₺" karşılaştırılamıyor. Kullanıcı
"en ucuz" rozetine bakıp aslında litresi iki kat pahalı olanı alabiliyordu.
Birim fiyat bu kıyası mümkün kılar.

Miktar, ürün adının içinde serbest metin olarak geliyor ("Sek Süt 200 Ml",
"Pınar Süt 6 X 200 Ml"). Ayrıştırma bu yüzden **tahmini**: emin olunamayan her
durumda `None` döner. Yanlış bir ₺/L, hiç göstermemekten kötüdür — kullanıcı
buna güvenip alışveriş kararı veriyor.
"""
from __future__ import annotations

import re

# Birim -> (kanonik birim, kanonik birime çevirme çarpanı)
# Sıvılar litreye, katılar kilograma indirgenir; kullanıcı raf etiketlerinde
# bu ikisini görmeye alışkın.
_UNITS: dict[str, tuple[str, float]] = {
    "ml": ("L", 0.001),
    "cl": ("L", 0.01),
    "l": ("L", 1.0),
    "lt": ("L", 1.0),
    "litre": ("L", 1.0),
    "g": ("kg", 0.001),
    "gr": ("kg", 0.001),
    "gram": ("kg", 0.001),
    "kg": ("kg", 1.0),
}

# "200 Ml", "1,5 L", "500gr", "2.5 Kg" — sayı ile birim arasında boşluk olmayabilir.
# Birim adının ardından harf gelmemeli: "1 Litrelik" değil, "500 Gramaj" değil.
_QUANTITY = re.compile(
    r"(\d+(?:[.,]\d+)?)\s*(" + "|".join(sorted(_UNITS, key=len, reverse=True)) + r")(?![a-zçğıöşü])",
    re.IGNORECASE,
)

# Çoklu paket: "6 X 200 Ml", "6x200ml". Toplam miktar çarpımdır.
# Miktarın **hemen solunda** aranır, o yüzden desen dizgi sonuna sabitli ($):
# çarpan ile miktar arasına başka bir şey giriyorsa o çarpan bu miktara ait
# değildir.
_MULTIPACK = re.compile(r"(\d+)\s*[x×]\s*$", re.IGNORECASE)

# Bu sınırların dışındaki değer büyük olasılıkla miktar değil (model numarası,
# yıl, ürün kodu). Örn. "Kahve 2024" ya da "Ampul 100 W" gibi adlar.
_MIN_BASE = 0.001   # 1 ml / 1 g
_MAX_BASE = 100.0   # 100 L / 100 kg


def parse_quantity(name: str) -> tuple[float, str] | None:
    """Ürün adından toplam miktarı çıkarır.

    @return (kanonik birimdeki miktar, "L" | "kg") ya da çıkarılamazsa None

    >>> parse_quantity("Sek Süt 200 Ml")
    (0.2, 'L')
    >>> parse_quantity("Pınar Süt 6 X 200 Ml")
    (1.2, 'L')
    >>> parse_quantity("Çay Bardağı") is None
    True
    """
    if not name:
        return None

    match = _QUANTITY.search(name)
    if match is None:
        return None

    raw_amount, raw_unit = match.group(1), match.group(2).lower()
    try:
        amount = float(raw_amount.replace(",", "."))
    except ValueError:
        return None
    if amount <= 0:
        return None

    unit, factor = _UNITS[raw_unit]
    total = amount * factor

    # Çoklu paket çarpanı yalnız miktarın hemen solundaysa geçerli; adın
    # başındaki alakasız bir sayı ("2 Li Paket Çay 500 G") yanlışlıkla
    # çarpan sanılmasın.
    multi = _MULTIPACK.search(name[: match.start()])
    if multi is not None:
        total *= int(multi.group(1))

    if not _MIN_BASE <= total <= _MAX_BASE:
        return None
    return total, unit


def unit_price(price: float, name: str) -> tuple[str, str] | None:
    """Paket fiyatından birim fiyat.

    @return ("112.50", "L") biçiminde (değer, birim) ya da hesaplanamazsa None.
        Değer metin olarak döner: `Item.price` ile aynı sözleşme, istemci
        biçimlendirmeyi kendi yerelinde yapar.
    """
    if price <= 0:
        return None
    parsed = parse_quantity(name)
    if parsed is None:
        return None

    quantity, unit = parsed
    value = price / quantity

    # Paket fiyatından ucuz bir birim fiyat matematiksel olarak mümkün (5 L'lik
    # bidon) ama absürt büyük bir değer ayrıştırma hatasına işaret eder.
    if value > 1_000_000:
        return None
    return f"{value:.2f}", unit

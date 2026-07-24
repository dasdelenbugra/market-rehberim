"""Süreç içi TTL önbelleği — scraping sonuçları için.

Neden gerekli: `services.search()` her çağrıda dört ulusal markete HTTP isteği
atar ve `optimize_basket()` bunu listedeki her ürün için tekrar eder; on ürünlük
bir sepet kırk scraping isteği demektir. Android istemcisindeki günlük fiyat
takibi de aynı uca yükleniyor.

Kapsam bilinçli olarak dar: yalnız **ulusal scraping** sonuçları önbelleğe alınır.
Crowdsourced fiyatlar SQLite'tan gelir (ucuz) ve tazeliği önemlidir — kullanıcı
raf etiketini gönderdikten hemen sonra kendi katkısını görmeli.

Ek bağımlılık yok; birkaç yüz anahtarlık bir sözlük için Redis kurmaya değmez.
Çok işçili (gunicorn) dağıtımda her işçinin kendi kopyası olur; bu kabul edilebilir,
en kötü ihtimalle önbellek isabet oranı işçi sayısına bölünür.
"""
from __future__ import annotations

import threading
import time
from typing import Any, Callable

_lock = threading.Lock()
_store: dict[str, tuple[float, Any]] = {}

# Önbelleğin dolup taşmaması için üst sınır. Aşılınca süresi en yakında dolacak
# kayıtlar atılır (basit ve öngörülebilir; LRU sayacı tutmaya değmez).
MAX_ENTRIES = 512


def get_or_set(key: str, ttl: float, producer: Callable[[], Any]) -> Any:
    """Anahtar taze ise önbellekten döner, değilse `producer()` çalıştırıp saklar.

    `ttl <= 0` ise önbellek tamamen atlanır (yapılandırmayla kapatmak için).
    """
    if ttl <= 0:
        return producer()

    now = time.monotonic()

    with _lock:
        entry = _store.get(key)
        if entry is not None and entry[0] > now:
            return entry[1]

    # Üretim kilit dışında: yavaş bir scraping çağrısı diğer anahtarların
    # okumalarını bloklamamalı. Aynı anahtar için iki istek yarışırsa ikisi de
    # üretir — tekrarlanan iş, bozuk veri değil; kilidi tutmaktan iyisi.
    value = producer()

    with _lock:
        if len(_store) >= MAX_ENTRIES:
            _evict_locked()
        _store[key] = (now + ttl, value)

    return value


def _evict_locked() -> None:
    """Süresi dolmuşları at; hepsi tazeyse en erken dolacak dörtte biri gitsin."""
    now = time.monotonic()
    expired = [k for k, (expires, _) in _store.items() if expires <= now]
    for k in expired:
        del _store[k]

    if len(_store) < MAX_ENTRIES:
        return

    oldest = sorted(_store.items(), key=lambda kv: kv[1][0])[: MAX_ENTRIES // 4]
    for k, _ in oldest:
        del _store[k]


def clear() -> None:
    """Testler ve yeniden yapılandırma için."""
    with _lock:
        _store.clear()


def size() -> int:
    with _lock:
        return len(_store)

"""SQLite veri katmanı — crowdsourced fiyatlar ve fiyat geçmişi.

Yerel marketlerin web sitesi olmadığından fiyatları kullanıcılar gönderir
(raf etiketi fotoğrafı → OCR). Her gönderim aynı zamanda fiyat geçmişine işlenir,
böylece ürünün zaman içindeki fiyat değişimi grafiği çıkarılabilir.

Basit tutmak için standart kütüphanedeki `sqlite3` kullanılır (ek bağımlılık yok).
"""
from __future__ import annotations

import os
import sqlite3
import threading
from contextlib import contextmanager

from app.text import fold

_DB_PATH = os.getenv("DB_PATH", os.path.join(os.path.dirname(__file__), "..", "data.sqlite"))
_lock = threading.Lock()


@contextmanager
def _conn():
    conn = sqlite3.connect(_DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db() -> None:
    with _lock, _conn() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS crowd_prices (
                id       INTEGER PRIMARY KEY AUTOINCREMENT,
                city     TEXT NOT NULL,
                market   TEXT NOT NULL,
                name     TEXT NOT NULL,
                price    REAL NOT NULL,
                image    TEXT,
                created  TEXT NOT NULL DEFAULT (datetime('now'))
            );
            CREATE INDEX IF NOT EXISTS idx_crowd_lookup
                ON crowd_prices(city, name);

            CREATE TABLE IF NOT EXISTS price_history (
                id       INTEGER PRIMARY KEY AUTOINCREMENT,
                market   TEXT NOT NULL,
                name     TEXT NOT NULL,
                price    REAL NOT NULL,
                created  TEXT NOT NULL DEFAULT (datetime('now'))
            );
            CREATE INDEX IF NOT EXISTS idx_history_lookup
                ON price_history(market, name);

            -- Günde tek nokta: arama her yapıldığında değil, fiyat o gün ilk kez
            -- görüldüğünde kayıt düşsün. `INSERT OR IGNORE` bu indeksle çalışır,
            -- böylece tekilleştirme için ayrıca SELECT atmaya gerek kalmaz.
            CREATE UNIQUE INDEX IF NOT EXISTS idx_history_daily
                ON price_history(market, name, date(created));

            -- Sonuçsuz kalan aramalar. Eş anlamlı sözlüğünü (app/synonyms.py)
            -- tahmin listesiyle değil gerçek kullanımla beslemek için.
            -- Olay olay değil terim başına sayaç tutulur: tablo sınırlı büyür,
            -- istenen zaten sıklık, ve kimin ne aradığı hiç kaydedilmez.
            CREATE TABLE IF NOT EXISTS search_gaps (
                term_key   TEXT PRIMARY KEY,
                term       TEXT NOT NULL,
                results    INTEGER NOT NULL,
                hits       INTEGER NOT NULL DEFAULT 1,
                first_seen TEXT NOT NULL DEFAULT (datetime('now')),
                last_seen  TEXT NOT NULL DEFAULT (datetime('now'))
            );
            """
        )


def add_crowd_price(city: str, market: str, name: str, price: float, image: str | None) -> None:
    """Şehir anahtarı `fold()` ile üretilir — `registry.city_key()` ile aynı işlev.

    `str.lower()` kullanılamaz: "İstanbul".lower() birleşik bir karakter üretir
    ("i" + U+0307) ve `registry`nin ürettiği "istanbul" anahtarıyla eşleşmez;
    yerel market fiyatları aramada sessizce kaybolurdu. Bkz. `app.text`.
    """
    with _lock, _conn() as conn:
        conn.execute(
            "INSERT INTO crowd_prices(city, market, name, price, image) VALUES(?,?,?,?,?)",
            (fold(city), market.strip(), name.strip(), float(price), image),
        )
        # `OR REPLACE`: `idx_history_daily` günde tek nokta dayatıyor ve düz
        # INSERT, aynı ürüne aynı gün ikinci fiyat gönderildiğinde isteği
        # IntegrityError ile düşürüyordu. Ulusal kaynağın aksine burada **son**
        # gönderim kazanır: kullanıcı çoğu zaman hatalı okumasını düzeltiyordur.
        conn.execute(
            "INSERT OR REPLACE INTO price_history(market, name, price) VALUES(?,?,?)",
            (market.strip(), name.strip(), float(price)),
        )


def latest_crowd_prices(city: str, name: str) -> list[dict]:
    """Bir şehir+ürün için her yerel marketin EN GÜNCEL fiyatını döndürür.

    Şehir anahtarı için bkz. `add_crowd_price` — yazma ve okuma aynı `fold()`
    işlevini kullanmak zorunda.
    """
    with _lock, _conn() as conn:
        rows = conn.execute(
            """
            SELECT market, name, price, image, MAX(created) AS created
            FROM crowd_prices
            WHERE city = ? AND name LIKE ?
            GROUP BY market, name
            ORDER BY price ASC
            """,
            (fold(city), f"%{name.strip()}%"),
        ).fetchall()
        return [dict(r) for r in rows]


def price_history(market: str, name: str, limit: int = 50) -> list[dict]:
    """Tek bir market+ürünün zaman içindeki fiyat noktaları.

    Eşleşme **tam**, `LIKE '%ad%'` değil: joker eşleşme "süt" sorgusuna
    "Sek Süt 200 Ml", "Danone Çilekli Süt" gibi farklı ürünlerin fiyatlarını
    tek seriye karıştırıyordu. Karışık ürünlerden çizilen bir grafik fiyat
    geçmişi değil, gürültü. İstemci zaten ürünün tam adını gönderiyor.
    """
    with _lock, _conn() as conn:
        rows = conn.execute(
            """
            SELECT price, created FROM price_history
            WHERE market = ? AND name = ?
            ORDER BY created ASC
            LIMIT ?
            """,
            (market.strip(), name.strip(), limit),
        ).fetchall()
        return [dict(r) for r in rows]


#: Aşırı uzun girdiler tabloyu şişirmesin; gerçek arama terimi bu sınırın altında.
_MAX_TERM_LEN = 80


def record_search_gap(term: str, results: int) -> None:
    """Sonuç bulamayan bir aramayı sayar.

    Kaydedilen: terimin kendisi, kaç sonuç döndüğü ve kaç kez arandığı.
    Kaydedilmeyen: kim aradığı, ne zaman aradığı (yalnız ilk/son görülme
    tarihi), hangi cihazdan geldiği. Amaç bir kullanıcıyı değil, kataloğun
    kör noktasını takip etmek.

    Şehir bilerek tutulmuyor: eş anlamlı boşluğu kelime dağarcığı sorunu,
    şehirden bağımsız. Şehir eklemek satırları böler ve sıklığı gizlerdi.
    """
    cleaned = " ".join(term.split())[:_MAX_TERM_LEN]
    key = fold(cleaned)
    if not key:
        return

    with _lock, _conn() as conn:
        conn.execute(
            """
            INSERT INTO search_gaps(term_key, term, results)
            VALUES(?,?,?)
            ON CONFLICT(term_key) DO UPDATE SET
                hits      = hits + 1,
                results   = excluded.results,
                last_seen = datetime('now')
            """,
            (key, cleaned, int(results)),
        )


def search_gaps(limit: int = 50) -> list[dict]:
    """En çok aranıp en az sonuç veren terimler — yeni eş anlamlı adayları."""
    with _lock, _conn() as conn:
        rows = conn.execute(
            """
            SELECT term, term_key, results, hits, first_seen, last_seen
            FROM search_gaps
            ORDER BY hits DESC, results ASC, last_seen DESC
            LIMIT ?
            """,
            (limit,),
        ).fetchall()
        return [dict(r) for r in rows]


def distinct_product_names(limit: int = 5000) -> list[str]:
    """Şimdiye kadar görülmüş farklı ürün adları.

    `price_history` her aramada kaynaktan geleni işlediği için katalogun
    kullandığı gerçek adların birikmiş hâlini tutuyor. `app.suggest` bunu
    kelime dağarcığı olarak kullanıyor — ayrı bir sözlük tutmaya gerek yok,
    korpus uygulama kullanıldıkça kendiliğinden büyüyor.
    """
    with _lock, _conn() as conn:
        rows = conn.execute(
            "SELECT DISTINCT name FROM price_history LIMIT ?", (limit,)
        ).fetchall()
        return [r["name"] for r in rows if r["name"]]


def clear_search_gaps() -> None:
    """Boşluk sayaçlarını sıfırlar.

    Sözlüğe yeni eşleşmeler eklendikten sonra çalıştırılır: eski sayaçlar
    kalırsa çözülmüş terimler listenin başında durmaya devam eder ve yeni
    sinyali gizler.
    """
    with _lock, _conn() as conn:
        conn.execute("DELETE FROM search_gaps")


def record_daily_prices(rows: list[tuple[str, str, float]]) -> None:
    """Ulusal kaynaktan taze çekilen fiyatları geçmişe işler.

    `rows`: (market, ürün adı, fiyat) üçlüleri. Aynı market+ürün için o gün zaten
    bir kayıt varsa `idx_history_daily` sayesinde sessizce atlanır — grafik günde
    tek nokta ilerler, arama sayısından bağımsız.

    Tek bir işlemde (transaction) yazılır: bir arama onlarca satır üretebiliyor.
    """
    if not rows:
        return
    with _lock, _conn() as conn:
        conn.executemany(
            "INSERT OR IGNORE INTO price_history(market, name, price) VALUES(?,?,?)",
            [(m.strip(), n.strip(), float(p)) for m, n, p in rows],
        )

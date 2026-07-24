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
            """
        )


def add_crowd_price(city: str, market: str, name: str, price: float, image: str | None) -> None:
    with _lock, _conn() as conn:
        conn.execute(
            "INSERT INTO crowd_prices(city, market, name, price, image) VALUES(?,?,?,?,?)",
            (city.strip().lower(), market.strip(), name.strip(), float(price), image),
        )
        conn.execute(
            "INSERT INTO price_history(market, name, price) VALUES(?,?,?)",
            (market.strip(), name.strip(), float(price)),
        )


def latest_crowd_prices(city: str, name: str) -> list[dict]:
    """Bir şehir+ürün için her yerel marketin EN GÜNCEL fiyatını döndürür."""
    with _lock, _conn() as conn:
        rows = conn.execute(
            """
            SELECT market, name, price, image, MAX(created) AS created
            FROM crowd_prices
            WHERE city = ? AND name LIKE ?
            GROUP BY market, name
            ORDER BY price ASC
            """,
            (city.strip().lower(), f"%{name.strip()}%"),
        ).fetchall()
        return [dict(r) for r in rows]


def price_history(market: str, name: str, limit: int = 50) -> list[dict]:
    with _lock, _conn() as conn:
        rows = conn.execute(
            """
            SELECT price, created FROM price_history
            WHERE market = ? AND name LIKE ?
            ORDER BY created ASC
            LIMIT ?
            """,
            (market.strip(), f"%{name.strip()}%", limit),
        ).fetchall()
        return [dict(r) for r in rows]


def record_history(market: str, name: str, price: float) -> None:
    """Scraping/mock sonuçlarını da geçmişe işler (grafik için veri birikir)."""
    with _lock, _conn() as conn:
        conn.execute(
            "INSERT INTO price_history(market, name, price) VALUES(?,?,?)",
            (market.strip(), name.strip(), float(price)),
        )

"""Geliştirme sunucusu giriş noktası.

Çalıştırma:
    python app.py

Üretim için (Windows dışı):
    gunicorn "app:app" --bind 0.0.0.0:5454
"""
from app import create_app
from config import Config

app = create_app()

if __name__ == "__main__":
    app.run(host=Config.HOST, port=Config.PORT, debug=Config.DEBUG)
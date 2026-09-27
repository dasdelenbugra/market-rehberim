"""WSGI giriş noktası — sunucunun uygulamayı bulduğu yer.

Çalıştırma:
    python wsgi.py                                    # geliştirme sunucusu
    gunicorn wsgi:app --bind 0.0.0.0:5454             # üretim (Windows dışı)

Neden `app.py` değil de `wsgi.py`
---------------------------------
Bu dosyanın adı `app.py` iken `gunicorn app:app` üretimde şu hatayla düşüyordu:

    AttributeError: module 'app' has no attribute 'app'. Did you mean: 'api'?

Sebep, aynı dizinde hem `app.py` dosyası hem de `app/` paketi bulunması.
`import app` ikisine birden işaret ediyor ve Python **paketi** seçiyor; paketin
içinde `app` adında bir nitelik yok (`create_app` ve `api` var), gunicorn da
aradığını bulamıyor.

Yerelde fark edilmiyordu: `python app.py` bir dosyayı doğrudan çalıştırmak
demek, içe aktarma çözümlemesi devreye girmiyor. Yani hata ancak üretimde
ortaya çıkan cinstendi.

Dosyayı `wsgi.py` yapmak çakışmayı ortadan kaldırıyor — `import wsgi` tek bir
şeye işaret ediyor. `wsgi` adı ayrıca bu dosyanın ne olduğunu da söylüyor:
sunucu ile uygulama arasındaki standart arayüz.
"""
from app import create_app
from config import Config

app = create_app()

if __name__ == "__main__":
    app.run(host=Config.HOST, port=Config.PORT, debug=Config.DEBUG)

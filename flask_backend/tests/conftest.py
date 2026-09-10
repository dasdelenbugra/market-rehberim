"""Ortak test kurulumu.

Ortam değişkenleri **import sırasında** ayarlanmak zorunda: `config.Config` ve
`app.db` modül yüklenirken okuyor. pytest `conftest.py`'yi test modüllerinden
önce yüklediği için doğru yer burası — daha önce her test dosyasının başında
tekrarlanıyordu ve dosyalar ayrı çalıştırıldığında kurulum kaçabiliyordu.
"""
import os
import tempfile

os.environ["USE_MOCK"] = "true"
os.environ["DB_PATH"] = os.path.join(tempfile.gettempdir(), "mr_test.sqlite")

# Her test oturumunda temiz başla: testler crowdsourced satır yazıyor ve
# önceki oturumdan kalanlar arama sonuçlarını kirletirdi.
if os.path.exists(os.environ["DB_PATH"]):
    os.remove(os.environ["DB_PATH"])

import pytest

from app import create_app


@pytest.fixture()
def client():
    app = create_app()
    app.config.update(TESTING=True)
    return app.test_client()


@pytest.fixture()
def tmp_db():
    """Boş bir `search_gaps` tablosuyla başlatır.

    Bu testler tablonun tamamına bakıyor (kaç satır, hangi sırada); başka bir
    testin bıraktığı kayıt sonucu sessizce değiştirirdi.
    """
    from app import db

    db.init_db()
    db.clear_search_gaps()
    return db

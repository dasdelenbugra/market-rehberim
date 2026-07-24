"""Uygulama fabrikası (application factory).

`create_app()` yapılandırılmış bir Flask örneği döndürür. Bu desen testleri ve
çoklu ortam yapılandırmasını kolaylaştırır.
"""
import logging

from flask import Flask
from flask_cors import CORS

from config import Config
from app.routes import api
from app import db


def create_app(config: type[Config] = Config) -> Flask:
    app = Flask(__name__)
    app.config.from_object(config)

    db.init_db()

    logging.basicConfig(
        level=logging.DEBUG if config.DEBUG else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    # Android istemcisi ve olası bir web arayüzü için CORS'u aç
    CORS(app)

    app.register_blueprint(api)
    return app
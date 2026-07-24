# Yayın (Release) Rehberi — Market Rehberim

Uygulamayı Play Store'a yüklemek için adımlar.

## 1. Backend'i buluta deploy edin
Yayınlanan uygulama `localhost`'a erişemez. Önce backend'i genel bir adrese alın
(bkz. [`flask_backend/README.md`](flask_backend/README.md) → Bulut Deploy). Örn:
`https://market-rehberim-backend.onrender.com`

## 2. Backend adresini üretim için ayarlayın
`local.properties` içine (sonu `/` ile):
```properties
backend.baseUrl=https://market-rehberim-backend.onrender.com/
```
> Üretim adresi HTTPS olmalıdır; ağ güvenliği yapılandırması şifresiz trafiğe yalnızca
> yerel geliştirme adreslerinde (10.0.2.2, localhost) izin verir.

## 3. Yayın imzalama anahtarı oluşturun
```bash
keytool -genkeypair -v -keystore market-rehberim.keystore \
  -alias market -keyalg RSA -keysize 2048 -validity 10000
```
Kök dizinde `keystore.properties` oluşturun (bu dosya `.gitignore`'da, repoya eklenmez):
```properties
storeFile=../market-rehberim.keystore
storePassword=***
keyAlias=market
keyPassword=***
```
> `keystore.properties` yoksa release derlemesi geçici olarak debug imzasıyla çalışır
> (yalnızca test amaçlı; Play Store'a debug imzalı yüklenemez).

## 4. Yayın paketini üretin
Play Store **AAB** ister:
```bash
./gradlew bundleRelease
# çıktı: app/build/outputs/bundle/release/app-release.aab
```
Test için imzalı APK:
```bash
./gradlew assembleRelease
# çıktı: app/build/outputs/apk/release/app-release.apk
```
R8 (kod küçültme + karıştırma) ve kaynak küçültme etkindir.

## 5. Play Console
1. [play.google.com/console](https://play.google.com/console) → Uygulama oluştur.
2. AAB'yi yükleyin (Dahili test → Üretim).
3. Mağaza kaydını doldurun: [`STORE_LISTING.md`](STORE_LISTING.md).
4. Gizlilik politikası URL'si girin: [`PRIVACY.md`](PRIVACY.md) içeriğini yayımlayın.
5. Veri güvenliği formunu doldurun (kişisel veri toplanmaz).
6. İçerik derecelendirme anketini tamamlayın.

## Sürüm yükseltme
Her yeni yayında `app/build.gradle.kts` içinde `versionCode` (tam sayı, artan) ve
`versionName` değerlerini güncelleyin.

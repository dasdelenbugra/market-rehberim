# Fiyat Verisi: Kaynak ve Hukuki Zemin

Bu belge, Market Rehberim'in ulusal market fiyatlarını nereden aldığını ve neden
doğrudan scraping'den vazgeçildiğini açıklar.

---

## 1. Neden doğrudan scraping bırakıldı

Projenin ilk sürümü Migros, A101, ŞOK ve CarrefourSA sitelerini doğrudan
kazıyordu (`app/scrapers/`). Türkiye'de bunun asıl riski ceza değil, **ticari
uyuşmazlık**:

| Risk | Dayanak | Pratik sonuç |
|------|---------|--------------|
| Haksız rekabet | TTK m.55/1-(c) — başkasının emeğinden haksız yararlanma | İhtarname, tazminat davası |
| Veritabanı hakkı | FSEK Ek m.8 — veritabanı yapımcısının *sui generis* hakkı | Önemli kısmın sistematik alınmasına itiraz |
| Sözleşme ihlali | Sitelerin kullanım şartları scraping'i açıkça yasaklar | Erişim engeli, IP/hesap bloğu |
| Teknik kırılganlık | HTML seçicileri ve JS render'ı sürekli değişir | Sessizce boş sonuç, mock'a düşme |

Play Store'a çıkacak, kullanıcı toplayan bir üründe bu riski taşımaya değmez.
Üstelik gerek de yok: aynı veri zaten kamuya açılmış durumda.

---

## 2. Kullanılan kaynak: marketfiyati.org.tr

**İşleten:** TÜBİTAK BİLGEM (TCMB ve Ticaret Bakanlığı ile birlikte yürütülen
"Market Fiyatı Platformu" projesi).

**Kapsam:** A101, BİM, CarrefourSA, HAKMAR, Migros, Türkiye Tarım Kredi
Kooperatifleri, ŞOK — yaklaşık 50 bin ürün, şube (depot) düzeyinde fiyat.

**Neden kullanımı meşru:**

1. **Veri mevzuat gereği toplanıyor.** *Perakende Ticarette Uygulanacak İlke ve
   Kurallar Hakkında Yönetmelik* değişikliğiyle, hızlı tüketim malları satan ve
   şube sayısı 200'den fazla olan zincir mağazalar, satışa sundukları ürünlere
   ve şubelerine ilişkin verileri Bakanlıkça belirlenen sisteme aktarmakla
   **yükümlü** tutuldu.
2. **Kamuya açılması amaçlanmış.** Aynı düzenleme, bu verilerin "ilgili kurum,
   kuruluş ve kamuoyuyla paylaşılabileceğini" ve amacın açıkça *"tüketicinin
   fiyat karşılaştırması yapabilmesine olanak sağlamak"* olduğunu söylüyor.
   Bizim yaptığımız tam olarak bu.
3. **Erişim kısıtlanmamış.** `marketfiyati.org.tr/robots.txt` → `Allow: /`
   (yalnız `/404` hariç). Kimlik doğrulama, ücret veya kota yok.

**Uç nokta:**

```
POST https://api.marketfiyati.org.tr/api/v2/searchByOnSaleProducts
{ "keywords": "süt", "latitude": 41.0082, "longitude": 28.9784,
  "distance": 15, "size": 24, "pages": 0 }
```

### Sorumlu kullanım kuralları

Kaynak resmî bir geliştirici sözleşmesi yayımlamadığı için, iyi niyetli
kullanımın sınırlarını kendimiz çiziyoruz:

- **Önbellek zorunlu.** `SEARCH_CACHE_TTL=21600` (6 saat). Kaynak zaten günlük
  tazeleniyor; daha sık sormak kimseye fayda sağlamaz.
- **Toplu indirme yok.** Yalnız kullanıcının aradığı ürün sorgulanır; katalog
  kopyalanmaz.
- **Kendimizi tanıtıyoruz.** `CLIENT_USER_AGENT` gerçek bir uygulama adı ve
  iletişim bilgisi taşır; tarayıcı taklidi yapmayız.
- **Kaynak gösterimi.** Uygulamada fiyatların kaynağı ve son güncelleme zamanı
  kullanıcıya gösterilmeli.

---

## 3. Tazelik: "anlık fiyat" gerçekçi değil

Ne resmî kaynak ne de scraping anlık fiyat verebilir — marketler veriyi günlük
mertebede besliyor. Doğru tasarım anlık fiyat vaat etmek değil, **ne kadar taze
olduğunu dürüstçe göstermek**:

- `MarketFiyatiSource.fetch_with_meta()` fiyatlarla **aynı istekte** en yeni
  indeksleme zamanını döndürür; `/search` bunu `X-Data-Updated` yanıt başlığına
  koyar (gövde şeması `List<Item>` sabit sözleşme olduğundan başlıkla taşınır).
- İstemci bu başlığı okuyup "Son güncelleme: 25 Tem 03:10" satırıyla gösterir.
- Fiyat düşüşü bildirimi (WorkManager) günde bir kez çalışsın; daha sık
  çalıştırmak yeni veri getirmez, sadece pil harcar.

---

## 4. Diğer resmî kaynaklar

| Kaynak | İçerik | Kullanım |
|--------|--------|----------|
| [Hal Kayıt Sistemi](https://www.hal.gov.tr/Sayfalar/1_FiyatDetaylari.aspx) | Yaş meyve-sebze hal fiyatları, tarih bazlı | Manav/sebze kategorisi için referans fiyat |
| [TÜİK Veri Portalı](https://data.tuik.gov.tr/) | Madde bazlı ortalama fiyat ve TÜFE | Ürün değil **trend** verisi; "geçen yıla göre %X" tarzı içgörü |
| Crowdsourced (kendi verimiz) | Kullanıcının raf etiketi OCR'ı | Yerel marketler — ulusal kaynakta yok |

---

## 5. Yerel marketler

marketfiyati.org.tr yalnız 200+ şubeli zincirleri kapsıyor; Tokat'taki Erenler
veya Mopaş orada yok. Bunlar için iki meşru yol var:

1. **Crowdsourced** (mevcut çözüm) — kullanıcı raf etiketini fotoğraflar, OCR ile
   fiyat girilir. Veri kullanıcının kendi gözlemi; kimsenin veritabanı değil.
2. **Açık izin** — yerel zincirle yazılı anlaşma yapıp veri akışı kurmak.
   `app/scrapers/erenler.py` bu senaryo için iskelet olarak duruyor; **izin
   alınmadan açılmamalı**.

---

## 6. Kodda nerede

```
app/sources/marketfiyati.py   Kaynak istemcisi + toleranslı ayrıştırıcı
app/sources/__init__.py       NATIONAL_SOURCE — servislerin kullandığı tekil örnek
app/registry.py               CITY_COORDS — şehir merkezi koordinatları
app/text.py                   Türkçe normalizasyon (fold)
app/scrapers/                 EMEKLİ. ENABLE_LEGACY_SCRAPERS=false ile kapalı
tools/check_marketfiyati.py   Canlı doğrulama scripti
tests/test_marketfiyati.py    Ayrıştırıcı testleri (ağa çıkmaz)
```

Kaynak şeması değişirse ilk adım:

```bash
python tools/check_marketfiyati.py süt --city istanbul --raw
```

Çıktıdaki alan adlarını `app/sources/marketfiyati.py` içindeki `_first(...)`
adaylarıyla karşılaştır; eksik olanı listeye ekle.

---

> **Not:** Bu belge hukuki mütalaa değil, mühendislik gerekçesidir. Uygulamayı
> ticarileştirmeden (reklam, abonelik) önce bir avukata danışmakta fayda var —
> özellikle marka adlarının (Migros, A101…) uygulama içinde ve mağaza
> görselinde kullanımı açısından.

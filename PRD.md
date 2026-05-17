# Ürün Gereksinimleri Dokümanı (PRD)

**Proje Adı:** Akıllı Sağlık ve Erişilebilirlik Asistanı (ASEA)  
**Asistan Aktivasyon Adı (Wake Word):** "Ela"  
**Hedef Platform:** Mobil (Öncelikli Android - Arka plan servisleri nedeniyle)  
**Geliştirme Aracı:** Google Antigravity IDE  
**Sürüm:** 1.2.0  
**Tarih:** Mayıs 2026  

---

## 1. Ürün Genel Bakışı (Overview)
Bu proje; Parkinson ve kaygı bozukluğu teşhisi olan bir kullanıcının günlük yaşam kalitesini artırmak, ilaç ve hastalık geçmişini takip etmek ve kriz anlarında çevreye sesli bilgilendirme yaparak güvenliğini sağlamak amacıyla tasarlanmış, **yapay zeka tabanlı kişisel bir erişilebilirlik uygulamasıdır.**

**Kritik Geliştirme Prensibi:** Uygulama içerisindeki hiçbir metin, acil durum senaryosu, ilaç saati veya kullanıcı bilgisi kodun içine gömülü (**hard-coded**) olmayacaktır. Tüm içerikler cihaz içi yerel bir veritabanından dinamik olarak okunacak; kullanıcı arayüzden veya sesli komutlarla bu verileri dilediği zaman güncelleyebilecektir.

---

## 2. Temel Kullanıcı Senaryoları (Use Cases)

* **Senaryo A (Dinamik Bilgi Güncelleme):** Kullanıcı, doktorunun verdiği yeni bir ilacı veya değişen kriz protokolünü uygulamadaki ayarlar ekranından ya da asistanına *"Ela, akşam ilacımı X olarak değiştir"* diyerek günceller. Veritabanı anında yenilenir.
* **Senaryo B (Kriz Anı / Çevre Bilgilendirme):** Kullanıcı kriz anında sadece *"Ela"* diyerek seslenir. Telefon, ekranı kapalı olsa dahi uyanır ve o an **veritabanında güncel olarak saklanan** açıklama metnini (ambulans çağrılmaması gerektiği, stres altında durumun kötüleşeceği bilgisini) yüksek sesle çevreye okur.
* **Senaryo C (Acil Arama):** Kullanıcı sesli komutla (*"Ela, [İsim]'i ara"*) rehberindeki bir yakınının aranmasını talep eder; asistan dinamik olarak yerel rehber verilerine erişip aramayı başlatır.

---

## 3. Fonksiyonel Gereksinimler (Functional Requirements)

Aşağıdaki tablo, Antigravity ajanının mimariyi kurarken tüm verileri dinamik bir veri katmanına bağlaması gerektiğini gösteren modülleri içermektedir:

| Modül Kodu | Özellik Adı | Veri Yapısı (Dinamik/DB) | Öncelik |
| :--- | :--- | :--- | :--- |
| **F-01** | Her An Aktif Dinleme (Always-on) | Aktivasyon kelimesi DB'den okunur (Varsayılan: "Ela"). | En Yüksek |
| **F-02** | Kriz Anı Sesli Açıklama (TTS) | Metin şablonu DB'de tutulur; arayüzden veya sesle düzenlenebilir. | En Yüksek |
| **F-03** | Sesli Rehber Araması | Cihaz rehberiyle dinamik senkronizasyon. | Yüksek |
| **F-04** | Sağlık Bilgi Deposu (LLM) | İlaç listesi, dozajlar ve tıbbi geçmiş Room/SQLite DB'de şifreli tutulur. | Yüksek |
| **F-05** | Kullanıcı Düzenleme Arayüzü | DB'deki tüm verilerin manuel olarak değiştirilebileceği Ayarlar ekranı. | Yüksek |

---

### 3.1. Detaylı Özellik Seti ve Veritabanı İlişkisi

#### F-01: Ekran Kapalıyken Sesli Aktivasyon (Ela)
* **Gereksinim:** Uygulama, Android *Foreground Service* kullanarak sürekli dinleme modunda olmalıdır.
* **Dinamik Yapı:** Asistanı uyandıracak kelime veritabanında bir parametre olarak tutulacaktır. Sistem başlangıçta **"Ela"** tetikleyicisine duyarlı olacaktır.

#### F-02: Çevresel Bilgilendirme Protokolü (Text-to-Speech)
* **Gereksinim:** Kullanıcı sesli komut verdiğinde (örn: *"Durumu Açıkla"*), cihaz sesi maksimuma getirilir ve veritabanındaki güncel metin okunur.
* **Varsayılan DB Metni (İlklendirmede yüklenecek, sonradan değiştirilebilir):**
    > "Merhaba, ben şu an geçici bir Parkinson/stres atağı geçiriyorum. Lütfen panik yapmayın. Ambulans çağrılmasına veya hastaneye gidilmesine gerek yoktur. Çevredeki aşırı stres ve kalabalık durumumu daha da zorlaştırabilir. Sadece sakinleşene kadar güvenli ve sessiz bir alanda kalmama yardımcı olabilirsiniz. Teşekkür ederim."
* **Dinamik Yönetim:** Kullanıcı bu metne yeni eklemeler yaparsa, kod seviyesinde hiçbir şeye dokunulmadan doğrudan DB'deki ilgili hücre güncellenecektir.

#### F-03: Sesli Arama ve Rehber Entegrasyonu
* **Gereksinim:** `READ_CONTACTS` ve `CALL_PHONE` izinleri kullanılır. Dinamik olarak rehber eşleştirmesi yapılır, sabit bir numara koda yazılmaz.

#### F-04: Sağlık Geçmişi ve Konuşma Motoru (LLM ve DB Entegrasyonu)
* **Gereksinim:** Gemini API, uygulamanın ana mantığını yürütecektir.
* **Veri Akışı:** Kullanıcı ilaç listesini veya geçmişini sorguladığında, LLM doğrudan yerel `Room/SQLite` veritabanına sorgu atarak güncel verileri çeker. Kullanıcı sesli olarak *"Ela, yeni bir ilaca başladım, adı Y"* dediğinde, LLM bu komutu anlamlandırıp DB'ye yeni bir satır eklemelidir.

---

## 4. Technical & Data Stack (Teknik Mimari)

Antigravity IDE içinde projenin iskeleti kurulurken aşağıdaki veri tabanı tabloları ve mimari standartlar temel alınacaktır:

### 4.1. Veritabanı Tablo Tasarımları (SQLite / Room DB)

## KullaniciAyarlari Tablosu
* id: INTEGER (PK)
* wake_word: TEXT (Varsayılan: "Ela")
* emergency_text: TEXT (Çevreye okunacak dinamik metin)
* volume_level: INTEGER (Varsayılan: 100)

## IlacTakip Tablosu
* id: INTEGER (PK)
* ilac_adi: TEXT
* dozaj: TEXT
* hatirlatma_saati: TEXT (Örn: "20:00")
* aktif_mi: BOOLEAN

## SaglikGecmisi Tablosu
* id: INTEGER (PK)
* kayit_tipi: TEXT (Örn: "Teşhis", "Alerji", "Doktor Notu")
* icerik: TEXT
* kayit_tarihi: TEXT

### 4.2. Teknolojik Yapı
* **Geliştirme Dili:** Yerel Android (Kotlin) — Donanım, TTS ve kararlı arka plan servisleri için.
* **Veritabanı Katmanı:** Cihaz içi şifrelenmiş **Room Database** (SQLCipher entegrasyonu ile).
* **Yapay Zeka:** Yerel veritabanı verilerini okuyup yorumlayabilen Gemini API bağlantısı.

---

## 5. Gizlilik, Güvenlik ve Esneklik Gereksinimleri
* **Sıfır Hard-Code Politikası:** Uygulama içinde API anahtarları hariç hiçbir operasyonel metin string olarak statik tutulmayacaktır. Her şey veri tabanından beslenecektir.
* **Veri Güvenliği:** Sağlık verileri Room DB içinde şifreli (Encrypted) olarak saklanacak, internete sızdırılmayacaktır.

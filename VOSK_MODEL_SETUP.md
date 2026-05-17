# Vosk Modeli Kurulum Rehberi

## Neden Gerekli?

ASEA, internet bağlantısı olmadan da çalışabilmesi için **offline** konuşma tanıma kullanır.
Bu amaçla [Vosk](https://alphacephei.com/vosk/) motoru seçilmiştir.
Model, cihaza yerel olarak yüklenir; hiçbir ses verisi buluta gönderilmez.

## Model İndirme

1. Aşağıdaki bağlantıdan **Türkçe küçük modeli** indirin (~40 MB):
   👉 https://alphacephei.com/vosk/models → `vosk-model-small-tr-0.3`

2. İndirilen `.zip` dosyasını açın.

## Modeli Projeye Ekleme

3. Açılan klasörü (içinde `am/`, `conf/`, `graph/` gibi dizinler bulunur) şu yola kopyalayın:

```
app/
└── src/
    └── main/
        └── assets/
            └── vosk-model-small-tr/   ← klasörü buraya koyun
                ├── am/
                ├── conf/
                ├── graph/
                └── ...
```

> ⚠️ Klasör adı `vosk-model-small-tr` olmalıdır. Bu isim `WakeWordService.kt` içindeki
> `StorageService.unpack(...)` çağrısıyla eşleşmelidir.

## İlk Çalıştırma

- Uygulama **ilk kez başlatıldığında** `StorageService` bu model klasörünü
  `filesDir/vosk_model` altına kopyalar (~5-10 sn sürebilir).
- Sonraki açılışlarda model zaten kopyalanmış olduğundan anında yüklenir.

## Alternatif: Büyük Model

Daha yüksek doğruluk istiyorsanız `vosk-model-tr-0.3` (~1.8 GB) modelini kullanabilirsiniz.
Ancak bu model cihazda daha fazla yer kaplar ve yüklenmesi daha uzun sürer.

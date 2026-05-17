package com.example.asea.service

import android.util.Log

/**
 * Ham Vosk metnini [VoiceCommand]'a dönüştüren kural tabanlı ön-işleyici.
 *
 * Öncelik sırası:
 *   1. Acil durum anahtar kelimeleri (en yüksek öncelik)
 *   2. Arama komutları
 *   3. İlaç ekleme
 *   4. Sağlık notu
 *   5. Listeleme komutları
 *   6. Tanınamayan → Gemini'ye ilet
 *
 * NOT: Bu parser yalnızca hızlı, yerel bir ön-filtreleme sağlar.
 * Belirsiz ya da karmaşık komutlar [VoiceCommand.Bilinmiyor] ile
 * Gemini entegrasyonuna devredilir.
 */
object CommandParser {

    private const val TAG = "CommandParser"

    // Tetikleyici kelime kümeleri (Türkçe için)
    private val ACIL_DURUM_KELIMELERI = setOf(
        "acil", "yardım", "bayılıyorum", "hasta", "kriz", "düşüyorum", "nöbet"
    )
    private val BDT_KELIMELERI = setOf(
        "bunalıyorum", "korkuyorum"
    )
    private val ARA_KELIMELERI = setOf("ara", "çağır", "bağlan")
    private val ILAC_EKLE_KELIMELERI = setOf("ilaca başladım", "yeni ilaç", "ilaç ekle")
    private val SAGLIK_NOTU_KELIMELERI = setOf("not ekle", "kaydedelim", "not al", "not et", "unutma")
    private val LISTELE_ILAC_KELIMELERI = setOf("ilaçlarım", "ilaç listem", "hangi ilaç")
    private val LISTELE_GECMIS_KELIMELERI = setOf("geçmişim", "kayıtlarım", "neler oldu")

    // Çoklu Yapay Zeka Arama ve Raporlama Terimleri
    private val ARASTIRMA_KELIMELERI = setOf(
        "araştırma", "araştır", "bilgi bul", "internet", "makale", "tedavi", "perplexity", "arama yap", "son araştırmalar"
    )
    private val DOKTOR_RAPOR_KELIMELERI = setOf(
        "raporla", "notlarımı raporla", "doktor raporu", "doktoruma raporla"
    )

    // Parkinson ve Anksiyete Dostu Müzik Kelimeleri
    private val KASILMA_KELIMELERI = setOf(
        "kasılıyorum", "kasılma", "donma", "kramp", "titreme", "motor"
    )
    private val PANIK_KELIMELERI = setOf(
        "panik", "panik atak", "nefes", "nefes alamıyorum", "kalp", "çarpıntı", "kalp çarpıntısı"
    )
    private val KAYGI_KELIMELERI = setOf(
        "kaygılıyım", "kaygı", "sakinleşmek", "huzur"
    )
    private val MUZIK_CAL_KELIMELERI = setOf(
        "sakin bir müzik aç", "sakin müzik aç", "müzik çal", "müzik aç", "şarkı çal", "şarkı aç", "çal", "başlat", "evet", "olur"
    )
    private val MUZIK_DURDUR_KELIMELERI = setOf(
        "durdur", "kapat", "müziği kapat", "müziği durdur", "kes", "sessiz", "müzik kapat"
    )

    /**
     * @param metin Wake-word çıkarılmış, küçük harfe dönüştürülmüş ham komut metni.
     * @return Eşleşen [VoiceCommand] veya [VoiceCommand.Bilinmiyor].
     */
    fun parse(metin: String): VoiceCommand {
        val temiz = metin.lowercase().trim()
        Log.d(TAG, "Komut parse ediliyor: \"$temiz\"")

        // 1) Acil durum
        if (ACIL_DURUM_KELIMELERI.any { temiz.contains(it) }) {
            Log.i(TAG, "Acil durum komutu tespit edildi.")
            return VoiceCommand.AcilDurum
        }

        // 2) Müzik Durdurma (Yüksek öncelikli)
        if (MUZIK_DURDUR_KELIMELERI.any { temiz.contains(it) }) {
            Log.i(TAG, "Müziği durdurma komutu tespit edildi.")
            return VoiceCommand.MuzikDurdur
        }

        // 3) Parkinson / Spasm / Motor Donma Durumu -> parkinson_rhythm.mp3
        if (KASILMA_KELIMELERI.any { temiz.contains(it) }) {
            Log.i(TAG, "Kasılma durumu tespit edildi, motor kabuk uyarıcı ARS müziği çalınacak.")
            return VoiceCommand.MuzikCal("audio/parkinson_rhythm.mp3")
        }

        // 4) Panik Atak / Kalp Çarpıntısı Durumu -> weightless_calm.mp3
        if (PANIK_KELIMELERI.any { temiz.contains(it) }) {
            Log.i(TAG, "Panik atak durumu tespit edildi, topraklama müziği çalınacak.")
            return VoiceCommand.MuzikCal("audio/weightless_calm.mp3")
        }

        // 5) Yoğun Kaygı / Sakin Müzik İstekleri -> ambient_432hz.mp3
        if (KAYGI_KELIMELERI.any { temiz.contains(it) } || MUZIK_CAL_KELIMELERI.any { temiz.contains(it) }) {
            Log.i(TAG, "Sakin müzik veya kaygı durumu tespit edildi, 432 Hz müzik çalınacak.")
            return VoiceCommand.MuzikCal("audio/ambient_432hz.mp3")
        }

        // 5.5) Doktor Raporlama
        if (DOKTOR_RAPOR_KELIMELERI.any { temiz.contains(it) }) {
            Log.i(TAG, "Doktor raporu üretme komutu tespit edildi.")
            return VoiceCommand.DoktorRaporuUret
        }

        // 5.6) Tıbbi Araştırma (Perplexity)
        if (ARASTIRMA_KELIMELERI.any { temiz.contains(it) }) {
            Log.i(TAG, "Tıbbi araştırma komutu tespit edildi.")
            return VoiceCommand.ArastirmaYap(temiz)
        }

        // 6) BDT Sakinleşme Modu (F-06) - Diğer CBT durumları için
        if (BDT_KELIMELERI.any { temiz.contains(it) }) {
            Log.i(TAG, "CBT / Sakinleşme modu komutu tespit edildi.")
            return VoiceCommand.BdtModu
        }

        // 2) Arama: "ara [isim]" → ismi çıkart
        ARA_KELIMELERI.forEach { anahtar ->
            val idx = temiz.indexOf(anahtar)
            if (idx != -1) {
                // "ara"dan sonra gelen kısmı isim olarak al
                val kisimAdi = temiz.substring(idx + anahtar.length).trim()
                if (kisimAdi.isNotBlank()) {
                    Log.i(TAG, "Arama komutu: \"$kisimAdi\"")
                    return VoiceCommand.AraKisi(kisimAdi)
                }
            }
        }

        // 3) İlaç ekleme
        if (ILAC_EKLE_KELIMELERI.any { temiz.contains(it) }) {
            Log.i(TAG, "İlaç ekleme komutu, Gemini'ye devrediliyor.")
            // Dozaj ve saat bilgisi karmaşık → Gemini'ye bırak
            return VoiceCommand.Bilinmiyor(temiz)
        }

        // 4) Sağlık notu
        if (SAGLIK_NOTU_KELIMELERI.any { temiz.contains(it) }) {
            val icerik = temiz
            Log.i(TAG, "Sağlık notu komutu: \"$icerik\"")
            return VoiceCommand.SaglikNotuKaydet(icerik)
        }

        // 5) İlaç listele
        if (LISTELE_ILAC_KELIMELERI.any { temiz.contains(it) }) {
            return VoiceCommand.IlaclariListele
        }

        // 6) Sağlık geçmişi listele
        if (LISTELE_GECMIS_KELIMELERI.any { temiz.contains(it) }) {
            return VoiceCommand.SaglikGecmisiniListele
        }

        // 7) Bilinmiyor → Gemini NLP devreye girecek
        Log.d(TAG, "Komut tanınamadı, Gemini'ye yönlendiriliyor.")
        return VoiceCommand.Bilinmiyor(temiz)
    }
}

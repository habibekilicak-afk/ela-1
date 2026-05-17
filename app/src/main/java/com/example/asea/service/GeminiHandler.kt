package com.example.asea.service

import android.content.Context
import android.util.Log
import com.example.asea.data.local.AseaDatabase
import com.example.asea.data.local.DatabaseKeyProvider
import com.example.asea.data.local.GeminiApiKeyProvider
import com.example.asea.data.local.entity.IlacTakipEntity
import com.example.asea.data.local.entity.SaglikGecmisiEntity
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Gemini API üzerinden doğal dil komutlarını anlayan ve
 * Room DB'ye ilgili işlemi yansıtan handler.
 *
 * Sorumluluklar:
 * 1. Kullanıcının ham sesli komutunu Gemini'ye iletir.
 * 2. Gemini'den yapılandırılmış [GeminiIntent] JSON yanıtı alır.
 * 3. Niyet tipine göre Room DB'ye ilaç ekler veya sağlık notu kaydeder.
 * 4. Sonucu [EmergencyTtsHandler] aracılığıyla kullanıcıya sesli bildirir.
 *
 * Sistem Prompt Stratejisi:
 * - Gemini'ye ASEA asistanının rolü ve bağlamı açıklanır.
 * - Yanıtlar her zaman sabit bir JSON şemasında döndürülmesi zorunlu kılınır.
 * - Bu şema [GeminiIntent] data class ile birebir eşleşir.
 */
class GeminiHandler(
    private val context: Context,
    private val tts: EmergencyTtsHandler
) {
    companion object {
        private const val TAG = "GeminiHandler"

        /**
         * Sistem prompt — Gemini'ye ASEA'nın kim olduğunu ve hangi JSON
         * formatında yanıt vereceğini açıklar.
         *
         * JSON yanıt şeması:
         * - niyet: "ilac_ekle" | "saglik_notu" | "sorgu" | "belirsiz"
         * - ilac_adi, dozaj, saat: niyet="ilac_ekle" ise dolu
         * - not_icerigi: niyet="saglik_notu" ise dolu
         * - yanit_metni: her zaman dolu (kullanıcıya TTS ile okunacak Türkçe cümle)
         */
        private val SISTEM_PROMPT = """
            Sen ASEA (Akıllı Sağlık ve Erişilebilirlik Asistanı) adlı bir yapay zeka sağlık asistanısın.
            Parkinson ve kaygı bozukluğu olan bir kullanıcıya destek veriyorsun.
            
            Kullanıcı sana sesli komut yoluyla Türkçe konuşacak. Görevin:
            1. Komutun niyetini belirlemek.
            2. Yanıtını SADECE aşağıdaki JSON formatında döndürmek. Hiçbir ek açıklama veya markdown ekleme.
            
            JSON Şeması:
            {
              "niyet": "<ilac_ekle|saglik_notu|sorgu|belirsiz>",
              "ilac_adi": "<string veya null>",
              "dozaj": "<string veya null>",
              "saat": "<HH:mm formatında veya null>",
              "not_icerigi": "<string veya null>",
              "yanit_metni": "<kullanıcıya sesli okunacak Türkçe kısa yanıt>"
            }
            
            Niyet Tanımları:
            - "ilac_ekle": Kullanıcı yeni bir ilaç başladığını veya eklemek istediğini belirtir.
            - "saglik_notu": Kullanıcı bir belirti, his veya günlük sağlık notu paylaşır.
            - "sorgu": Kullanıcı ilaçları veya sağlık geçmişi hakkında bilgi ister.
            - "belirsiz": Niyet anlaşılamıyorsa.
            
            Önemli Kurallar:
            - "saat" alanını her zaman HH:mm (24 saat) formatında yaz.
            - "yanit_metni" kısa, net ve kullanıcıyı rahatlatıcı olsun.
            - Tıbbi öneri verme; sadece kayıt al ve bilgi sun.
        """.trimIndent()
    }

    // JSON parser — bilinmeyen alanları yoksay (API değişikliklerine karşı güvenli)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val db by lazy {
        AseaDatabase.getInstance(context, DatabaseKeyProvider.getKey(context))
    }

    // ------------------------------------------------------------------ //
    // Ana giriş noktası
    // ------------------------------------------------------------------ //

    /**
     * [hamMetin] sesli komuttan gelen (wake-word çıkarılmış) ham metni temsil eder.
     * Gemini'ye gönderir, yanıtı ayrıştırır ve ilgili DB işlemini gerçekleştirir.
     */
    suspend fun isle(hamMetin: String) {
        if (hamMetin.isBlank()) return

        val apiKey = GeminiApiKeyProvider.getKey(context)
        if (apiKey.isBlank()) {
            Log.w(TAG, "Gemini API anahtarı bulunamadı.")
            tts.oku("Yapay zeka özelliği için lütfen ayarlar ekranından API anahtarınızı girin.")
            return
        }

        try {
            val intent = withContext(Dispatchers.IO) {
                geminiIsle(apiKey, hamMetin)
            }
            intent?.let { dispatch(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini işleme hatası: ${e.message}", e)
            tts.oku("Yapay zeka isteği sırasında bir hata oluştu. Lütfen tekrar deneyin.")
        }
    }

    // ------------------------------------------------------------------ //
    // Gemini API çağrısı
    // ------------------------------------------------------------------ //

    private suspend fun geminiIsle(apiKey: String, metin: String): GeminiIntent? {
        val model = GenerativeModel(
            modelName = "gemini-1.5-flash",     // Hız/maliyet dengesi için flash modeli
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f              // Düşük temperature → tutarlı JSON çıktısı
                topP = 0.9f
                maxOutputTokens = 512
            },
            systemInstruction = content { text(SISTEM_PROMPT) }
        )

        Log.d(TAG, "Gemini'ye gönderilen metin: \"$metin\"")
        val yanit = model.generateContent(metin)
        val yanitMetni = yanit.text?.trim() ?: return null

        Log.d(TAG, "Gemini yanıtı: $yanitMetni")

        // JSON bloğunu temizle (Gemini bazen ```json ... ``` ekleyebilir)
        val temizJson = yanitMetni
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            json.decodeFromString<GeminiIntent>(temizJson)
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse hatası: ${e.message}\nYanıt: $temizJson")
            // Parse başarısız → belirsiz niyet olarak işle
            GeminiIntent(
                niyet = "belirsiz",
                yanitMetni = "Komutunuzu anlayamadım. Lütfen tekrar söyler misiniz?"
            )
        }
    }

    // ------------------------------------------------------------------ //
    // Niyet → DB işlemi dispatcher'ı
    // ------------------------------------------------------------------ //

    private suspend fun dispatch(intent: GeminiIntent) {
        Log.i(TAG, "Niyet: ${intent.niyet} | Yanıt: ${intent.yanitMetni}")

        when (intent.niyet) {
            "ilac_ekle" -> ilacEkle(intent)
            "saglik_notu" -> saglikNotuKaydet(intent)
            "sorgu"       -> sorguYanıtla(intent)
            else          -> tts.oku(intent.yanitMetni)   // "belirsiz" veya bilinmeyen
        }
    }

    // ------------------------------------------------------------------ //
    // İlaç ekleme
    // ------------------------------------------------------------------ //

    private suspend fun ilacEkle(intent: GeminiIntent) {
        val ilacAdi = intent.ilacAdi
        if (ilacAdi.isNullOrBlank()) {
            tts.oku("İlaç adını anlayamadım. Lütfen 'Ela, yeni ilaç ekle: [ilaç adı]' şeklinde tekrar deneyin.")
            return
        }

        val ilac = IlacTakipEntity(
            ilacAdi = ilacAdi,
            dozaj = intent.dozaj ?: "Belirtilmedi",
            hatirlatmaSaati = intent.saat ?: "08:00",
            aktifMi = true
        )

        withContext(Dispatchers.IO) {
            db.ilacTakipDao.insertIlac(ilac)
        }

        Log.i(TAG, "İlaç eklendi: $ilacAdi (${intent.dozaj}, ${intent.saat})")
        tts.oku(intent.yanitMetni)
    }

    // ------------------------------------------------------------------ //
    // Sağlık notu kaydetme
    // ------------------------------------------------------------------ //

    private suspend fun saglikNotuKaydet(intent: GeminiIntent) {
        val icerik = intent.notIcerigi ?: intent.yanitMetni

        withContext(Dispatchers.IO) {
            db.saglikGecmisiDao.insertSaglikKaydi(
                SaglikGecmisiEntity(
                    kayitTipi = "Not",
                    icerik = icerik,
                    kayitTarihi = System.currentTimeMillis()
                )
            )
        }

        Log.i(TAG, "Sağlık notu kaydedildi: \"$icerik\"")
        tts.oku(intent.yanitMetni)
    }

    // ------------------------------------------------------------------ //
    // Sorgu yanıtlama (ilaçlar / geçmiş)
    // ------------------------------------------------------------------ //

    private suspend fun sorguYanıtla(intent: GeminiIntent) {
        // Gemini'nin önerdiği yanıtı, mevcut DB verileriyle zenginleştir
        val ilaclar = withContext(Dispatchers.IO) {
            db.ilacTakipDao.getAktifIlaclar().first()
        }

        val zenginlestirilmisYanit = if (ilaclar.isNotEmpty() &&
            intent.yanitMetni.contains("ilaç", ignoreCase = true)
        ) {
            val ilacListesi = ilaclar.joinToString(", ") { it.ilacAdi }
            "${intent.yanitMetni} Aktif ilaçlarınız: $ilacListesi."
        } else {
            intent.yanitMetni
        }

        tts.oku(zenginlestirilmisYanit)
    }
}

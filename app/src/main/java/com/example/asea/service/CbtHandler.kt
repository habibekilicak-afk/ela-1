package com.example.asea.service

import android.content.Context
import android.util.Log
import com.example.asea.data.local.GeminiApiKeyProvider
import com.example.asea.data.local.MultiLlmApiKeyProvider
import com.example.asea.data.local.AseaDatabase
import com.example.asea.data.local.DatabaseKeyProvider
import com.example.asea.data.local.entity.SaglikGecmisiEntity
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * F-06: Bilişsel Davranışçı Terapi (BDT) Sakinleşme Modu
 *
 * Sorumluluklar:
 * 1. Kullanıcı kaygı/panik bildirdiğinde devreye girer.
 * 2. Gemini API ile Klinik BDT Psikoloğu rolünde sesli/metinsel rahatlatıcı destek sunar.
 * 3. 4-7-8 Nefes Egzersizini yavaş ve ritmik seslendirme (`speechRate = 0.75f`) ile yönetir.
 * 4. 5-4-3-2-1 Topraklama (Grounding) tekniğini interaktif olarak uygular.
 * 5. Durumu "Sakinleşme Modu" başlığı altında Sağlık Geçmişi Room DB'sine kaydeder.
 */
class CbtHandler(
    private val context: Context,
    private val tts: EmergencyTtsHandler
) {
    companion object {
        private const val TAG = "CbtHandler"

        private val BDT_SISTEM_PROMPT = """
            Sen Klinik BDT (Bilişsel Davranışçı Terapi) uzmanı bir psikologsun.
            Kaygı atağı, panik atak veya yoğun stres yaşayan bir Parkinson hastasına destek oluyorsun.
            
            Kullanıcı panik içinde konuşabilir veya kaygılı olduğunu bildirebilir. Görevin:
            1. Onu yargılamadan, son derece sakinleştirici, şefkatli, yavaş ve destekleyici bir tonla karşıla.
            2. Kısa, nefes almasını kolaylaştıracak 2-3 cümlelik bir giriş yap.
            3. Güven verici konuş ve onu rahatlat. Tıbbi öneri verme, sadece sakinleşmesini sağla.
        """.trimIndent()
    }

    private val db by lazy {
        AseaDatabase.getInstance(context, DatabaseKeyProvider.getKey(context))
    }

    /**
     * BDT Sakinleşme Modunun ana giriş fonksiyonu.
     * Sırasıyla: Kaygı kaydı ekle → Claude / Gemini ile hafıza destekli rahatlatıcı sesli yanıt → 4-7-8 Nefes Egzersizi → Kapanış
     */
    suspend fun sakinlestir(hamKomut: String) {
        Log.i(TAG, "CBT sakinleşme modu tetiklendi: \"$hamKomut\"")

        // 1) Sağlık Geçmişine Atak Başlangıcını kaydet
        saglikKaydiEkle("Anksiyete", "Sakinleşme modu tetiklendi. Komut: \"$hamKomut\"")

        // 2) Uzun vadeli hafızayı (LTM) yükle
        val memoryContext = MemoryManager.getProfileContext(context)
        val fullSystemPrompt = """
            $BDT_SISTEM_PROMPT
            
            Kullanıcının geçmiş seanslardan öğrenilmiş uzun vadeli hafıza profili şu şekildedir:
            $memoryContext
            
            Lütfen yanıtını bu hafıza bağlamına göre özelleştir (örneğin sevdiği bir hobisi varsa sakinleşirken atıfta bulunabilirsin).
        """.trimIndent()

        var rahatlaticiMesaj = ""

        // A) Öncelikli olarak Claude 3.5 Sonnet'i devreye al
        val claudeKey = MultiLlmApiKeyProvider.getClaudeKey(context)
        if (claudeKey.isNotBlank()) {
            try {
                Log.i(TAG, "CBT Sakinleştirme için Claude API devrede...")
                val claudeHandler = ClaudeHandler(context)
                rahatlaticiMesaj = claudeHandler.generateResponse(hamKomut, fullSystemPrompt)
            } catch (e: Exception) {
                Log.e(TAG, "Claude CBT bağlantı hatası, Gemini fall-back devreye giriyor", e)
            }
        }

        // B) Eğer Claude anahtarı yoksa veya hata verdiyse Gemini fall-back'i kullan
        if (rahatlaticiMesaj.isBlank()) {
            val geminiKey = GeminiApiKeyProvider.getKey(context)
            if (geminiKey.isNotBlank()) {
                try {
                    Log.i(TAG, "CBT Sakinleştirme için Gemini fall-back devrede...")
                    rahatlaticiMesaj = geminiCbtDestegi(geminiKey, hamKomut, memoryContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini CBT hatası", e)
                }
            }
        }

        // C) İki model de başarısız olursa güvenli varsayılan yanıtı seslendir
        if (rahatlaticiMesaj.isBlank()) {
            rahatlaticiMesaj = "Şu an yanındayım. Güvendesin. Birlikte sakinleşeceğiz. Lütfen beni dinle."
        }

        // Yanıtı yavaş konuşma hızıyla seslendir
        tts.oku(rahatlaticiMesaj, 0.75f)

        // Uzun vadeli hafızayı asenkron olarak yeni seans bilgileriyle güncelle
        try {
            MemoryManager.updateProfile(context, hamKomut, rahatlaticiMesaj)
        } catch (e: Exception) {
            Log.e(TAG, "LTM güncellenirken hata oluştu", e)
        }

        delay(1000L)

        // 3) 4-7-8 Nefes Egzersizini Başlat (3 Döngü)
        tts.oku("Şimdi seninle birlikte 4-7-8 nefes egzersizi yapacağız. Ben sayarken bana eşlik et.", 0.75f)
        delay(1500L)

        for (dongu in 1..3) {
            Log.d(TAG, "4-7-8 Döngü $dongu başladı")
            
            // 4 saniye nefes al
            tts.oku("Burundan yavaşça nefes al. 1... 2... 3... 4...", 0.70f)
            delay(4000L)
            
            // 7 saniye tut
            tts.oku("Nefesini tut. 1... 2... 3... 4... 5... 6... 7...", 0.70f)
            delay(7000L)
            
            // 8 saniye ağızdan ver
            tts.oku("Şimdi ağzından yavaşça üfleyerek ver. 1... 2... 3... 4... 5... 6... 7... 8...", 0.70f)
            delay(8000L)
        }

        // 4) 5-4-3-2-1 Topraklama (Grounding) yönlendirmesi
        tts.oku("Harika yaptın. Şimdi çevrene bakıp görebildiğin üç büyük şeyi içinden tekrar et.", 0.75f)
        delay(4000L)
        
        tts.oku("Şimdi duyabildiğin iki farklı sesi dinle ve odaklan.", 0.75f)
        delay(4000L)

        tts.oku("Ve şimdi tenine dokunan kıyafetinin veya oturduğun koltuğun hissini fark et. Güvendesin, buradasın.", 0.75f)
        delay(3000L)

        // 5) Kapanış
        val bitisMesajı = "Nefesin normale dönene kadar sakin kalmaya devam et. Ben her zaman buradayım."
        tts.oku(bitisMesajı, 0.75f)

        saglikKaydiEkle("Anksiyete", "Sakinleşme modu başarıyla tamamlandı. Kullanıcı sakinleştirildi.")
    }

    private suspend fun geminiCbtDestegi(apiKey: String, komut: String, memoryContext: String): String = withContext(Dispatchers.IO) {
        val fullPrompt = """
            $BDT_SISTEM_PROMPT
            
            Kullanıcının geçmiş seanslardan öğrenilmiş uzun vadeli hafıza profili:
            $memoryContext
        """.trimIndent()

        val model = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.4f
                maxOutputTokens = 200
            },
            systemInstruction = content { text(fullPrompt) }
        )

        val response = model.generateContent("Sana gelen acil durum stres çağrısı: \"$komut\". Kullanıcıyı rahatlatacak kısa, yumuşak bir şeyler söyle.")
        response.text?.trim() ?: "Derin bir nefes al. Ben buradayım."
    }

    private suspend fun saglikKaydiEkle(tip: String, icerik: String) = withContext(Dispatchers.IO) {
        db.saglikGecmisiDao.insertSaglikKaydi(
            SaglikGecmisiEntity(
                kayitTipi = tip,
                icerik = icerik,
                kayitTarihi = System.currentTimeMillis()
            )
        )
    }
}

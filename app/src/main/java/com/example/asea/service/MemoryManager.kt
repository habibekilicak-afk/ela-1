package com.example.asea.service

import android.content.Context
import android.util.Log
import com.example.asea.data.local.AseaDatabase
import com.example.asea.data.local.DatabaseKeyProvider
import com.example.asea.data.local.GeminiApiKeyProvider
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Kullanıcının karakterini, alışkanlıklarını, tıbbi durumunu ve asistanla olan geçmişini
 * Room veritabanında şifreli olarak saklayan ve yöneten uzun vadeli hafıza (LTM) modülü.
 */
object MemoryManager {

    private const val TAG = "MemoryManager"

    /**
     * Room veritabanındaki kullanıcı ayarları tablosundan profil JSON verisini okur ve metne dönüştürür.
     * @param context Uygulama bağlamı.
     * @return Yapay zeka sistem talimatlarına (system prompts) eklenmeye hazır profil metni.
     */
    suspend fun getProfileContext(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val db = AseaDatabase.getInstance(context, DatabaseKeyProvider.getKey(context))
            val ayarlar = db.kullaniciAyarlariDao.getAyarlar().first()
            val profileJson = ayarlar?.userProfileJson ?: "{}"
            Log.d(TAG, "Uzun vadeli hafıza JSON yüklendi: $profileJson")

            val json = JSONObject(profileJson)
            if (json.length() == 0) {
                return@withContext "Kullanıcı Hafıza Profili: [Henüz öğrenilmiş karakter, alışkanlık veya semptom bilgisi yok.]"
            }

            val builder = StringBuilder()
            builder.append("Kullanıcı Hafıza Profili:\n")
            json.keys().forEach { key ->
                builder.append("- $key: ${json.get(key)}\n")
            }
            return@withContext builder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Uzun vadeli hafıza bağlamı okunurken hata oluştu", e)
            return@withContext ""
        }
    }

    /**
     * Konuşma sonrasında, kullanıcının girdisini ve asistanın yanıtını analiz ederek
     * kullanıcının hayatı, tercihleri, Parkinson semptomları veya alışkanlıkları hakkındaki kalıcı bilgileri günceller.
     * @param context Uygulama bağlamı.
     * @param userMessage Kullanıcının asistanla konuştuğu son girdi.
     * @param aiResponse Asistanın ürettiği sakinleştirici veya tıbbi yanıt.
     */
    suspend fun updateProfile(context: Context, userMessage: String, aiResponse: String) = withContext(Dispatchers.IO) {
        val geminiKey = GeminiApiKeyProvider.getKey(context)
        if (geminiKey.isBlank()) {
            Log.d(TAG, "Gemini Key eksik olduğu için hafıza güncellenmedi.")
            return@withContext
        }

        try {
            val db = AseaDatabase.getInstance(context, DatabaseKeyProvider.getKey(context))
            val ayarlar = db.kullaniciAyarlariDao.getAyarlar().first() ?: return@withContext
            val currentProfileJson = ayarlar.userProfileJson

            val prompt = """
                Aşağıdaki son konuşmaya göre kullanıcının kişiliği, tercihleri, sağlık durumu, Parkinson semptomları veya alışkanlıkları hakkında yeni, kalıcı bilgiler öğrenilmiş mi analiz et.
                
                Mevcut Kullanıcı Hafızası (JSON):
                $currentProfileJson
                
                Kullanıcı: "$userMessage"
                Asistan: "$aiResponse"
                
                Eğer yeni bir bilgi varsa, mevcut JSON yapısını güncelle veya yeni alanlar ekle. Sadece ve sadece geçerli ve temiz bir JSON nesnesi döndür (markdown kod bloğu olmadan, sadece ham json).
                Yeni bir şey öğrenilmediyse mevcut JSON'u aynen geri döndür.
            """.trimIndent()

            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = geminiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f
                    maxOutputTokens = 300
                }
            )

            val response = model.generateContent(prompt)
            var responseText = response.text?.trim() ?: "{}"
            
            // Markdown kod bloklarını temizleme
            if (responseText.startsWith("```json")) {
                responseText = responseText.substringAfter("```json").substringBeforeLast("```").trim()
            } else if (responseText.startsWith("```")) {
                responseText = responseText.substringAfter("```").substringBeforeLast("```").trim()
            }

            // JSON doğrulaması yapıp veri tabanını güncelle
            try {
                JSONObject(responseText) // Geçerlilik testi
                val updatedAyarlar = ayarlar.copy(userProfileJson = responseText)
                db.kullaniciAyarlariDao.insertOrUpdate(updatedAyarlar)
                Log.i(TAG, "Uzun vadeli hafıza başarıyla güncellendi ve kaydedildi: $responseText")
            } catch (jsonEx: Exception) {
                Log.e(TAG, "Hafıza güncellenirken Gemini geçersiz JSON döndürdü: $responseText", jsonEx)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uzun vadeli hafıza güncellenirken hata oluştu", e)
        }
    }
}

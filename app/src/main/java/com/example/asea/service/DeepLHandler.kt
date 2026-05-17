package com.example.asea.service

import android.content.Context
import android.util.Log
import com.example.asea.data.local.MultiLlmApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Yabancı tıp makalelerini ve bilimsel terimleri akademik bütünlüğü koruyarak
 * Türkçe'ye çeviren DeepL API istemci modülü.
 */
class DeepLHandler(private val context: Context) {

    companion object {
        private const val TAG = "DeepLHandler"
        private const val DEEPL_ENDPOINT = "https://api-free.deepl.com/v2/translate"
    }

    /**
     * Verilen metni Türkçe'ye çevirir.
     * @param text Çevrilecek yabancı kaynak metni.
     * @return Türkçe çeviri sonucu. Hata veya boş anahtar durumunda ham kaynak metni aynen geri döner.
     */
    suspend fun translateToTurkish(text: String): String = withContext(Dispatchers.IO) {
        val apiKey = MultiLlmApiKeyProvider.getDeepLKey(context)
        if (apiKey.isBlank()) {
            Log.w(TAG, "DeepL API Anahtarı bulunamadı. Çeviri yapılmadan ham metin dönülüyor.")
            return@withContext text
        }

        try {
            val url = URL(DEEPL_ENDPOINT)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "DeepL-Auth-Key $apiKey")
            conn.setRequestProperty("content-type", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.doOutput = true

            // JSON Body
            val jsonBody = JSONObject().apply {
                val textArray = JSONArray().apply {
                    put(text)
                }
                put("text", textArray)
                put("target_lang", "TR")
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.use { it.readText() }
                
                val jsonResponse = JSONObject(response)
                val translations = jsonResponse.getJSONArray("translations")
                if (translations.length() > 0) {
                    return@withContext translations.getJSONObject(0).getString("text").trim()
                }
            } else {
                val errorStream = conn.errorStream
                val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "DeepL API Hatası: $responseCode | Mesaj: $errorMsg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "DeepL API bağlantı hatası", e)
        }
        return@withContext text
    }
}

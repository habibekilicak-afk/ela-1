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
 * Derin felsefi, psikolojik ve BDT sakinleştirme egzersizleri için
 * Claude API (Anthropic Messages API) ile bağlantı kuran modül.
 */
class ClaudeHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "ClaudeHandler"
        private const val CLAUDE_ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val CLAUDE_MODEL = "claude-3-5-sonnet-20241022"
    }

    /**
     * Kullanıcının girdisine karşılık Claude yanıtı üretir.
     * @param prompt Kullanıcının anksiyete, panik veya konuşma metni.
     * @param systemInstruction Modelin rolünü tanımlayan sistem direktifi.
     * @return Modelin yanıt metni. Hata veya boş anahtar durumunda boş string döner.
     */
    suspend fun generateResponse(prompt: String, systemInstruction: String = ""): String = withContext(Dispatchers.IO) {
        val apiKey = MultiLlmApiKeyProvider.getClaudeKey(context)
        if (apiKey.isBlank()) {
            Log.w(TAG, "Claude API Anahtarı bulunamadı. Boş dönülüyor.")
            return@withContext ""
        }

        try {
            val url = URL(CLAUDE_ENDPOINT)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.setRequestProperty("content-type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.doOutput = true

            // JSON Body
            val jsonBody = JSONObject().apply {
                put("model", CLAUDE_MODEL)
                put("max_tokens", 1024)
                
                if (systemInstruction.isNotBlank()) {
                    put("system", systemInstruction)
                }

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                put("messages", messagesArray)
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
                val contentArray = jsonResponse.getJSONArray("content")
                if (contentArray.length() > 0) {
                    val textObject = contentArray.getJSONObject(0)
                    return@withContext textObject.getString("text").trim()
                }
            } else {
                val errorStream = conn.errorStream
                val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Claude API Hatası: $responseCode | Mesaj: $errorMsg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Claude API bağlantısı sırasında hata", e)
        }
        return@withContext ""
    }
}

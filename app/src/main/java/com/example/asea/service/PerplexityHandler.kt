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
 * En güncel medikal makaleleri, Parkinson ve anksiyete üzerine yapılan
 * araştırmaları anlık olarak internetten sorgulayan Perplexity API istemcisi.
 */
class PerplexityHandler(private val context: Context) {

    companion object {
        private const val TAG = "PerplexityHandler"
        private const val PERPLEXITY_ENDPOINT = "https://api.perplexity.ai/chat/completions"
        private const val PERPLEXITY_MODEL = "sonar-reasoning"
    }

    /**
     * Tıbbi araştırma sorguları gerçekleştirir ve sonucu metin olarak döner.
     * @param query Tıbbi arama sorgusu.
     * @return Akademik ve güncel arama sonuçları. Hata durumunda boş string döner.
     */
    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        val apiKey = MultiLlmApiKeyProvider.getPerplexityKey(context)
        if (apiKey.isBlank()) {
            Log.w(TAG, "Perplexity API Anahtarı bulunamadı. Boş dönülüyor.")
            return@withContext ""
        }

        try {
            val url = URL(PERPLEXITY_ENDPOINT)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("content-type", "application/json")
            conn.connectTimeout = 12000
            conn.readTimeout = 20000
            conn.doOutput = true

            // JSON Body
            val jsonBody = JSONObject().apply {
                put("model", PERPLEXITY_MODEL)
                
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Sen Parkinson ve anksiyete üzerine tıbbi araştırmaları tarafsız, bilimsel ve güncel olarak tarayan, hastaya ve doktoruna bilgi sunan uzman bir yapay zeka tıp asistanısın. Lütfen bulguları ve kaynakları Türkçe ve akademik olarak sun.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", query)
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
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    return@withContext message.getString("content").trim()
                }
            } else {
                val errorStream = conn.errorStream
                val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Perplexity API Hatası: $responseCode | Mesaj: $errorMsg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Perplexity API bağlantı hatası", e)
        }
        return@withContext ""
    }
}

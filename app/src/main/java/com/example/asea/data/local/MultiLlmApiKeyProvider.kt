package com.example.asea.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.asea.BuildConfig

/**
 * Claude, Perplexity ve DeepL API anahtarlarını güvenli biçimde yöneten sağlayıcı.
 * Hem yerel BuildConfig üzerinden hem de EncryptedSharedPreferences üzerinden dinamik okuma yapar.
 */
object MultiLlmApiKeyProvider {

    private const val PREFS_FILE = "asea_multi_llm_prefs"
    private const val CLAUDE_KEY = "claude_api_key"
    private const val PERPLEXITY_KEY = "perplexity_api_key"
    private const val DEEPL_KEY = "deepl_api_key"

    /** Claude API Anahtarını döndürür. */
    fun getClaudeKey(context: Context): String {
        val stored = readFromPrefs(context, CLAUDE_KEY)
        if (stored.isNotBlank()) return stored
        
        return try {
            BuildConfig.CLAUDE_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    /** Perplexity API Anahtarını döndürür. */
    fun getPerplexityKey(context: Context): String {
        val stored = readFromPrefs(context, PERPLEXITY_KEY)
        if (stored.isNotBlank()) return stored
        
        return try {
            BuildConfig.PERPLEXITY_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    /** DeepL API Anahtarını döndürür. */
    fun getDeepLKey(context: Context): String {
        val stored = readFromPrefs(context, DEEPL_KEY)
        if (stored.isNotBlank()) return stored
        
        return try {
            BuildConfig.DEEPL_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    /** API anahtarlarını uygulama içinden dinamik olarak kaydeder. */
    fun saveKeys(context: Context, claude: String, perplexity: String, deepl: String) {
        val prefs = buildPrefs(context)
        prefs.edit().apply {
            putString(CLAUDE_KEY, claude.trim())
            putString(PERPLEXITY_KEY, perplexity.trim())
            putString(DEEPL_KEY, deepl.trim())
            apply()
        }
    }

    private fun readFromPrefs(context: Context, key: String): String {
        return try {
            buildPrefs(context).getString(key, "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

package com.example.asea.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.asea.BuildConfig

/**
 * Gemini API anahtarını güvenli biçimde yöneten sağlayıcı.
 *
 * Öncelik sırası:
 * 1. [EncryptedSharedPreferences]'te kullanıcının uygulama içinden girdiği anahtar.
 * 2. `local.properties` → `BuildConfig.GEMINI_API_KEY` (geliştirme ortamı).
 *
 * Bu sayede;
 * - Geliştirici local.properties'e anahtarını koyarak doğrudan çalışabilir.
 * - Son kullanıcı Ayarlar ekranından kendi API anahtarını girebilir (Adım 7).
 */
object GeminiApiKeyProvider {

    private const val PREFS_FILE  = "asea_gemini_prefs"
    private const val KEY_API_KEY = "gemini_api_key"

    /** Saklı API anahtarını döndürür; yoksa boş string. */
    fun getKey(context: Context): String {
        val stored = readFromPrefs(context)
        if (stored.isNotBlank()) return stored

        // BuildConfig'den oku (local.properties: GEMINI_API_KEY="AIza...")
        val buildConfigKey = BuildConfig.GEMINI_API_KEY
        return buildConfigKey
    }

    /** API anahtarını EncryptedSharedPreferences'a yazar (Ayarlar ekranından). */
    fun saveKey(context: Context, apiKey: String) {
        val prefs = buildPrefs(context)
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    /** Kaydedilmiş anahtarı siler (anahtar sıfırlama). */
    fun clearKey(context: Context) {
        val prefs = buildPrefs(context)
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    private fun readFromPrefs(context: Context): String =
        buildPrefs(context).getString(KEY_API_KEY, "") ?: ""

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

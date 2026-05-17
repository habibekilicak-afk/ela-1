package com.example.asea.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * SQLCipher veritabanı parolasını güvenli biçimde sağlar.
 *
 * Strateji:
 * - İlk çalıştırmada rastgele 256-bit parola üretilir.
 * - Parola, Android Keystore destekli [EncryptedSharedPreferences]'e yazılır.
 * - Sonraki açılışlarda aynı parola şifreli depodan okunur.
 *
 * Bu yaklaşım;
 *   • Parolanın kaynak kodda hard-coded olmamasını,
 *   • Cihaz güvenli alanı dışına çıkmamasını sağlar.
 */
object DatabaseKeyProvider {

    private const val PREFS_FILE = "asea_secure_prefs"
    private const val KEY_DB_PASS = "db_passphrase"

    fun getKey(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existing = prefs.getString(KEY_DB_PASS, null)
        if (existing != null) {
            return existing.toByteArray(Charsets.UTF_8)
        }

        // İlk çalıştırma: 32 bayt = 256-bit rastgele parola üret
        val random = SecureRandom()
        val bytes  = ByteArray(32).also { random.nextBytes(it) }
        val hex    = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_DB_PASS, hex).apply()
        return hex.toByteArray(Charsets.UTF_8)
    }
}

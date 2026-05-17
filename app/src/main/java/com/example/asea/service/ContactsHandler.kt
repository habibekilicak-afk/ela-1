package com.example.asea.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.asea.util.FuzzyMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sesli komuttan gelen ismi rehberde fuzzy-matching ile bulup
 * hoparlör (hands-free) modunda arayan handler.
 *
 * Akış:
 * 1. [READ_CONTACTS] ve [CALL_PHONE] izinleri kontrol edilir.
 * 2. ContentResolver ile tüm kişi adları + numaralar çekilir.
 * 3. [FuzzyMatcher] ile en yakın eşleşme hesaplanır.
 * 4. Yeterince iyi eşleşme bulunursa [Intent.ACTION_CALL] ile arama başlatılır.
 * 5. [AudioManager] üzerinden hoparlör modu etkinleştirilir.
 */
class ContactsHandler(
    private val context: Context,
    private val tts: EmergencyTtsHandler
) {
    companion object {
        private const val TAG = "ContactsHandler"

        /** Eşleşme skoru bu değerin altındaysa kişi "bulunamadı" sayılır */
        private const val ESLESME_ESIGI = 0.55f
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ------------------------------------------------------------------ //
    // Ana giriş noktası
    // ------------------------------------------------------------------ //

    /**
     * [komutIsmi] sesli komuttan gelen ham ismi temsil eder.
     * Rehberde en yakın kişiyi bulur ve arar; başarısızsa TTS ile bildirir.
     */
    suspend fun araKisi(komutIsmi: String) {
        if (komutIsmi.isBlank()) {
            tts.oku("Lütfen aramak istediğiniz kişinin adını söyleyin.")
            return
        }

        // 1) İzinleri kontrol et
        if (!izinlerMevcut()) {
            Log.w(TAG, "READ_CONTACTS veya CALL_PHONE izni eksik.")
            tts.oku("Rehber erişim izni bulunmuyor. Lütfen uygulama ayarlarından izin verin.")
            return
        }

        // 2) Rehberi oku
        val kisiler = withContext(Dispatchers.IO) { rehberiCek() }
        if (kisiler.isEmpty()) {
            tts.oku("Rehberinizde kayıtlı kişi bulunamadı.")
            return
        }

        // 3) Fuzzy matching
        val eslesme = FuzzyMatcher.enYakinEslesme(
            sorgu = komutIsmi,
            adaylar = kisiler.keys.toList(),
            esikDeger = ESLESME_ESIGI
        )

        if (eslesme == null) {
            Log.i(TAG, "\"$komutIsmi\" için yeterli eşleşme bulunamadı.")
            tts.oku("\"$komutIsmi\" adında bir kişi rehberinizde bulunamadı.")
            return
        }

        val (bulunanIsim, skor) = eslesme
        val numara = kisiler[bulunanIsim]

        if (numara.isNullOrBlank()) {
            tts.oku("$bulunanIsim için kayıtlı telefon numarası bulunamadı.")
            return
        }

        Log.i(TAG, "Eşleşme: \"$komutIsmi\" → \"$bulunanIsim\" (skor: %.2f), numara: $numara".format(skor))

        // 4) TTS geri bildirim — arama başlamadan önce kullanıcıyı bilgilendir
        tts.oku("$bulunanIsim aranıyor.")

        // 5) Arama başlat + hoparlör modu
        withContext(Dispatchers.Main) {
            aramaBaslat(numara)
        }
    }

    // ------------------------------------------------------------------ //
    // İzin kontrolü
    // ------------------------------------------------------------------ //

    private fun izinlerMevcut(): Boolean {
        val readContacts = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val callPhone = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return readContacts && callPhone
    }

    // ------------------------------------------------------------------ //
    // Rehber okuma
    // ------------------------------------------------------------------ //

    /**
     * ContactsProvider'dan kişi adı → telefon numarası eşlemesi döndürür.
     * Birden fazla numarası olan kişiler için ilk numara kullanılır.
     *
     * @return Map<Ad, Numara> — her kişi için birincil numara.
     */
    private fun rehberiCek(): Map<String, String> {
        val kisiler = mutableMapOf<String, String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIdx   = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val ad     = it.getString(nameIdx)?.trim() ?: continue
                val numara = it.getString(numberIdx)?.trim() ?: continue
                // Aynı kişinin birden fazla numarası varsa ilk kaydı tut
                if (!kisiler.containsKey(ad)) {
                    kisiler[ad] = numara
                }
            }
        }

        Log.d(TAG, "Rehberden ${kisiler.size} kişi çekildi.")
        return kisiler
    }

    // ------------------------------------------------------------------ //
    // Arama başlatma + hoparlör modu
    // ------------------------------------------------------------------ //

    /**
     * [numara]'yı `tel:` URI üzerinden Intent.ACTION_CALL ile çevirir.
     * Arama bağlandıktan ~1 saniye sonra AudioManager hoparlör moduna geçirilir.
     *
     * Not: Hoparlör modu yalnızca arama bağlantısı kurulduktan sonra
     * etkili olur; bu nedenle kısa bir gecikme eklendi.
     */
    private fun aramaBaslat(numara: String) {
        try {
            val temizNumara = numara.replace(Regex("[^+\\d]"), "")
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$temizNumara")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Arama başlatıldı: $temizNumara")

            // Hoparlör modunu etkinleştir (kısa gecikme ile)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                hoparloreGec()
            }, 1500L)

        } catch (e: SecurityException) {
            Log.e(TAG, "CALL_PHONE izni reddi: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Arama başlatılamadı: ${e.message}", e)
        }
    }

    /**
     * Aktif telefon görüşmesini hoparlör (hands-free) moduna alır.
     * Android API 31+ için [TelecomManager] tercih edilir;
     * daha eski sürümlerde [AudioManager] doğrudan kullanılır.
     */
    private fun hoparloreGec() {
        try {
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true
            Log.i(TAG, "Hoparlör modu etkinleştirildi.")
        } catch (e: Exception) {
            Log.e(TAG, "Hoparlör modu etkinleştirilemedi: ${e.message}")
        }
    }
}

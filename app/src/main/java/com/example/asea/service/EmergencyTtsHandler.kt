package com.example.asea.service

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.asea.data.local.AseaDatabase
import com.example.asea.data.local.DatabaseKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Acil durum senaryosunda çalışan Text-to-Speech handler'ı.
 *
 * Akış:
 * 1. [AudioManager] ile mevcut ses seviyesi kaydedilir, maksimuma çıkılır.
 * 2. Room DB'den güncel [emergency_text] okunur.
 * 3. Android TTS Türkçe dil ayarlarıyla başlatılır ve metin seslendirilir.
 * 4. Okuma tamamlandığında (veya hata alındığında) ses seviyesi eski haline getirilir.
 *
 * Tüm metot çağrıları suspend fonksiyon şeklinde tasarlanmıştır;
 * Coroutine scope ile güvenle çağrılabilir.
 */
class EmergencyTtsHandler(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyTtsHandler"
        private const val UTTERANCE_ID = "asea_emergency"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val db by lazy {
        AseaDatabase.getInstance(context, DatabaseKeyProvider.getKey(context))
    }

    // ------------------------------------------------------------------ //
    // Ana giriş noktası
    // ------------------------------------------------------------------ //

    /**
     * Acil durum sesli bildirimini başlatan suspend fonksiyon.
     * Sırayla: ses maks → DB'den metin → TTS → ses eski hali.
     */
    suspend fun acilDurumOku() {
        val oncekiSes = artirmaSesi()
        try {
            val emergencyText = fetchEmergencyText()
            val rate = fetchSpeechRate()
            Log.i(TAG, "Acil metin okunacak (${emergencyText.length} karakter) | Hız: $rate")
            konustur(emergencyText, rate)
        } finally {
            // Ses seviyesini her koşulda eski haline getir
            geriYukle(oncekiSes)
        }
    }

    /**
     * İsteğe bağlı genel TTS — ilaç listesi, sağlık geçmişi vb. için.
     * Ses seviyesini değiştirmez; sadece verilen metni seslendirir.
     */
    suspend fun oku(metin: String, overrideSpeechRate: Float? = null) {
        if (metin.isBlank()) return
        val rate = overrideSpeechRate ?: fetchSpeechRate()
        konustur(metin, rate)
    }

    // ------------------------------------------------------------------ //
    // Adım 1 — Ses maksimuma çıkar
    // ------------------------------------------------------------------ //

    /**
     * Mevcut STREAM_MUSIC ses seviyesini kaydedip maksimuma çıkarır.
     * @return Geri yüklemede kullanmak üzere orijinal ses seviyesi.
     */
    private fun artirmaSesi(): Int {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            maxVolume,
            0 // FLAG_SHOW_UI gösterme — acil durumda gereksiz UI distraction
        )
        Log.d(TAG, "Ses seviyesi: $currentVolume → $maxVolume (maks)")
        return currentVolume
    }

    /**
     * Ses seviyesini [oncekiSeviye]'e geri döndürür.
     */
    private fun geriYukle(oncekiSeviye: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, oncekiSeviye, 0)
        Log.d(TAG, "Ses seviyesi geri yüklendi: $oncekiSeviye")
    }

    // ------------------------------------------------------------------ //
    // Adım 2 — DB'den emergency_text oku
    // ------------------------------------------------------------------ //

    private suspend fun fetchEmergencyText(): String = withContext(Dispatchers.IO) {
        try {
            val ayarlar = db.kullaniciAyarlariDao.getAyarlar().first()
            ayarlar?.emergencyText
                ?.takeIf { it.isNotBlank() }
                ?: fallbackText()
        } catch (e: Exception) {
            Log.e(TAG, "DB'den acil metin alınamadı, yedek metin kullanılıyor.", e)
            fallbackText()
        }
    }

    /** DB erişimi başarısız olursa kullanılacak güvenlik metni */
    private fun fallbackText() =
        "Acil durum! Lütfen yardım edin. Ben bir Parkinson hastasıyım."

    private suspend fun fetchSpeechRate(): Float = withContext(Dispatchers.IO) {
        try {
            val ayarlar = db.kullaniciAyarlariDao.getAyarlar().first()
            ayarlar?.speechRate ?: 1.0f
        } catch (e: Exception) {
            Log.e(TAG, "DB'den konuşma hızı alınamadı, varsayılan kullanılıyor.", e)
            1.0f
        }
    }

    // ------------------------------------------------------------------ //
    // Adım 3 — TTS ile seslendir
    // ------------------------------------------------------------------ //

    /**
     * Android TextToSpeech API'sini başlatır ve [metin]'i Türkçe olarak seslendirir.
     * TTS başlatılması asenkron olduğundan [suspendCancellableCoroutine] kullanılır;
     * bu sayede coroutine, TTS hazır olana kadar bloklanmadan bekler.
     */
    private suspend fun konustur(metin: String, rate: Float) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var tts: TextToSpeech? = null

            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.ERROR) {
                    Log.e(TAG, "TTS motoru başlatılamadı.")
                    continuation.resume(Unit)
                    return@TextToSpeech
                }

                // Türkçe dil ayarı
                val dil = Locale("tr", "TR")
                val destekleniyor = tts?.isLanguageAvailable(dil) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (destekleniyor == TextToSpeech.LANG_NOT_SUPPORTED ||
                    destekleniyor == TextToSpeech.LANG_MISSING_DATA
                ) {
                    Log.w(TAG, "Türkçe TTS dil paketi bulunamadı, sistem varsayılanı kullanılıyor.")
                    tts?.language = Locale.getDefault()
                } else {
                    tts?.language = dil
                }

                // Okuma hızı ve perde — dinamik hız ayarı
                tts?.setSpeechRate(rate)
                tts?.setPitch(1.0f)

                // Konuşma tamamlanınca veya hata olunca coroutine'i devam ettir
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS okuma başladı.")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.i(TAG, "TTS okuma tamamlandı.")
                        tts?.shutdown()
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    @Deprecated("Deprecated in API 21", replaceWith = ReplaceWith("onError(utteranceId, errorCode)"))
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS okuma hatası.")
                        tts?.shutdown()
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e(TAG, "TTS okuma hatası (kod: $errorCode).")
                        tts?.shutdown()
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                })

                // Metni seslendir
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_STREAM,
                        AudioManager.STREAM_MUSIC.toString())
                }
                tts?.speak(metin, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
            }

            // Coroutine iptal edilirse TTS'i temizle
            continuation.invokeOnCancellation {
                tts?.stop()
                tts?.shutdown()
                Log.d(TAG, "TTS coroutine iptal edildi, kaynaklar temizlendi.")
            }
        }
    }
}

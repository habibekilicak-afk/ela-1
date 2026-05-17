package com.example.asea.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import com.example.asea.data.local.AseaDatabase
import com.example.asea.data.local.DatabaseKeyProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException

/**
 * ASEA Foreground Service — Vosk ile Offline Wake-Word Dinleme
 *
 * Sorumluluklar:
 * 1. Vosk modelini assets'ten veya harici depodan yükle.
 * 2. AudioRecord ile sürekli mikrofon akışını oku.
 * 3. Vosk Recognizer ile metne dönüştür.
 * 4. DB'den okunan wake_word'ü tespit ettiğinde [WakeWordEvent.WakeWordAlgilandi] yayınla.
 * 5. Tam cümle tanındığında [WakeWordEvent.KomutAlindi] yayınla (Gemini entegrasyonuna hazır).
 */
class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE_FACTOR = 4

        /** Diğer bileşenler bu flow'u collect ederek olayları dinler */
        private val _eventFlow = MutableSharedFlow<WakeWordEvent>(extraBufferCapacity = 64)
        val eventFlow: SharedFlow<WakeWordEvent> = _eventFlow.asSharedFlow()
    }

    // Servis yaşam döngüsüne bağlı coroutine scope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var voskModel: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var listenJob: Job? = null

    /** DB'den okunan uyandırma kelimesi; varsayılan "ela" */
    private var wakeWord: String = "ela"

    /** Kulaklık takılma durumunu algılayıp kullanıcıyı sesli uyaran BroadcastReceiver */
    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                if (state == 1) { // 1 = takıldı
                    Log.i(TAG, "Kulaklık takıldı! Asistan otomatik uyanıyor...")
                    serviceScope.launch(Dispatchers.Main) {
                        try {
                            val tts = EmergencyTtsHandler(context)
                            // Kulaklık takıldığında sesli soru sorarak asistanı uyandırıyoruz
                            tts.oku("Kulaklık bağlandı. Sakinleştirici bir müzik açmamı ister misiniz?")
                        } catch (e: Exception) {
                            Log.e(TAG, "Kulaklık takılma TTS uyarısı hatası", e)
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    override fun onCreate() {
        super.onCreate()
        WakeWordNotificationHelper.createNotificationChannel(this)
        startForeground(
            WakeWordNotificationHelper.NOTIFICATION_ID,
            WakeWordNotificationHelper.buildNotification(this, "Model yükleniyor…")
        )
        loadWakeWordFromDb()

        // Kulaklık takılma algılayıcısını dinamik olarak kaydet
        try {
            registerReceiver(headsetReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
            Log.i(TAG, "Kulaklık algılayıcı BroadcastReceiver başarıyla kaydedildi.")
        } catch (e: Exception) {
            Log.e(TAG, "Kulaklık algılayıcı kaydedilemedi", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Servis öldürülürse sistem yeniden başlatsın
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopListening()

        // Kulaklık algılayıcısını kaldır
        try {
            unregisterReceiver(headsetReceiver)
            Log.i(TAG, "Kulaklık algılayıcı BroadcastReceiver kaldırıldı.")
        } catch (e: Exception) {
            Log.e(TAG, "headsetReceiver kaldırma hatası", e)
        }

        emitEvent(WakeWordEvent.Durdu)
    }

    // ------------------------------------------------------------------ //
    // Adım 1 — DB'den wake_word'ü oku
    // ------------------------------------------------------------------ //

    private fun loadWakeWordFromDb() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = AseaDatabase.getInstance(applicationContext, DatabaseKeyProvider.getKey(applicationContext))
                val ayarlar = db.kullaniciAyarlariDao.getAyarlar().first()
                wakeWord = ayarlar?.wakeWord?.lowercase() ?: "ela"
                Log.d(TAG, "Wake word DB'den yüklendi: \"$wakeWord\"")
            } catch (e: Exception) {
                Log.e(TAG, "DB'den wake word alınamadı, varsayılan kullanılıyor.", e)
            } finally {
                initVoskModel()
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Adım 2 — Vosk modelini yükle (assets/vosk-model-small-tr/)
    // ------------------------------------------------------------------ //

    private fun initVoskModel() {
        emitEvent(WakeWordEvent.ModelYukleniyor)
        updateNotification("Vosk modeli yükleniyor…")

        // StorageService assets'ten modeli telefon depolama alanına kopyalar (ilk açılışta)
        StorageService.unpack(
            applicationContext,
            "vosk-model-small-tr",          // assets klasör adı
            "vosk_model",                   // hedef klasör (filesDir altında)
            { model ->
                voskModel = model
                Log.i(TAG, "Vosk modeli başarıyla yüklendi.")
                startListening()
            },
            { exception ->
                val msg = "Vosk modeli yüklenemedi: ${exception.message}"
                Log.e(TAG, msg, exception)
                emitEvent(WakeWordEvent.Hata(msg))
                updateNotification("Model yüklenemedi!")
            }
        )
    }

    // ------------------------------------------------------------------ //
    // Adım 3 — Mikrofon akışını başlat ve sürekli dinle
    // ------------------------------------------------------------------ //

    private fun startListening() {
        val model = voskModel ?: return
        recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * BUFFER_SIZE_FACTOR

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            emitEvent(WakeWordEvent.Hata("AudioRecord başlatılamadı."))
            return
        }

        audioRecord?.startRecording()
        emitEvent(WakeWordEvent.DinlemeBasladi)
        updateNotification("\"$wakeWord\" için dinleniyor…")
        Log.i(TAG, "Mikrofon dinlemesi başladı.")

        listenJob = serviceScope.launch {
            val buffer = ShortArray(bufferSize / 2)
            while (true) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    processAudio(buffer, read)
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Adım 4 — Vosk çıktısını işle, wake-word'ü ara
    // ------------------------------------------------------------------ //

    private fun processAudio(buffer: ShortArray, size: Int) {
        val rec = recognizer ?: return

        // PCM kısa dizisini bayt dizisine çevir
        val bytes = ByteArray(size * 2)
        for (i in 0 until size) {
            bytes[i * 2]     = (buffer[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte()
        }

        if (rec.acceptWaveForm(bytes, bytes.size)) {
            // Tam cümle tamamlandı
            val result = rec.result      // JSON: {"text": "..."}
            val text = parseVoskText(result)
            if (text.isNotBlank()) {
                Log.d(TAG, "Tanınan metin: \"$text\"")
                if (text.contains(wakeWord, ignoreCase = true)) {
                    emitEvent(WakeWordEvent.WakeWordAlgilandi(1.0f))
                }
                emitEvent(WakeWordEvent.KomutAlindi(text))
            }
        }
        // Kısmi sonuçlar (partial) şimdilik işlenmiyor; ileride gerçek zamanlı gösterim için kullanılabilir
    }

    /**
     * Vosk'un döndürdüğü `{"text": "merhaba dünya"}` formatından
     * sadece metni çıkartır. Hızlı bir manuel parse; JSON kütüphanesi gerektirmez.
     */
    private fun parseVoskText(json: String): String {
        val key = "\"text\""
        val keyIdx = json.indexOf(key)
        if (keyIdx == -1) return ""
        val colonIdx = json.indexOf(':', keyIdx)
        if (colonIdx == -1) return ""
        val startIdx = json.indexOf('"', colonIdx + 1)
        if (startIdx == -1) return ""
        val endIdx = json.indexOf('"', startIdx + 1)
        if (endIdx == -1) return ""
        return json.substring(startIdx + 1, endIdx).trim()
    }

    // ------------------------------------------------------------------ //
    // Yardımcı fonksiyonlar
    // ------------------------------------------------------------------ //

    private fun stopListening() {
        listenJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recognizer?.close()
        recognizer = null
        voskModel?.close()
        voskModel = null
    }

    private fun emitEvent(event: WakeWordEvent) {
        serviceScope.launch { _eventFlow.emit(event) }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            WakeWordNotificationHelper.NOTIFICATION_ID,
            WakeWordNotificationHelper.buildNotification(this, text)
        )
    }

    // Notification kanalı için import — ileride kaldırılabilir
    private val NotificationManager get() =
        getSystemService(android.app.NotificationManager::class.java)
}

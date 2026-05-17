package com.example.asea.service

import android.content.Context
import android.util.Log
import com.example.asea.data.local.AseaDatabase
import com.example.asea.data.local.DatabaseKeyProvider
import com.example.asea.data.local.MultiLlmApiKeyProvider
import com.example.asea.data.local.entity.SaglikGecmisiEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * WakeWordService'ten gelen [WakeWordEvent]'leri dinler,
 * [CommandParser] yardımıyla uygun [VoiceCommand]'a dönüştürür
 * ve ilgili handler'a yönlendirir.
 *
 * Bağlı handler'lar:
 * - [EmergencyTtsHandler] → acil durum TTS + ses maks (Adım 4 ✅)
 * - ContactsHandler        → sesli arama (Adım 5 — stub)
 * - GeminiHandler          → LLM entegrasyonu (Adım 6 — stub)
 */
class CommandProcessor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "CommandProcessor"
    }

    private val db by lazy {
        AseaDatabase.getInstance(context, DatabaseKeyProvider.getKey(context))
    }

    /** EmergencyTtsHandler — acil durum sesli bildirimi */
    private val emergencyTts by lazy { EmergencyTtsHandler(context) }

    /** ContactsHandler — fuzzy matching + hoparlörlü arama (Adım 5 ✅) */
    private val contactsHandler by lazy { ContactsHandler(context, emergencyTts) }

    /** GeminiHandler — LLM tabanlı doğal dil işleme (Adım 6 ✅) */
    private val geminiHandler by lazy { GeminiHandler(context, emergencyTts) }

    /** EmergencySmsHandler — Konum tabanlı acil durum SMS'i (F-07 ✅) */
    private val emergencySmsHandler by lazy { EmergencySmsHandler(context, emergencyTts) }

    /** CbtHandler — BDT Sakinleşme Modu (F-06 ✅) */
    private val cbtHandler by lazy { CbtHandler(context, emergencyTts) }

    /** Wake-word algılandıktan sonra komut bekleme durumu */
    private var awaitingCommand = false

    // ------------------------------------------------------------------ //
    // Başlatma
    // ------------------------------------------------------------------ //

    fun start() {
        WakeWordService.eventFlow
            .onEach { event -> handleEvent(event) }
            .launchIn(scope)

        Log.i(TAG, "CommandProcessor başlatıldı, servis olayları dinleniyor.")
    }

    // ------------------------------------------------------------------ //
    // Olay işleyici
    // ------------------------------------------------------------------ //

    private suspend fun handleEvent(event: WakeWordEvent) {
        when (event) {
            is WakeWordEvent.WakeWordAlgilandi -> {
                Log.i(TAG, "Wake-word algılandı! Komut bekleniyor…")
                awaitingCommand = true
            }

            is WakeWordEvent.KomutAlindi -> {
                if (!awaitingCommand) return

                val wakeWord = fetchWakeWord()
                val komutMetni = event.metin
                    .lowercase()
                    .substringAfter(wakeWord)
                    .trim()

                if (komutMetni.isBlank()) return

                val komut = CommandParser.parse(komutMetni)
                Log.d(TAG, "Komut ayrıştırıldı: $komut")
                dispatch(komut, event.metin)
                awaitingCommand = false
            }

            is WakeWordEvent.Hata -> {
                Log.e(TAG, "Servis hatası: ${event.mesaj}")
                awaitingCommand = false
            }

            is WakeWordEvent.Durdu -> {
                Log.i(TAG, "WakeWordService durdu.")
                awaitingCommand = false
            }

            else -> Unit
        }
    }

    // ------------------------------------------------------------------ //
    // Komut yönlendirici
    // ------------------------------------------------------------------ //

    private fun dispatch(komut: VoiceCommand, hamMetin: String) {
        // Her dispatch kendi coroutine'inde çalışır; uzun TTS servisi bloklamaz
        scope.launch(Dispatchers.Main) {
            when (komut) {
                // ──── Adım 4 ✅ ────
                is VoiceCommand.AcilDurum -> {
                    Log.i(TAG, "[ACİL DURUM] TTS başlatılıyor…")
                    saglikKaydiEkle("Kriz", "Acil durum komutu tetiklendi: \"$hamMetin\"")
                    // F-07: Arka planda paralel olarak GPS konumunu alıp acil SMS'i gönder
                    scope.launch(Dispatchers.IO) {
                        emergencySmsHandler.gonderAcilSms()
                    }
                    // Çevreye acil durum metnini oku
                    emergencyTts.acilDurumOku()
                }

                // ──── Adım 5 ✅ ────
                is VoiceCommand.AraKisi -> {
                    Log.i(TAG, "[ARAMA] Kişi: ${komut.kisimAdi}")
                    contactsHandler.araKisi(komut.kisimAdi)
                }

                // ──── Sağlık notu ────
                is VoiceCommand.SaglikNotuKaydet -> {
                    saglikKaydiEkle("Not", komut.icerik)
                    Log.i(TAG, "[NOT] Sağlık notu kaydedildi.")
                    emergencyTts.oku("Notunuz kaydedildi.")
                }

                // ──── İlaç listele (Adım 4 kapsamı) ────
                is VoiceCommand.IlaclariListele -> {
                    val ilaclar = db.ilacTakipDao.getAllIlaclar().first()
                    if (ilaclar.isEmpty()) {
                        emergencyTts.oku("Kayıtlı ilaç bulunamadı.")
                    } else {
                        val metin = ilaclar.joinToString(". ") { ilac ->
                            "${ilac.ilacAdi}, ${ilac.dozaj}, saat ${ilac.hatirlatmaSaati}"
                        }
                        emergencyTts.oku("İlaçlarınız: $metin")
                    }
                }

                // ──── Sağlık geçmişi listele (Adım 4 kapsamı) ────
                is VoiceCommand.SaglikGecmisiniListele -> {
                    val gecmis = db.saglikGecmisiDao.getAllSaglikGecmisi().first()
                    if (gecmis.isEmpty()) {
                        emergencyTts.oku("Kayıtlı sağlık geçmişi bulunamadı.")
                    } else {
                        val son3 = gecmis.take(3)
                        val metin = son3.joinToString(". ") { kayit ->
                            "${kayit.kayitTipi}: ${kayit.icerik}"
                        }
                        emergencyTts.oku("Son kayıtlarınız: $metin")
                    }
                }

                // ──── Adım 6 ✅ ────
                is VoiceCommand.Bilinmiyor -> {
                    Log.i(TAG, "[GEMINI] Ham metin işleniyor: \"${komut.hamMetin}\"")
                    geminiHandler.isle(komut.hamMetin)
                }

                is VoiceCommand.IlacEkle -> {
                    Log.i(TAG, "[GEMINI] İlaç ekleme komutu Gemini'ye iletiliyor.")
                    // İlaç ekleme niyeti zaten Gemini aracılığıyla işlenir
                    geminiHandler.isle(
                        "${komut.ilacAdi} isimli ilacı, ${komut.dozaj} dozunda, saat ${komut.saat}'de ekle."
                    )
                }

                // ──── F-06 BDT Sakinleşme Modu ✅ ────
                is VoiceCommand.BdtModu -> {
                    Log.i(TAG, "[BDT SAKİNLEŞME] Egzersiz başlatılıyor.")
                    cbtHandler.sakinlestir(hamMetin)
                }

                // ──── Parkinson & Anksiyete Dostu Müzik Çalma ✅ ────
                is VoiceCommand.MuzikCal -> {
                    Log.i(TAG, "[MÜZİK ÇAL] Dosya: ${komut.dosyaAdi}")
                    saglikKaydiEkle("Müzik", "Müzik çalındı: ${komut.dosyaAdi} (Komut: \"$hamMetin\")")
                    AudioPlayerHandler.playAsset(context, komut.dosyaAdi)
                }

                is VoiceCommand.MuzikDurdur -> {
                    Log.i(TAG, "[MÜZİK DURDUR] Müzik durduruluyor.")
                    saglikKaydiEkle("Müzik", "Müzik durduruldu.")
                    AudioPlayerHandler.stop()
                }

                // ──── Çoklu LLM Tıbbi Araştırma (Perplexity & DeepL) ✅ ────
                is VoiceCommand.ArastirmaYap -> {
                    Log.i(TAG, "[ARAŞTIRMA] Sorgu: ${komut.hamMetin}")
                    emergencyTts.oku("İnternet üzerinden en son tıbbi makaleleri ve araştırmaları tarıyorum. Lütfen bekleyin...")
                    scope.launch(Dispatchers.Main) {
                        try {
                            val perplexityHandler = PerplexityHandler(context)
                            val deepLHandler = DeepLHandler(context)
                            
                            // Perplexity'den güncel makaleleri ve kaynakları ara
                            var arastirmaSonucu = perplexityHandler.search(komut.hamMetin)
                            
                            if (arastirmaSonucu.isNotBlank()) {
                                // Eğer DeepL anahtarı tanımlanmışsa, bilimsel bütünlüğü koruyarak Türkçe akademik çeviri yap
                                val deepLKey = MultiLlmApiKeyProvider.getDeepLKey(context)
                                if (deepLKey.isNotBlank()) {
                                    arastirmaSonucu = deepLHandler.translateToTurkish(arastirmaSonucu)
                                }
                                
                                Log.i(TAG, "Tıbbi araştırma başarıyla sonuçlandı.")
                                emergencyTts.oku("Araştırma Sonucu: $arastirmaSonucu")
                                
                                // Uzun vadeli hafızaya bu araştırma sonucunu asenkron olarak kaydet
                                MemoryManager.updateProfile(context, komut.hamMetin, arastirmaSonucu)
                                
                                // Sağlık geçmişine olay kaydı ekle
                                saglikKaydiEkle("Araştırma", "Tıbbi araştırma yapıldı: ${komut.hamMetin}")
                            } else {
                                Log.w(TAG, "Perplexity boş sonuç döndü. Gemini fall-back devreye giriyor.")
                                geminiHandler.isle(komut.hamMetin)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Araştırma işlemi sırasında hata", e)
                            geminiHandler.isle(komut.hamMetin)
                        }
                    }
                }

                // ──── Şık Kronolojik Doktor Raporu Üretimi ✅ ────
                is VoiceCommand.DoktorRaporuUret -> {
                    Log.i(TAG, "[DOKTOR RAPORU] Rapor oluşturma işlemi tetiklendi.")
                    emergencyTts.oku("Günlük notlarınızı ve tüm kriz geçmişinizi kronolojik bir doktor raporu belgesine dönüştürüyorum.")
                    scope.launch(Dispatchers.Main) {
                        try {
                            val reportFile = ReportGenerator.generateReport(context)
                            if (reportFile != null) {
                                val basariMesaji = "Doktor raporunuz başarıyla oluşturuldu. Belgeniz cihaz hafızasına ${reportFile.name} adıyla kaydedildi."
                                Log.i(TAG, "Rapor başarıyla oluşturuldu: ${reportFile.absolutePath}")
                                emergencyTts.oku(basariMesaji)
                                saglikKaydiEkle("Sistem", "Doktor raporu oluşturuldu.")
                            } else {
                                emergencyTts.oku("Maalesef rapor belgesi oluşturulurken bir sistem hatası meydana geldi.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Doktor raporu oluşturulurken hata", e)
                            emergencyTts.oku("Maalesef rapor belgesi oluşturulurken bir hata oluştu.")
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Yardımcı
    // ------------------------------------------------------------------ //

    private suspend fun saglikKaydiEkle(tip: String, icerik: String) {
        db.saglikGecmisiDao.insertSaglikKaydi(
            SaglikGecmisiEntity(
                kayitTipi = tip,
                icerik = icerik,
                kayitTarihi = System.currentTimeMillis()
            )
        )
    }

    private suspend fun fetchWakeWord(): String = try {
        db.kullaniciAyarlariDao.getAyarlar().first()?.wakeWord?.lowercase() ?: "ela"
    } catch (e: Exception) {
        "ela"
    }
}

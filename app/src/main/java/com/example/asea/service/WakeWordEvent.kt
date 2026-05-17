package com.example.asea.service

/**
 * WakeWordService'in dış dünyaya yayınladığı olaylar.
 * Bu sealed class, servis → UI / diğer servisler arası
 * iletişimi tip-güvenli biçimde sağlar.
 */
sealed class WakeWordEvent {
    /** Vosk modeli yüklenirken fırlatılır */
    object ModelYukleniyor : WakeWordEvent()

    /** Vosk modeli başarıyla yüklendi, dinleme başladı */
    object DinlemeBasladi : WakeWordEvent()

    /** Wake-word (ör. "Ela") algılandı */
    data class WakeWordAlgilandi(val algilamanMetre: Float) : WakeWordEvent()

    /** Tanınan tam cümle (NLP için Gemini'ye gönderilecek) */
    data class KomutAlindi(val metin: String) : WakeWordEvent()

    /** Hata durumu */
    data class Hata(val mesaj: String) : WakeWordEvent()

    /** Servis durduruldu */
    object Durdu : WakeWordEvent()
}

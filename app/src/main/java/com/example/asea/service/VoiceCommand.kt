package com.example.asea.service

/**
 * Tanınan sesli komutun ne anlama geldiğini temsil eder.
 * WakeWordService → CommandProcessor → ilgili handler zinciri.
 */
sealed class VoiceCommand {
    /** Acil durum: TTS ile acil metni oku + ses maks yap */
    object AcilDurum : VoiceCommand()

    /** Rehberden kişi ara */
    data class AraKisi(val kisimAdi: String) : VoiceCommand()

    /** Yeni ilaç kaydı ekle */
    data class IlacEkle(val ilacAdi: String, val dozaj: String, val saat: String) : VoiceCommand()

    /** Sağlık notu kaydet */
    data class SaglikNotuKaydet(val icerik: String) : VoiceCommand()

    /** İlaç listesini sesli oku */
    object IlaclariListele : VoiceCommand()

    /** Sağlık geçmişini sesli oku */
    object SaglikGecmisiniListele : VoiceCommand()

    /** F-06: BDT Sakinleşme Modu */
    object BdtModu : VoiceCommand()

    /** Parkinson / Anksiyete Dostu Müzik Çal */
    data class MuzikCal(val dosyaAdi: String) : VoiceCommand()

    /** Müziği Durdur */
    object MuzikDurdur : VoiceCommand()

    /** Tıbbi/Medikal Makale Araştırması Yap */
    data class ArastirmaYap(val hamMetin: String) : VoiceCommand()

    /** Doktor Raporu Üret */
    object DoktorRaporuUret : VoiceCommand()

    /** Komut tanınamadı; ham metin Gemini'ye gönderilecek */
    data class Bilinmiyor(val hamMetin: String) : VoiceCommand()
}

package com.example.asea.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gemini'nin döndürdüğü yapılandırılmış niyet (intent) modeli.
 *
 * Gemini'ye gönderilen sistem prompt'u, yanıtı her zaman bu JSON yapısında
 * döndürmesini zorunlu kılar. Bu sayede parse işlemi güvenli ve tutarlıdır.
 *
 * Örnek Gemini çıktısı:
 * ```json
 * {
 *   "niyet": "ilac_ekle",
 *   "ilac_adi": "Metoprolol",
 *   "dozaj": "50mg",
 *   "saat": "08:00",
 *   "not_icerigi": null,
 *   "yanit_metni": "Metoprolol ilacınız sabah 08:00 için eklendi."
 * }
 * ```
 */
@Serializable
data class GeminiIntent(
    /** Tespit edilen niyet. Olası değerler: ilac_ekle, saglik_notu, sorgu, belirsiz */
    @SerialName("niyet") val niyet: String,

    /** [niyet] = "ilac_ekle" ise ilaç adı */
    @SerialName("ilac_adi") val ilacAdi: String? = null,

    /** [niyet] = "ilac_ekle" ise dozaj bilgisi (ör. "100mg") */
    @SerialName("dozaj") val dozaj: String? = null,

    /** [niyet] = "ilac_ekle" ise hatırlatma saati (ör. "08:00") */
    @SerialName("saat") val saat: String? = null,

    /** [niyet] = "saglik_notu" ise not içeriği */
    @SerialName("not_icerigi") val notIcerigi: String? = null,

    /** Kullanıcıya TTS ile okunacak Türkçe yanıt cümlesi */
    @SerialName("yanit_metni") val yanitMetni: String
)

package com.example.asea.util

/**
 * Levenshtein Distance tabanlı Fuzzy (yaklaşık) metin eşleştirme yardımcısı.
 *
 * Parkinson hastaları titreme veya artikülasyon sorunları nedeniyle isimleri
 * tam telaffuz edemeyebilir. Bu algoritma;
 *   - "Ahmet" yerine "amet", "ahmed" gibi değişkenleri,
 *   - "Ayşe" yerine "ayşi", "eyşe" gibi yakın sesleri
 *   tolere eder.
 *
 * Kullanım:
 * ```kotlin
 * val en_yakin = FuzzyMatcher.enYakinEslesme("amet", listOf("Ahmet", "Mehmet", "Ali"))
 * // → Pair("Ahmet", 0.80f)
 * ```
 */
object FuzzyMatcher {

    /**
     * [sorgu] ile [adaylar] listesindeki her string arasındaki
     * Levenshtein benzerlik skorunu hesaplar ve en yüksek skorlu
     * adayı döndürür.
     *
     * @param sorgu      Sesli komuttan gelen (ham, küçük harfli) isim.
     * @param adaylar    Rehberden çekilen kişi adları listesi.
     * @param esikDeger  0..1 arası minimum kabul edilebilir benzerlik skoru.
     *                   Varsayılan 0.55 — titrek telaffuz için biraz gevşek tutuldu.
     *
     * @return En iyi eşleşme ve skoru, ya da eşik altındaysa null.
     */
    fun enYakinEslesme(
        sorgu: String,
        adaylar: List<String>,
        esikDeger: Float = 0.55f
    ): Pair<String, Float>? {
        if (adaylar.isEmpty() || sorgu.isBlank()) return null

        val normalSorgu = normalize(sorgu)

        var enIyiAday = ""
        var enIyiSkor = -1f

        adaylar.forEach { aday ->
            val normalAday = normalize(aday)

            // Tam alt-string eşleşmesi varsa doğrudan yüksek skor ver
            val altStringSkoru = if (normalAday.contains(normalSorgu) ||
                normalSorgu.contains(normalAday)) 0.90f else -1f

            val levenSkor = levenshteinBenzerlik(normalSorgu, normalAday)
            val nihai = maxOf(levenSkor, altStringSkoru)

            if (nihai > enIyiSkor) {
                enIyiSkor = nihai
                enIyiAday = aday  // Orijinal (büyük harf korunmuş) adı sakla
            }
        }

        return if (enIyiSkor >= esikDeger) Pair(enIyiAday, enIyiSkor) else null
    }

    // ------------------------------------------------------------------ //
    // Levenshtein Similarity
    // ------------------------------------------------------------------ //

    /**
     * İki string arasında 0.0 (tamamen farklı) — 1.0 (aynı) arasında
     * normalleştirilmiş benzerlik skoru döndürür.
     *
     * Formül: 1 - (distance / max(len_a, len_b))
     */
    fun levenshteinBenzerlik(a: String, b: String): Float {
        val mesafe = levenshteinMesafe(a, b)
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0f
        return 1f - (mesafe.toFloat() / maxLen.toFloat())
    }

    /**
     * Klasik Levenshtein (edit distance) algoritması.
     * Zaman: O(m×n), Bellek: O(min(m,n)) — tek satır optimizasyonu.
     */
    private fun levenshteinMesafe(a: String, b: String): Int {
        // Kısa olanı sütun ekseni yap (bellek optimizasyonu)
        val (kisa, uzun) = if (a.length <= b.length) Pair(a, b) else Pair(b, a)
        val n = kisa.length
        val m = uzun.length

        var oncekiSatir = IntArray(n + 1) { it }
        var suankiSatir = IntArray(n + 1)

        for (i in 1..m) {
            suankiSatir[0] = i
            for (j in 1..n) {
                val eklemeMaliyeti = suankiSatir[j - 1] + 1
                val silmeMaliyeti  = oncekiSatir[j] + 1
                val degistirmeMaliyeti = oncekiSatir[j - 1] +
                    if (uzun[i - 1] == kisa[j - 1]) 0 else 1
                suankiSatir[j] = minOf(eklemeMaliyeti, silmeMaliyeti, degistirmeMaliyeti)
            }
            // Satırları değiş-tokuş et (yeni nesne oluşturmadan)
            val temp = oncekiSatir
            oncekiSatir = suankiSatir
            suankiSatir = temp
        }
        return oncekiSatir[n]
    }

    // ------------------------------------------------------------------ //
    // Normalleştirme
    // ------------------------------------------------------------------ //

    /**
     * Türkçe karakterleri ASCII karşılıklarına çevirir ve küçük harfe indirger.
     * Bu adım olmadan "şükrü" ↔ "sukru" gibi çiftler çok düşük skor alır.
     */
    private fun normalize(s: String): String = s
        .lowercase()
        .replace('ş', 's').replace('ı', 'i').replace('ğ', 'g')
        .replace('ç', 'c').replace('ö', 'o').replace('ü', 'u')
        .replace('â', 'a').replace('î', 'i').replace('û', 'u')
        .trim()
}

package com.example.asea.data.local

import com.example.asea.data.local.entity.IlacTakipEntity
import com.example.asea.data.local.entity.KullaniciAyarlariEntity
import com.example.asea.data.local.entity.SaglikGecmisiEntity

/**
 * Uygulama ilk kez kurulduğunda Room Database'e yazılacak
 * varsayılan (seed) verileri tanımlayan nesne.
 *
 * Tüm metinler hard-coded olmadığından, kullanıcı daha sonra
 * Ayarlar ekranından bunları değiştirebilir.
 */
object DatabaseSeeder {

    /** Varsayılan kullanıcı ayarları (sadece 1 satır, id=1 ile) */
    fun defaultKullaniciAyarlari() = KullaniciAyarlariEntity(
        id = 1,
        wakeWord = "Ela",
        emergencyText = "Merhaba, ben şu an geçici bir Parkinson/stres atağı geçiriyorum. " +
            "Lütfen panik yapmayın. Ambulans çağrılmasına veya hastaneye gidilmesine gerek yoktur. " +
            "Çevredeki aşırı stres ve kalabalık durumumu daha da zorlaştırabilir. " +
            "Sadece sakinleşene kadar güvenli ve sessiz bir alanda kalmama yardımcı olabilirsiniz. " +
            "Teşekkür ederim.",
        emergencyContactNumber = "",
        geminiApiKey = "",
        volumeLevel = 100,
        speechRate = 1.0f
    )

    /** Örnek başlangıç ilaçları – kullanıcı bunları uygulama üzerinden değiştirebilir */
    fun defaultIlaclar() = listOf(
        IlacTakipEntity(
            ilacAdi = "Levodopa",
            dozaj = "100mg",
            hatirlatmaSaati = "08:00",
            aktifMi = true
        ),
        IlacTakipEntity(
            ilacAdi = "Karvidilol",
            dozaj = "25mg",
            hatirlatmaSaati = "20:00",
            aktifMi = true
        )
    )

    /** İlk sağlık geçmişi kaydı – uygulamanın başlatıldığını belgeler */
    fun defaultSaglikGecmisi() = listOf(
        SaglikGecmisiEntity(
            kayitTipi = "Sistem",
            icerik = "ASEA uygulaması ilk kez başlatıldı. Hoş geldiniz!",
            kayitTarihi = System.currentTimeMillis()
        )
    )
}

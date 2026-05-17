package com.example.asea.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kullanici_ayarlari")
data class KullaniciAyarlariEntity(
    @PrimaryKey val id: Int = 1,
    val wakeWord: String = "Ela",
    val emergencyText: String = "Merhaba, ben şu an geçici bir Parkinson/stres atağı geçiriyorum. Lütfen panik yapmayın. " +
        "Ambulans çağrılmasına veya hastaneye gidilmesine gerek yoktur. Çevredeki aşırı stres ve " +
        "kalabalık durumumu daha da zorlaştırabilir. Sadece sakinleşene kadar güvenli ve sessiz bir " +
        "alanda kalmama yardımcı olabilirsiniz. Teşekkür ederim.",
    val emergencyContactNumber: String = "",
    val geminiApiKey: String = "",
    val volumeLevel: Int = 100,
    val speechRate: Float = 1.0f,
    val userProfileJson: String = "{}"
)

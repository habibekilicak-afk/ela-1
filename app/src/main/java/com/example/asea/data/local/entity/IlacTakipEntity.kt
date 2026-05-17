package com.example.asea.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ilac_takip")
data class IlacTakipEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ilacAdi: String,
    val dozaj: String,
    val hatirlatmaSaati: String, // HH:mm formatında (ör: "09:00")
    val aktifMi: Boolean = true
)

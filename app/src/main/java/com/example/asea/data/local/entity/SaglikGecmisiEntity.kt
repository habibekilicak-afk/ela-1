package com.example.asea.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saglik_gecmisi")
data class SaglikGecmisiEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val kayitTipi: String, // Ör: "Kriz", "İlaç Alındı", "Not"
    val icerik: String,
    val kayitTarihi: Long // Timestamp olarak tutulacak
)

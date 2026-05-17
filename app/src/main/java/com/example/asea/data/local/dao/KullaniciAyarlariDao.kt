package com.example.asea.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.asea.data.local.entity.KullaniciAyarlariEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KullaniciAyarlariDao {
    @Query("SELECT * FROM kullanici_ayarlari WHERE id = 1")
    fun getAyarlar(): Flow<KullaniciAyarlariEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(ayarlar: KullaniciAyarlariEntity)
}

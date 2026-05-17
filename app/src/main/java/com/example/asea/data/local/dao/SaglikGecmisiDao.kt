package com.example.asea.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.asea.data.local.entity.SaglikGecmisiEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaglikGecmisiDao {
    @Query("SELECT * FROM saglik_gecmisi ORDER BY kayitTarihi DESC")
    fun getAllSaglikGecmisi(): Flow<List<SaglikGecmisiEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaglikKaydi(kayit: SaglikGecmisiEntity)

    @Delete
    suspend fun deleteSaglikKaydi(kayit: SaglikGecmisiEntity)
}

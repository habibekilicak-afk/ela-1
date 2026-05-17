package com.example.asea.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.asea.data.local.entity.IlacTakipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IlacTakipDao {
    @Query("SELECT * FROM ilac_takip")
    fun getAllIlaclar(): Flow<List<IlacTakipEntity>>

    @Query("SELECT * FROM ilac_takip WHERE aktifMi = 1")
    fun getAktifIlaclar(): Flow<List<IlacTakipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIlac(ilac: IlacTakipEntity)

    @Update
    suspend fun updateIlac(ilac: IlacTakipEntity)

    @Delete
    suspend fun deleteIlac(ilac: IlacTakipEntity)
}

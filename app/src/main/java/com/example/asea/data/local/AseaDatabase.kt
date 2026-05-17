package com.example.asea.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.asea.data.local.dao.IlacTakipDao
import com.example.asea.data.local.dao.KullaniciAyarlariDao
import com.example.asea.data.local.dao.SaglikGecmisiDao
import com.example.asea.data.local.entity.IlacTakipEntity
import com.example.asea.data.local.entity.KullaniciAyarlariEntity
import com.example.asea.data.local.entity.SaglikGecmisiEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import androidx.room.migration.Migration

@Database(
    entities = [
        KullaniciAyarlariEntity::class,
        IlacTakipEntity::class,
        SaglikGecmisiEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AseaDatabase : RoomDatabase() {

    abstract val kullaniciAyarlariDao: KullaniciAyarlariDao
    abstract val ilacTakipDao: IlacTakipDao
    abstract val saglikGecmisiDao: SaglikGecmisiDao

    companion object {
        @Volatile
        private var INSTANCE: AseaDatabase? = null

        /**
         * Migration from version 1 to 2.
         * Adds emergencyContactNumber, geminiApiKey, and speechRate to kullanici_ayarlari table.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kullanici_ayarlari ADD COLUMN emergencyContactNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE kullanici_ayarlari ADD COLUMN geminiApiKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE kullanici_ayarlari ADD COLUMN speechRate REAL NOT NULL DEFAULT 1.0")
            }
        }

        /**
         * Migration from version 2 to 3.
         * Adds userProfileJson to kullanici_ayarlari table for Long-Term Memory context.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kullanici_ayarlari ADD COLUMN userProfileJson TEXT NOT NULL DEFAULT '{}'")
            }
        }

        /**
         * Veritabanı yalnızca ilk kez oluşturulduğunda tetiklenen Callback.
         * Singleton INSTANCE henüz atanmadığı için DAO'lar doğrudan kullanılamaz;
         * bunun yerine `SupportSQLiteDatabase` üzerinden ham INSERT çalıştırıyoruz.
         * Bu yaklaşım, coroutine bağımlılığı olmadan güvenli seed sağlar.
         */
        private val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Seeding işlemini IO thread'inde arka planda çalıştır
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        seedDatabase(database)
                    }
                }
            }
        }

        /**
         * Varsayılan verileri DAO'lar aracılığıyla veritabanına yazar.
         * INSTANCE atandıktan hemen sonra çağrıldığı için güvenlidir.
         */
        private suspend fun seedDatabase(db: AseaDatabase) {
            // 1) Kullanıcı ayarları
            db.kullaniciAyarlariDao.insertOrUpdate(
                DatabaseSeeder.defaultKullaniciAyarlari()
            )

            // 2) Örnek başlangıç ilaçları
            DatabaseSeeder.defaultIlaclar().forEach { ilac ->
                db.ilacTakipDao.insertIlac(ilac)
            }

            // 3) İlk sağlık geçmişi kaydı
            DatabaseSeeder.defaultSaglikGecmisi().forEach { kayit ->
                db.saglikGecmisiDao.insertSaglikKaydi(kayit)
            }
        }

        fun getInstance(context: Context, passphrase: ByteArray): AseaDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportOpenHelperFactory(passphrase)
                Room.databaseBuilder(
                    context.applicationContext,
                    AseaDatabase::class.java,
                    "asea_encrypted.db"
                )
                    .openHelperFactory(factory)
                    .addCallback(seedCallback)   // ← İlk açılışta seed tetiklenir
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // ← Migration support v3
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

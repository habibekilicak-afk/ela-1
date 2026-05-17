package com.example.asea

import android.app.Application
import com.example.asea.data.local.AseaDatabase
import com.example.asea.data.local.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * ASEA Application sınıfı.
 *
 * Sorumluluklar:
 * 1. SQLCipher native kütüphanesini uygulama genelinde yükler.
 * 2. Room + SQLCipher veritabanını (singleton) oluşturur;
 *    ilk açılışta [DatabaseSeeder] aracılığıyla başlangıç verileri yazılır.
 */
class AseaApplication : Application() {

    /** Uygulama genelinde erişilebilen DB referansı */
    lateinit var database: AseaDatabase
        private set

    override fun onCreate() {
        super.onCreate()

        // 1) SQLCipher native lib'ini yükle — Room.openHelperFactory'dan önce çağrılmalı
        SQLiteDatabase.loadLibs(this)

        // 2) Şifrelenmiş DB'yi başlat (ilk açılışta seed tetiklenir)
        val key = DatabaseKeyProvider.getKey(this)
        database = AseaDatabase.getInstance(this, key)
    }
}

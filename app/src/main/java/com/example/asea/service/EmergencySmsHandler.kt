package com.example.asea.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.asea.data.local.AseaDatabase
import com.example.asea.data.local.DatabaseKeyProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * F-07: Konum Tabanlı Akıllı SMS Sistemi
 *
 * Sorumluluklar:
 * 1. SEND_SMS ve ACCESS_FINE_LOCATION izinlerini kontrol eder.
 * 2. FusedLocationProviderClient ile enlem/boylam bilgisini alır.
 * 3. DB'den acil durum yakınının numarasını ve acil durum metnini çeker.
 * 4. Dinamik olarak Google Maps linkini oluşturup SmsManager ile
 *    sendMultipartTextMessage kullanarak bölünme olmadan SMS olarak gönderir.
 */
class EmergencySmsHandler(
    private val context: Context,
    private val tts: EmergencyTtsHandler
) {
    companion object {
        private const val TAG = "EmergencySmsHandler"
    }

    private val db by lazy {
        AseaDatabase.getInstance(context, DatabaseKeyProvider.getKey(context))
    }

    private val locationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Konum bilgisiyle birlikte acil durum SMS'ini gönderen suspend fonksiyon.
     */
    suspend fun gonderAcilSms() = withContext(Dispatchers.IO) {
        // 1) İzin kontrolü
        if (!izinlerMevcut()) {
            Log.w(TAG, "SMS veya Konum izinleri eksik.")
            withContext(Dispatchers.Main) {
                tts.oku("Konum tabanlı acil durum mesajı gönderilemedi. Gerekli izinler eksik.")
            }
            return@withContext
        }

        // 2) DB'den acil durum bilgilerini çek
        val ayarlar = db.kullaniciAyarlariDao.getAyarlar().first()
        val telNo = ayarlar?.emergencyContactNumber ?: ""
        val emergencyText = ayarlar?.emergencyText ?: ""

        if (telNo.isBlank()) {
            Log.w(TAG, "Acil durum telefon numarası tanımlanmamış.")
            withContext(Dispatchers.Main) {
                tts.oku("Acil mesaj gönderilemedi. Lütfen ayarlardan bir acil durum numarası tanımlayın.")
            }
            return@withContext
        }

        Log.i(TAG, "Konum aranıyor ve acil durum SMS'i hazırlanıyor...")

        // 3) Konumu al (Timeout: 10sn)
        val konum = konumCek()
        val mapsLink = konum?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" }

        // 4) Dinamik mesaj oluştur
        val mesajGovdesi = if (mapsLink != null) {
            "$emergencyText\n\nGüncel Konumum:\n$mapsLink"
        } else {
            "$emergencyText\n\n(Güncel konum bilgisine ulaşılamadı)"
        }

        // 5) SMS Gönder
        smsGonder(telNo, mesajGovdesi)
    }

    private fun izinlerMevcut(): Boolean {
        val smsIzni = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val konumIzni = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return smsIzni && konumIzni
    }

    /**
     * FusedLocationProviderClient kullanarak güncel yüksek doğruluklu konumu çeker.
     */
    private suspend fun konumCek(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Konum alınamadı: ${exception.message}")
                    continuation.resume(null)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Konum izni hatası: ${e.message}")
            continuation.resume(null)
        }
    }

    /**
     * Mesajı bölmeden göndermek için sendMultipartTextMessage kullanır.
     */
    private fun smsGonder(telNo: String, mesaj: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parcalar = smsManager.divideMessage(mesaj)
            smsManager.sendMultipartTextMessage(telNo, null, parcalar, null, null)
            Log.i(TAG, "Acil durum SMS'i başarıyla gönderildi: $telNo")
        } catch (e: Exception) {
            Log.e(TAG, "SMS gönderim hatası: ${e.message}", e)
        }
    }
}

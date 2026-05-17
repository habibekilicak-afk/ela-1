package com.example.asea

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.asea.presentation.MainScreen
import com.example.asea.presentation.MainViewModel
import com.example.asea.service.CommandProcessor
import com.example.asea.service.WakeWordService

/**
 * ASEA Giriş Noktası (MainActivity)
 *
 * Sorumluluklar:
 * 1. Çalışma zamanı izinlerini (Mikrofon, Arama, Rehber, Konum, SMS) yönetir.
 * 2. Gerekli izinler alındığında [WakeWordService] Foreground Servisini başlatır.
 * 3. [CommandProcessor]'ı başlatarak sesli komut dinleyicilerini aktif hale getirir.
 * 4. Jetpack Compose [MainScreen] arayüzünü yükler.
 */
class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()
    private var commandProcessor: CommandProcessor? = null

    // Talep edilecek tüm izinler
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.SEND_SMS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordAudioGranted) {
            Log.i("MainActivity", "Mikrofon izni onaylandı. Servis başlatılıyor...")
            servisBaslat()
        } else {
            Log.w("MainActivity", "Mikrofon izni reddedildi. Sesli asistan çalışmayacak.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Sesli komut işleyici koordinatörünü başlat
        commandProcessor = CommandProcessor(applicationContext, lifecycleScope)
        commandProcessor?.start()

        // 2) İzinleri kontrol et ve talep et
        izinleriYonet()

        // 3) Jetpack Compose Arayüzünü set et
        setContent {
            MainScreen(viewModel)
        }
    }

    private fun izinleriYonet() {
        val eksikIzinler = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (eksikIzinler.isNotEmpty()) {
            Log.i("MainActivity", "Eksik izinler talep ediliyor: $eksikIzinler")
            permissionLauncher.launch(eksikIzinler.toTypedArray())
        } else {
            Log.i("MainActivity", "Tüm izinler zaten verilmiş. Servis başlatılıyor...")
            servisBaslat()
        }
    }

    private fun servisBaslat() {
        try {
            val intent = Intent(this, WakeWordService::class.java)
            ContextCompat.startForegroundService(this, intent)
            Log.i("MainActivity", "WakeWordService başarıyla başlatıldı.")
        } catch (e: Exception) {
            Log.e("MainActivity", "Servis başlatma hatası: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Gerekirse kaynak temizleme yapılabilir
    }
}

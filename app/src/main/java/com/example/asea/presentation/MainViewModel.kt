package com.example.asea.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.asea.data.local.AseaDatabase
import com.example.asea.data.local.DatabaseKeyProvider
import com.example.asea.data.local.entity.IlacTakipEntity
import com.example.asea.data.local.entity.KullaniciAyarlariEntity
import com.example.asea.data.local.entity.SaglikGecmisiEntity
import com.example.asea.service.CbtHandler
import com.example.asea.service.EmergencySmsHandler
import com.example.asea.service.EmergencyTtsHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val db by lazy {
        AseaDatabase.getInstance(context, DatabaseKeyProvider.getKey(context))
    }

    private val tts by lazy { EmergencyTtsHandler(context) }
    private val smsHandler by lazy { EmergencySmsHandler(context, tts) }
    private val cbtHandler by lazy { CbtHandler(context, tts) }

    // State flows
    private val _ayarlar = MutableStateFlow<KullaniciAyarlariEntity?>(null)
    val ayarlar: StateFlow<KullaniciAyarlariEntity?> = _ayarlar.asStateFlow()

    private val _ilaclar = MutableStateFlow<List<IlacTakipEntity>>(emptyList())
    val ilaclar: StateFlow<List<IlacTakipEntity>> = _ilaclar.asStateFlow()

    private val _saglikGecmisi = MutableStateFlow<List<SaglikGecmisiEntity>>(emptyList())
    val saglikGecmisi: StateFlow<List<SaglikGecmisiEntity>> = _saglikGecmisi.asStateFlow()

    init {
        yukleVeriler()
    }

    fun yukleVeriler() {
        viewModelScope.launch {
            // Ayarları tek seferlik akıştan veya sürekli güncellemeden al
            db.kullaniciAyarlariDao.getAyarlar().collect {
                _ayarlar.value = it
            }
        }
        viewModelScope.launch {
            db.ilacTakipDao.getAllIlaclar().collect {
                _ilaclar.value = it
            }
        }
        viewModelScope.launch {
            db.saglikGecmisiDao.getAllSaglikGecmisi().collect {
                _saglikGecmisi.value = it
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Acil Durum & BDT Sakinleşme Tetikleyicileri
    // ------------------------------------------------------------------ //

    fun tetikleAcilDurum() {
        viewModelScope.launch {
            Log.i("MainViewModel", "Arayüzden acil durum SOS tetiklendi.")
            // Paralel SMS gönderimi
            launch(Dispatchers.IO) {
                smsHandler.gonderAcilSms()
            }
            // TTS
            tts.acilDurumOku()
        }
    }

    fun tetikleCbtSakinlesme() {
        viewModelScope.launch {
            Log.i("MainViewModel", "Arayüzden CBT Sakinleşme Modu tetiklendi.")
            cbtHandler.sakinlestir("Arayüzden manuel sakinleşme tetiklendi")
        }
    }

    // ------------------------------------------------------------------ //
    // Ayarlar Güncelleme
    // ------------------------------------------------------------------ //

    fun guncelleAyarlar(
        wakeWord: String,
        emergencyText: String,
        emergencyContactNumber: String,
        geminiApiKey: String,
        volumeLevel: Int,
        speechRate: Float
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val mevcut = _ayarlar.value ?: KullaniciAyarlariEntity()
            val yeniAyarlar = mevcut.copy(
                wakeWord = wakeWord.trim(),
                emergencyText = emergencyText.trim(),
                emergencyContactNumber = emergencyContactNumber.trim(),
                geminiApiKey = geminiApiKey.trim(),
                volumeLevel = volumeLevel,
                speechRate = speechRate
            )
            db.kullaniciAyarlariDao.insertOrUpdate(yeniAyarlar)
            Log.i("MainViewModel", "Kullanıcı ayarları güncellendi.")
        }
    }

    // ------------------------------------------------------------------ //
    // İlaç İşlemleri
    // ------------------------------------------------------------------ //

    fun ilacEkle(ad: String, dozaj: String, saat: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val yeniIlac = IlacTakipEntity(
                ilacAdi = ad.trim(),
                dozaj = dozaj.trim(),
                hatirlatmaSaati = saat.trim(),
                aktifMi = true
            )
            db.ilacTakipDao.insertIlac(yeniIlac)
            Log.i("MainViewModel", "Yeni ilaç eklendi: $ad")
        }
    }

    fun ilacSil(ilac: IlacTakipEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.ilacTakipDao.deleteIlac(ilac)
            Log.i("MainViewModel", "İlaç silindi: ${ilac.ilacAdi}")
        }
    }

    fun ilacAktiflikGuncelle(ilac: IlacTakipEntity, aktif: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val guncel = ilac.copy(aktifMi = aktif)
            db.ilacTakipDao.insertIlac(guncel) // Insert acts as upsert
        }
    }
}

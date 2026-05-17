package com.example.asea.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.IOException

/**
 * Parkinson ve Anksiyete yönetimi için yerel (offline) müzik dosyalarını
 * döngüsel (looping) olarak çalan, thread-safe, tekil (Singleton) oynatıcı modülü.
 */
object AudioPlayerHandler {
    private const val TAG = "AudioPlayerHandler"
    private var mediaPlayer: MediaPlayer? = null

    /**
     * assets/audio/ altındaki bir müzik dosyasını oynatır.
     * @param context Uygulama bağlamı.
     * @param assetPath Oynatılacak ses dosyasının assets altındaki tam yolu (örn: "audio/ambient_432hz.mp3").
     */
    @Synchronized
    fun playAsset(context: Context, assetPath: String) {
        try {
            Log.i(TAG, "Müzik çalınıyor: $assetPath")
            
            // Eğer çalan başka bir müzik varsa temizle ve durdur
            stop()

            mediaPlayer = MediaPlayer().apply {
                val afd = context.assets.openFd(assetPath)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                isLooping = true // Terapi müzikleri için döngüsel çalma esastır
                start()
            }
            
            Log.d(TAG, "MediaPlayer başarıyla başlatıldı ve çalıyor: $assetPath")
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer başlatılırken hata oluştu: $assetPath", e)
        }
    }

    /**
     * Çalan müziği durdurur ve MediaPlayer kaynaklarını serbest bırakır.
     */
    @Synchronized
    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
                mediaPlayer = null
                Log.i(TAG, "MediaPlayer durduruldu ve serbest bırakıldı.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer durdurulurken hata oluştu", e)
        }
    }

    /**
     * Oynatıcının şu an çalıp çalmadığını döner.
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
}

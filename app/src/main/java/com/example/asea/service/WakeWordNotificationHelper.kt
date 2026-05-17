package com.example.asea.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.asea.R

/**
 * Foreground Service için kalıcı bildirim oluşturucu.
 * Android 8+ (Oreo) zorunlu kanal yönetimini de içerir.
 */
object WakeWordNotificationHelper {

    const val CHANNEL_ID   = "asea_wake_word_channel"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ASEA – Dinleme Servisi",
                NotificationManager.IMPORTANCE_LOW          // Ses çıkarmasın, sürekli görünür olsun
            ).apply {
                description = "\"Ela\" uyandırma kelimesi için arka planda dinleme yapılıyor."
                setShowBadge(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(context: Context, statusText: String = "Dinleniyor…"): Notification {
        // Bildirime tıklandığında MainActivity açılacak (ileride eklenecek)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, Class.forName("${context.packageName}.MainActivity")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("ASEA Asistan")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)               // Kullanıcı kaydıramazlar
            .setContentIntent(pendingIntent)
            .build()
    }
}

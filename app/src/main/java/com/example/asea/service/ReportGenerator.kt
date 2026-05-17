package com.example.asea.service

import android.content.Context
import android.util.Log
import com.example.asea.data.local.AseaDatabase
import com.example.asea.data.local.DatabaseKeyProvider
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kullanıcının günlük sağlık notlarını, Parkinson donma/kasılma geçmişini ve ilaç takibini
 * kronolojik ve tıbbi bir Markdown rapor dosyasına dönüştüren modül.
 */
object ReportGenerator {

    private const val TAG = "ReportGenerator"

    /**
     * Tüm sağlık verilerini çekerek cihaz dosyalarına Markdown formatında kaydeder.
     * @param context Uygulama bağlamı.
     * @return Başarıyla oluşturulan Rapor dosyası nesnesi veya hata durumunda null.
     */
    suspend fun generateReport(context: Context): File? {
        try {
            Log.i(TAG, "Doktor raporu oluşturuluyor…")
            val db = AseaDatabase.getInstance(context, DatabaseKeyProvider.getKey(context))
            
            // Tüm geçmişi ve ilaçları çek
            val records = db.saglikGecmisiDao.getAllSaglikGecmisi().first()
            val medicines = db.ilacTakipDao.getAllIlaclar().first()

            val dateFormat = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("tr", "TR"))
            val reportBuilder = StringBuilder()

            // 1. Rapor Başlığı
            reportBuilder.append("# ASEA - KİŞİSEL SAĞLIK VE DOKTOR RAPORU\n")
            reportBuilder.append("Oluşturulma Tarihi: ${dateFormat.format(Date())}\n\n")
            reportBuilder.append("Bu belge, hastanın günlük sağlık notlarını, Parkinson semptomlarını ve anksiyete kriz geçmişini içermektedir.\n\n")
            reportBuilder.append("---\n\n")

            // 2. İlaç Tedavisi Bilgileri
            reportBuilder.append("## 💊 AKTİF İLAÇ TEDAVİSİ\n")
            if (medicines.isEmpty()) {
                reportBuilder.append("- Kayıtlı ilaç tedavisi bulunmamaktadır.\n")
            } else {
                medicines.forEach { med ->
                    val durum = if (med.aktifMi) "Aktif" else "Pasif"
                    reportBuilder.append("- **${med.ilacAdi}** (${med.dozaj}) - Hatırlatma Saati: *${med.hatirlatmaSaati}* - Durum: *$durum*\n")
                }
            }
            reportBuilder.append("\n")

            // 3. Kişisel Notlar Bölümü
            reportBuilder.append("## 📝 HASTA GÜNLÜK SAĞLIK NOTLARI\n")
            val notes = records.filter { it.kayitTipi.lowercase() == "not" || it.kayitTipi.lowercase() == "sağlık notu" }
            if (notes.isEmpty()) {
                reportBuilder.append("- Kayıtlı hasta notu bulunmamaktadır.\n")
            } else {
                notes.forEach { note ->
                    val tarih = dateFormat.format(Date(note.kayitTarihi))
                    reportBuilder.append("- **[$tarih]**: ${note.icerik}\n")
                }
            }
            reportBuilder.append("\n")

            // 4. Kriz ve Ataklar Bölümü
            reportBuilder.append("## ⚠️ ANKSİYETE VE PARKİNSON MOTOR ATAKLARI\n")
            val crises = records.filter { it.kayitTipi.lowercase() in setOf("kriz", "anksiyete", "motor donma", "kasılma") }
            if (crises.isEmpty()) {
                reportBuilder.append("- Kayıtlı atak veya kriz kaydı bulunmamaktadır.\n")
            } else {
                crises.forEach { crisis ->
                    val tarih = dateFormat.format(Date(crisis.kayitTarihi))
                    reportBuilder.append("- **[$tarih]** *(${crisis.kayitTipi})*: ${crisis.icerik}\n")
                }
            }
            reportBuilder.append("\n")

            // 5. Kronolojik Tüm Olaylar Geçmişi
            reportBuilder.append("## 🕒 TÜM KRONOLOJİK GEÇMİŞ LİSTESİ\n")
            if (records.isEmpty()) {
                reportBuilder.append("- Kayıtlı tıbbi olay geçmişi bulunmamaktadır.\n")
            } else {
                records.forEach { record ->
                    val tarih = dateFormat.format(Date(record.kayitTarihi))
                    reportBuilder.append("- **[$tarih]** *[${record.kayitTipi}]*: ${record.icerik}\n")
                }
            }

            // Dosyaya yazma (External Files veya internal cache)
            val fileName = "doktor_raporu.md"
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, fileName)
            file.writeText(reportBuilder.toString())

            Log.i(TAG, "Doktor raporu başarıyla oluşturuldu: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Doktor raporu oluşturulurken hata", e)
            return null
        }
    }
}

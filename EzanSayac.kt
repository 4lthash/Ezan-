// EzanSayac.kt — Android Studio'da "Empty Activity" (Kotlin) projesi açın,
// paket adını kendinize göre değiştirip bu dosyayı ekleyin.
//
// AndroidManifest.xml'e ekleyin:
//   <uses-permission android:name="android.permission.INTERNET"/>
//   <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
//   <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
//   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
//   <application ...>
//     <activity android:name=".MainActivity" android:exported="true">
//       <intent-filter>
//         <action android:name="android.intent.action.MAIN"/>
//         <category android:name="android.intent.category.LAUNCHER"/>
//       </intent-filter>
//     </activity>
//     <service android:name=".EzanService" android:exported="false"
//              android:foregroundServiceType="specialUse">
//       <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
//                 android:value="prayer_countdown"/>
//     </service>
//   </application>

package com.example.ezansayac

import android.Manifest
import android.app.*
import android.content.Intent
import android.os.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

const val SEHIR = "Ankara"   // şehrinizi değiştirin
const val ULKE = "Turkey"

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        startForegroundService(Intent(this, EzanService::class.java))
        finish()
    }
}

class EzanService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tz = TimeZone.getTimeZone("Europe/Istanbul")
    private val isimler = linkedMapOf(
        "Fajr" to "İmsak", "Sunrise" to "Güneş", "Dhuhr" to "Öğle",
        "Asr" to "İkindi", "Maghrib" to "Akşam", "Isha" to "Yatsı"
    )

    override fun onBind(i: Intent?) = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val ch = NotificationChannel("ezan", "Ezan Sayacı", NotificationManager.IMPORTANCE_LOW)
        ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        startForeground(1, build("Yükleniyor...", null))
        thread { guncelle() }
        return START_STICKY
    }

    private fun build(baslik: String, hedef: Long?): Notification {
        val n = Notification.Builder(this, "ezan")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(baslik)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (hedef != null) {
            n.setWhen(hedef)
            n.setUsesChronometer(true)
            n.setChronometerCountDown(true) // saat:dakika:saniye geri sayım
        }
        return n.build()
    }

    private fun vakitler(tarih: Date): Map<String, Long> {
        val gun = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply { timeZone = tz }.format(tarih)
        val url = "https://api.aladhan.com/v1/timingsByCity/$gun?city=$SEHIR&country=$ULKE&method=13"
        val t = JSONObject(URL(url).readText()).getJSONObject("data").getJSONObject("timings")
        val tf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).apply { timeZone = tz }
        return isimler.keys.associateWith { k ->
            tf.parse("$gun ${t.getString(k).substring(0, 5)}")!!.time
        }
    }

    private fun guncelle() {
        try {
            val simdi = System.currentTimeMillis()
            var sonraki: Pair<String, Long>? = null
            for (gunEkle in 0..1) {
                val v = vakitler(Date(simdi + gunEkle * 86400000L))
                sonraki = v.entries.firstOrNull { it.value > simdi }?.let { it.key to it.value }
                if (sonraki != null) break
            }
            sonraki ?: return
            val (anahtar, zaman) = sonraki
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(1, build("${isimler[anahtar]} vaktine kalan süre", zaman))
            // Vakit gelince yeniden hesapla
            handler.postDelayed({ thread { guncelle() } }, zaman - simdi + 1000)
        } catch (e: Exception) {
            handler.postDelayed({ thread { guncelle() } }, 60000) // internet yoksa 1 dk sonra tekrar
        }
    }
}

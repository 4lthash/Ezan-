# Ezan Sayacı — GitHub'a eklenecek 6 dosya

Her başlık, GitHub'da yazacağınız **dosya yolu**. Altındaki kodu aynen kopyalayıp yapıştırın.
Sıra önemli: **en son** build.yml dosyasını ekleyin (derleme o zaman başlar).

## settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "EzanSayac"
```

## build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

android {
    namespace = "com.example.ezansayac"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.ezansayac"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
```

## gradle.properties

```text
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
```

## src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:label="Ezan Sayacı"
        android:icon="@android:drawable/ic_lock_idle_alarm"
        android:allowBackup="false">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".EzanService"
            android:exported="false" />

        <receiver
            android:name=".BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

## src/main/java/EzanSayac.kt

```kotlin
package com.example.ezansayac

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

// Şehrinizi buradan değiştirin (İngilizce yazılışıyla, örn: Istanbul, Izmir)
const val SEHIR = "Ankara"
const val ULKE = "Turkey"

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            baslat()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        baslat()
    }

    private fun baslat() {
        startForegroundService(Intent(this, EzanService::class.java))
        Toast.makeText(this, "Ezan sayacı başlatıldı", Toast.LENGTH_SHORT).show()
        finish()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startForegroundService(Intent(context, EzanService::class.java))
    }
}

class EzanService : Service() {

    private val tz = TimeZone.getTimeZone("Europe/Istanbul")
    private val isimler = linkedMapOf(
        "Fajr" to "İmsak",
        "Sunrise" to "Güneş",
        "Dhuhr" to "Öğle",
        "Asr" to "İkindi",
        "Maghrib" to "Akşam",
        "Isha" to "Yatsı"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        val kanal = NotificationChannel("ezan", "Ezan Sayacı", NotificationManager.IMPORTANCE_LOW)
        kanal.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(kanal)
        startForeground(1, bildirim("Ezan vakti yükleniyor...", null))
        thread { guncelle() }
        return START_STICKY
    }

    private fun bildirim(baslik: String, hedef: Long?): Notification {
        val b = Notification.Builder(this, "ezan")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(baslik)
            .setContentText("Kalan süre")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (hedef != null) {
            b.setWhen(hedef)
            b.setUsesChronometer(true)
            b.setChronometerCountDown(true) // saat:dakika:saniye geri sayım
        }
        return b.build()
    }

    // Verilen günün vakitlerini (milisaniye olarak) getirir
    private fun vakitler(tarih: Date): Map<String, Long> {
        val gunFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        gunFormat.timeZone = tz
        val gun = gunFormat.format(tarih)

        val adres = "https://api.aladhan.com/v1/timingsByCity/$gun" +
            "?city=${URLEncoder.encode(SEHIR, "UTF-8")}" +
            "&country=${URLEncoder.encode(ULKE, "UTF-8")}&method=13" // 13 = Diyanet

        val baglanti = URL(adres).openConnection() as HttpURLConnection
        baglanti.connectTimeout = 10000
        baglanti.readTimeout = 10000
        val metin = baglanti.inputStream.bufferedReader().use { it.readText() }
        val t = JSONObject(metin).getJSONObject("data").getJSONObject("timings")

        val tf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
        tf.timeZone = tz
        val sonuc = LinkedHashMap<String, Long>()
        for (anahtar in isimler.keys) {
            val saat = t.getString(anahtar).substring(0, 5)
            sonuc[anahtar] = tf.parse("$gun $saat")!!.time
        }
        return sonuc
    }

    private fun guncelle() {
        try {
            val simdi = System.currentTimeMillis()
            var bulunan: Map.Entry<String, Long>? = null
            for (gunEkle in 0..1) {
                val v = vakitler(Date(simdi + gunEkle * 86400000L))
                bulunan = v.entries.firstOrNull { it.value > simdi }
                if (bulunan != null) break
            }
            val sonraki = bulunan ?: throw IllegalStateException("Vakit bulunamadı")

            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(1, bildirim("${isimler[sonraki.key]} vaktine kalan süre", sonraki.value))
            alarmKur(sonraki.value + 2000) // vakit girince bir sonrakine geç
        } catch (e: Exception) {
            alarmKur(System.currentTimeMillis() + 60000) // hata olursa 1 dk sonra tekrar dene
        }
    }

    private fun alarmKur(zaman: Long) {
        val am = getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getService(
            this, 0, Intent(this, EzanService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, zaman, pi)
    }
}
```

## .github/workflows/build.yml

```yaml
name: Build APK

on:
  push:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 8.7

      - name: Build
        run: gradle assembleDebug --no-daemon

      - uses: actions/upload-artifact@v4
        with:
          name: EzanSayac-apk
          path: build/outputs/apk/debug/*.apk
```

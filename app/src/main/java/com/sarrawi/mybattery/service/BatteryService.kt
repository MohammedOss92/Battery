package com.sarrawi.mybattery.service

import android.app.*
import android.content.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sarrawi.mybattery.R
import com.sarrawi.mybattery.bcm.BatteryReceiver
import com.sarrawi.mybattery.worker.BatteryWorker
import java.util.concurrent.TimeUnit

class BatteryService : Service(), BatteryReceiver.BatteryListener {

    private lateinit var batteryReceiver: BatteryReceiver

    override fun onCreate() {
        super.onCreate()

        // تشغيل الخدمة في وضع Foreground مع إشعار ثابت
        startForeground(1, createNotification())

        // تسجيل الـ BroadcastReceiver لمراقبة حالة البطارية
        batteryReceiver = BatteryReceiver(this)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    // إنشاء إشعار Foreground
    private fun createNotification(): Notification {
        val channelId = "battery_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Battery Monitor",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("مراقبة البطارية تعمل")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }




    override fun onBatteryChanged(level: Int, isCharging: Boolean) {
        val prefs = getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)

        val isServiceEnabled = prefs.getBoolean("service_enabled", false)
        if (!isServiceEnabled) return

        val chargeLimit = prefs.getInt("chargeLimit", 80)
        val lowLimit = prefs.getInt("lowBatteryLimit", 20)
        val rootEnabled = prefs.getBoolean("useRootControl", false)

        if (isCharging && level >= chargeLimit) {
            sendNotificationWithAction("افصل الشاحن، وصلت النسبة المحددة $level%")
            if (rootEnabled) executeAdbCommand()
            playCustomSound(isMax = true)
        } else if (!isCharging && level <= lowLimit) {
            // إشعار واضح مع دعوة لتفعيل توفير الطاقة
            sendNotificationWithAction(
                "⚠️ البطارية منخفضة ($level%)\n" +
                        "يرجى تفعيل وضع توفير الطاقة للحفاظ على عمر البطارية."
            )
            playCustomSound(isMax = false)
            // فتح شاشة إعدادات توفير الطاقة تلقائيًا
            val intent = Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }


    // أمر ADB يفصل الشاحن (تجريبي – يتطلب صلاحيات Root)
    private fun executeAdbCommand() {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "echo 0 > /sys/class/power_supply/battery/charging_enabled"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun sendNotification(message: String) {
        val channelId = "battery_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تنبيهات البطارية",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("تنبيه البطارية")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // الإشعار يختفي عند الضغط عليه
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, notification)
    }

    private fun sendNotificationWithAction(message: String) {
        val channelId = "battery_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تنبيهات البطارية",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("تنبيه البطارية")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)  // عند الضغط يفتح إعدادات توفير الطاقة
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, notification)
    }






    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // التطبيق غير مستثنى من تحسين البطارية، افتح إعدادات البطارية
                val batteryIntent = Intent()
                batteryIntent.action = android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                batteryIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(batteryIntent)
            }
            startForegroundServiceWithNotification()
            requestIgnoreBatteryOptimizations()
        }

        return START_STICKY
    }



//    private fun startForegroundServiceWithNotification2() {
//        val channelId = "battery_service"
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                channelId,
//                "Battery Service",
//                NotificationManager.IMPORTANCE_LOW
//            )
//            val manager = getSystemService(NotificationManager::class.java)
//            manager.createNotificationChannel(channel)
//        }
//
//        val notification = NotificationCompat.Builder(this, channelId)
//            .setContentTitle("مراقبة البطارية")
//            .setContentText("الخدمة تعمل في الخلفية")
//            .setSmallIcon(R.drawable.ic_launcher_foreground) // استبدل بالأيقونة الخاصة بك
//            .build()
//
//        startForeground(1, notification)
//    }
    private fun startForegroundServiceWithNotification() {
        val channelId = "battery_service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Battery Background Monitor",
                NotificationManager.IMPORTANCE_MIN
                // لا يظهر في الشريط العلوي
            ).apply {
                setShowBadge(false) // لا يظهر نقطة على الأيقونة
                lockscreenVisibility = Notification.VISIBILITY_SECRET // لا يظهر على شاشة القفل
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("مراقبة البطارية")
            .setContentText("الخدمة تعمل في الخلفية")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MIN) // حتى على إصدارات أقدم
            .setOngoing(true) // لا يمكن سحبها
            .build()

        startForeground(1, notification)
    }


    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        }
    }

    private fun scheduleBatteryCheckWithWorker() {
        val workRequest = PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "BatteryCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun playCustomSound(isMax: Boolean) {
        val prefs = getSharedPreferences("battery_settings", Context.MODE_PRIVATE)
        val uriString = if (isMax) {
            prefs.getString("high_sound_uri", null)
        } else {
            prefs.getString("low_sound_uri", null)
        }

        if (!uriString.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(uriString)
                val mediaPlayer = MediaPlayer()

                val resolver = applicationContext.contentResolver
                val afd = resolver.openAssetFileDescriptor(uri, "r")
                if (afd != null) {
                    mediaPlayer.setDataSource(afd.fileDescriptor)
                    afd.close()
                    mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_NOTIFICATION)
                    mediaPlayer.prepare()
                    mediaPlayer.start()
                    mediaPlayer.setOnCompletionListener {
                        it.release()
                    }
                } else {
                    Toast.makeText(this, "تعذر فتح الملف", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this, "فشل تشغيل الصوت", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }



}

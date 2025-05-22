package com.sarrawi.mybattery


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sarrawi.mybattery.service.BatteryService
import com.sarrawi.mybattery.worker.BatteryWorker
import java.util.concurrent.TimeUnit

class BootReceiver2 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // إعادة تشغيل الخدمة
            val serviceIntent = Intent(context, BatteryService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)

            // إعادة جدولة WorkManager
            val workRequest = PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "BatteryCheck",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}

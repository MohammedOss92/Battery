package com.sarrawi.mybattery.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sarrawi.mybattery.service.BatteryService

class BatteryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val prefs = context.getSharedPreferences("battery_settings", Context.MODE_PRIVATE)
        val minLevel = prefs.getInt("min_level", 30)
        val maxLevel = prefs.getInt("max_level", 80)

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return Result.failure()

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging && level in minLevel..maxLevel) {
            val serviceIntent = Intent(context, BatteryService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        return Result.success()
    }
}

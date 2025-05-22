package com.sarrawi.mybattery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.sarrawi.mybattery.service.BatteryService



//الكود الذي أرسلته هو BootReceiver، وهو مسؤول عن تشغيل خدمة BatteryService تلقائيًا عند إقلاع الجهاز.
//
//لأنك طلبت "حذف root"، فأحب أوضح:
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                val prefs = context.getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
                val isServiceEnabled = prefs.getBoolean("service_enabled", false)

                if (isServiceEnabled) {
                    val serviceIntent = Intent(context, BatteryService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }


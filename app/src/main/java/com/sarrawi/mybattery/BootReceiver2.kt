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
    //هذه الدالة يتم استدعاؤها تلقائيًا عندما يصل إشعار (Broadcast) من النظام بأن الجهاز أُعيد تشغيله.
    override fun onReceive(context: Context, intent: Intent?) {
        //يتأكد أن الحدث هو فعلاً "تم إقلاع الجهاز".
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // إعادة تشغيل الخدمة
            //هذا يقوم بتشغيل الخدمة التي تتابع حالة البطارية بعد إقلاع الجهاز مباشرةً.
            //
            //startForegroundService تُستخدم لتشغيل خدمة تعمل في المقدمة (Foreground)، وهي ضرورية بدءًا من Android 8 (Oreo) فما فوق.
            val serviceIntent = Intent(context, BatteryService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)

            //ينشئ مهمة دورية باستخدام WorkManager لتعمل كل 15 دقيقة وتقوم بعمل معين (مثل فحص حالة البطارية أو إرسال إشعار).
            // إعادة جدولة WorkManager
            val workRequest = PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES)
                .build()

            //هذا السطر يُخبر WorkManager بأن يُشغّل المهمة باسم "BatteryCheck"، وإذا كانت موجودة من قبل، فـ"استبدلها
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "BatteryCheck",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}

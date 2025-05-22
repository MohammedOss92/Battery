package com.sarrawi.mybattery.bcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.BatteryManager
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext

class BatteryReceiver(
    private val listener: BatteryListener? = null,
    private val lowBatteryLimit: Int = 20,
    private val chargeLimit: Int = 80,
    private var notifyLow: Boolean = false,
    private var notifyHigh: Boolean = false
) : BroadcastReceiver() {

    interface BatteryListener {
        fun onBatteryChanged(level: Int, isCharging: Boolean)
    }
    private var mediaPlayer: MediaPlayer? = null

    override fun onReceive(context: Context, intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

        val isCharging = status == BatteryManager.BATTERY_PLUGGED_AC ||
                status == BatteryManager.BATTERY_PLUGGED_USB ||
                status == BatteryManager.BATTERY_PLUGGED_WIRELESS

        listener?.onBatteryChanged(level, isCharging)

        // استدعاء الصوت من SharedPreferences
        val prefs = context.getSharedPreferences("battery_settings", Context.MODE_PRIVATE)
        val lowSoundUriString = prefs.getString("low_sound_uri", null)
        val highSoundUriString = prefs.getString("high_sound_uri", null)

        if (!isCharging && level in 1..lowBatteryLimit) {
            if (!notifyLow) {
                Toast.makeText(context, "🔋 البطارية منخفضة: $level%", Toast.LENGTH_LONG).show()
                notifyLow = true
                notifyHigh = false

                // تشغيل صوت البطارية المنخفضة
                lowSoundUriString?.let {
                    val lowUri = Uri.parse(it)
                    playSound(lowUri, context)
                }
            }
        } else if (isCharging && level >= chargeLimit) {
            if (!notifyHigh) {
                Toast.makeText(context, "⚡ البطارية مشحونة: $level%", Toast.LENGTH_LONG).show()
                notifyHigh = true
                notifyLow = false

                // تشغيل صوت البطارية العالية
                highSoundUriString?.let {
                    val highUri = Uri.parse(it)
                    playSound(highUri, context)
                }
            }
        } else {
            notifyLow = false
            notifyHigh = false
        }
    }


//    private fun playSound(uri: Uri, context: Context) {
//        mediaPlayer?.release()
//        mediaPlayer = MediaPlayer.create(context, uri)
//        mediaPlayer?.start()
//    }

    private fun playSound(uri: Uri, context: Context) {
        // تحرير الموارد السابقة إذا كانت موجودة
        mediaPlayer?.release()

        // إنشاء MediaPlayer جديد مع المصدر uri
        mediaPlayer = MediaPlayer.create(context, uri)

        mediaPlayer?.apply {
            setOnCompletionListener {
                // تحرير الموارد عند انتهاء الصوت
                it.release()
                mediaPlayer = null
            }
            start()
        } ?: run {
            // في حال لم يتم إنشاء MediaPlayer بنجاح
            Toast.makeText(context, "تعذر تشغيل الصوت", Toast.LENGTH_SHORT).show()
        }
    }
}

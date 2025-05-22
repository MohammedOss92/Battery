package com.sarrawi.mybattery.vm

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BatteryViewModel(private val context: Context) : ViewModel() {
    private val prefs: SharedPreferences = context.getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)

    val chargeLimit = MutableLiveData(prefs.getInt("chargeLimit", 80))
    val lowBatteryLimit = MutableLiveData(prefs.getInt("lowBatteryLimit", 20))

    private val _notifyHigh = MutableLiveData(prefs.getBoolean("notifyHigh", false))
    val notifyHigh: LiveData<Boolean> get() = _notifyHigh

    private val _notifyLow = MutableLiveData(prefs.getBoolean("notifyLow", false))
    val notifyLow: LiveData<Boolean> get() = _notifyLow

    private fun <T> savePreference(key: String, value: T) {
        with(prefs.edit()) {
            when (value) {
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
                else -> error("Unsupported preference type")
            }
            apply()
        }
    }

    fun setChargeLimit(value: Int) {
        chargeLimit.value = value
        savePreference("chargeLimit", value)
    }

    fun setLowBatteryLimit(value: Int) {
        lowBatteryLimit.value = value
        savePreference("lowBatteryLimit", value)
    }



    fun setNotifyHigh(value: Boolean) {
        _notifyHigh.value = value
        savePreference("notifyHigh", value)
    }

    fun setNotifyLow(value: Boolean) {
        _notifyLow.value = value
        savePreference("notifyLow", value)
    }
}


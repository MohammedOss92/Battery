package com.sarrawi.mybattery

import android.content.Context

object PrefsHelper {
    fun setBoolean(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
            .edit().putBoolean(key, value).apply()
    }

    fun getBoolean(context: Context, key: String, default: Boolean = false): Boolean {
        return context.getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
            .getBoolean(key, default)
    }

    fun setInt(context: Context, key: String, value: Int) {
        context.getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
            .edit().putInt(key, value).apply()
    }

    fun getInt(context: Context, key: String, default: Int): Int {
        return context.getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
            .getInt(key, default)
    }
}

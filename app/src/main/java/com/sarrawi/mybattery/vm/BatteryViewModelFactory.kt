package com.sarrawi.mybattery.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class BatteryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BatteryViewModel::class.java)) {
            return BatteryViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

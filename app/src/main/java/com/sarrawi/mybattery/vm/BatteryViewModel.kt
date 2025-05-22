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

    // ينشئ كائن SharedPreferences لتخزين الإعدادات الخاصة بالتطبيق باسم BatteryPrefs.
    //MODE_PRIVATE تعني أن هذه البيانات مرئية فقط للتطبيق الحالي.
    private val prefs: SharedPreferences = context.getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)

    //يمثل الحد الأقصى لشحن البطارية قبل إشعار المستخدم. يُحمّل القيمة من SharedPreferences وإذا لم توجد، يستخدم 80 كقيمة افتراضية.
    val chargeLimit = MutableLiveData(prefs.getInt("chargeLimit", 80))

    //يمثل الحد الأدنى لنسبة البطارية قبل تحذير المستخدم. القيمة الافتراضية هي 20.
    val lowBatteryLimit = MutableLiveData(prefs.getInt("lowBatteryLimit", 20))

    //يمثل ما إذا كان يجب إشعار المستخدم عند وصول الشحن للحد الأقصى.
    //السطر الثاني يُعرض LiveData فقط (قراءة فقط من الخارج).
    private val _notifyHigh = MutableLiveData(prefs.getBoolean("notifyHigh", false))
    val notifyHigh: LiveData<Boolean> get() = _notifyHigh

    //مثل ما إذا كان يجب إشعار المستخدم عند انخفاض البطارية. أيضًا، يتم عرضه كـ LiveData.
    private val _notifyLow = MutableLiveData(prefs.getBoolean("notifyLow", false))
    val notifyLow: LiveData<Boolean> get() = _notifyLow


    //دالة عامة لتخزين القيم في SharedPreferences. تستقبل اسم المفتاح والقيمة.
    private fun <T> savePreference(key: String, value: T) {
        // يبدأ تعديل SharedPreferences.
        with(prefs.edit()) {
            // يتحقق من نوع القيمة ويُخزنها بطريقة مناسبة. يُظهر خطأ إن لم يكن النوع مدعومًا.
            when (value) {
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
                else -> error("Unsupported preference type")
            }
            apply()
        }
    }

    // دالة لتحديث LiveData للحد الأقصى للشحن وتخزينه.
    fun setChargeLimit(value: Int) {
        chargeLimit.value = value
        savePreference("chargeLimit", value)
    }

    //⚠️ دالة لتحديث حد البطارية المنخفضة وتخزينه.
    fun setLowBatteryLimit(value: Int) {
        lowBatteryLimit.value = value
        savePreference("lowBatteryLimit", value)
    }


    //📳 تحديث خيار إشعار الحد الأقصى وتخزينه.
    fun setNotifyHigh(value: Boolean) {
        _notifyHigh.value = value
        savePreference("notifyHigh", value)
    }


    //🔋 تحديث خيار إشعار انخفاض البطارية وتخزينه.
    fun setNotifyLow(value: Boolean) {
        _notifyLow.value = value
        savePreference("notifyLow", value)
    }
}


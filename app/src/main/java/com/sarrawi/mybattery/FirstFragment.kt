
package com.sarrawi.mybattery

import android.content.*
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.work.*
import com.sarrawi.mybattery.databinding.FragmentFirstBinding
import com.sarrawi.mybattery.service.BatteryService
import com.sarrawi.mybattery.vm.BatteryViewModel
import com.sarrawi.mybattery.vm.BatteryViewModelFactory
import com.sarrawi.mybattery.worker.BatteryWorker
import java.util.concurrent.TimeUnit

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: BatteryViewModel

    //تعريف BroadcastReceiver داخلي (anonymous class) يستقبل تحديثات حالة البطارية من النظام.
    //
    //في onReceive:
    //
    //يأخذ مستوى البطارية الحالي.
    //
    //يأخذ حالة الشحن (هل يشحن أو ممتلئ).
    //
    //يستدعي دالة updateBatteryIcon لتحديث واجهة المستخدم.
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            updateBatteryIcon(level, isCharging)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        //تحميل ملفي SharedPreferences لحفظ واسترجاع الإعدادات الخاصة بالبطارية.
        //
        //kotlin
        //Copy
        //Edit
        val sharedPrefs = context.getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
        val settingsPrefs = context.getSharedPreferences("battery_settings", Context.MODE_PRIVATE)

        //تسجيل الـ batteryReceiver ليستقبل تحديثات البطارية تلقائيًا.
        //
        //kotlin
        //Copy
        //Edit
        requireContext().registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))


        //قراءة القيم المحفوظة من SharedPreferences:
        //
        //min_level و max_level هما حدود التنبيه لبطارية منخفضة وعالية.
        //
        //notifyLow و notifyHigh هل يجب إرسال إشعارات لهذه الحالات.
        val savedMin = settingsPrefs.getInt("min_level", 20)
        val savedMax = settingsPrefs.getInt("max_level", 80)
        val notifyLow = sharedPrefs.getBoolean("notifyLow", false)
        val notifyHigh = sharedPrefs.getBoolean("notifyHigh", false)

        viewModel = ViewModelProvider(
            this,
            BatteryViewModelFactory(context.applicationContext)
        )[BatteryViewModel::class.java]

        //تحديث الـ ViewModel بالقيم المحفوظة.
        viewModel.setLowBatteryLimit(savedMin)
        viewModel.setChargeLimit(savedMax)
        viewModel.setNotifyLow(notifyLow)
        viewModel.setNotifyHigh(notifyHigh)



        binding.switchNotifyLow.isChecked = notifyLow
        binding.switchNotifyHigh.isChecked = notifyHigh

        //تحديث القيم في SharedPreferences و ViewModel عند تغير حالة مفاتيح الإشعارات.
        binding.switchNotifyLow.setOnCheckedChangeListener { _, checked ->
            sharedPrefs.edit().putBoolean("notifyLow", checked).apply()
            viewModel.setNotifyLow(checked)
        }
        binding.switchNotifyHigh.setOnCheckedChangeListener { _, checked ->
            sharedPrefs.edit().putBoolean("notifyHigh", checked).apply()
            viewModel.setNotifyHigh(checked)
        }


        //ضبط شريط التمرير (SeekBar) الخاص بأعلى مستوى شحن.
        //
        //تحديث النص الذي يظهر بجانبه.
        //
        //حفظ القيمة عند توقف المستخدم عن التغيير.
        binding.seekBarChargeLimit.progress = savedMax
        binding.txtChargeLimit.text = "$savedMax%"
        binding.seekBarChargeLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.txtChargeLimit.text = "$progress%"
                viewModel.setChargeLimit(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                settingsPrefs.edit().putInt("max_level", binding.seekBarChargeLimit.progress).apply()
            }
        })

        binding.seekBarLowBatteryLimit.progress = savedMin
        binding.txtLowBatteryLimit.text = "$savedMin%"
        binding.seekBarLowBatteryLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.txtLowBatteryLimit.text = "$progress%"
                viewModel.setLowBatteryLimit(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                settingsPrefs.edit().putInt("min_level", binding.seekBarLowBatteryLimit.progress).apply()
            }
        })



        //قراءة حالة تشغيل الخدمة من SharedPreferences.
        //
        //تحديث نص الزر حسب الحالة.
        var isServiceRunning = sharedPrefs.getBoolean("service_enabled", false)
        updateButtonText(isServiceRunning)

        //عند الضغط على الزر:
        //
        //عكس حالة الخدمة (تشغيل/إيقاف).
        //
        //حفظ الحالة.
        //
        //تشغيل الخدمة (ForegroundService إذا أندرويد 8 أو أعلى).
        //
        //إظهار رسالة Toast.
        //
        //تحديث نص الزر.
        binding.btnStartService.setOnClickListener {
            isServiceRunning = !isServiceRunning
            sharedPrefs.edit().putBoolean("service_enabled", isServiceRunning).apply()

            val intent = Intent(context, BatteryService::class.java)
            if (isServiceRunning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Toast.makeText(context, "تم بدء الخدمة", Toast.LENGTH_SHORT).show()
            } else {
                context.stopService(intent)
                Toast.makeText(context, "تم إيقاف الخدمة", Toast.LENGTH_SHORT).show()
            }
            updateButtonText(isServiceRunning)
        }


        //هذه المراقبات تجعل الواجهة تتغير تلقائياً إذا تم تحديث القيم في ViewModel
        viewModel.chargeLimit.observe(viewLifecycleOwner) {
            binding.seekBarChargeLimit.progress = it
            binding.txtChargeLimit.text = "$it%"
        }

        viewModel.lowBatteryLimit.observe(viewLifecycleOwner) {
            binding.seekBarLowBatteryLimit.progress = it
            binding.txtLowBatteryLimit.text = "$it%"
        }



        // WorkManager job
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "BatteryWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        binding.button.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_settingsFragment)
        }


    }

    private fun updateButtonText(isRunning: Boolean) {
        binding.btnStartService.text = if (isRunning) "إيقاف الخدمة" else "بدء الخدمة"
    }



    //تحدد صورة البطارية المناسبة حسب نسبة الشحن وحالة الشحن.
    //
    //تُحدث النص بجانب الأيقونة لعرض مستوى البطارية.
    private fun updateBatteryIcon(level: Int, isCharging: Boolean) {
        val iconRes = when {
            isCharging -> R.drawable.battery_full
            level >= 90 -> R.drawable.battery_full
            level >= 60 -> R.drawable.battery_90
            level >= 50 -> R.drawable.battery_80
            level >= 20 -> R.drawable.battery_50
            else -> R.drawable.battery_20
        }
        binding.batteryView.batteryLevel = level
//        binding.imgBattery.setImageResource(iconRes)
        binding.txtBatteryStatus.text = "نسبة البطارية: $level% - ${if (isCharging) "يتم الشحن الآن" else "غير متصل بالشاحن"}"

//        binding.txtBatteryStatus.text = "$level%"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireContext().unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {}
        _binding = null
    }

}



//package com.sarrawi.mybattery
//
//import android.content.BroadcastReceiver
//import android.content.Context
//import android.content.Intent
//import android.content.IntentFilter
//import android.os.BatteryManager
//import android.os.Build
//import android.os.Bundle
//import android.os.PowerManager
//import android.util.Log
//import android.view.*
//import androidx.fragment.app.Fragment
//import android.widget.SeekBar
//import android.widget.Toast
//import androidx.core.content.ContextCompat.registerReceiver
//import androidx.core.content.ContextCompat.startForegroundService
//import androidx.core.view.MenuHost
//import androidx.core.view.MenuProvider
//import androidx.lifecycle.Lifecycle
//import androidx.lifecycle.ViewModelProvider
//import androidx.navigation.fragment.findNavController
//import androidx.work.ExistingPeriodicWorkPolicy
//import androidx.work.PeriodicWorkRequestBuilder
//import androidx.work.WorkManager
//import com.sarrawi.mybattery.bcm.BatteryReceiver
//import com.sarrawi.mybattery.databinding.FragmentFirstBinding
//import com.sarrawi.mybattery.service.BatteryService
//import com.sarrawi.mybattery.vm.BatteryViewModel
//import com.sarrawi.mybattery.vm.BatteryViewModelFactory
//import com.sarrawi.mybattery.worker.BatteryWorker
//import java.util.concurrent.TimeUnit
//import androidx.work.Constraints
//
//
//class FirstFragment : Fragment(), BatteryReceiver.BatteryListener {
//
//    private var _binding: FragmentFirstBinding? = null
//
//
//    private val binding get() = _binding!!
//
//    private lateinit var batteryReceiver: BatteryReceiver
//    private lateinit var viewModel: BatteryViewModel
//    private lateinit var batteryReceiver2: BroadcastReceiver
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//
//        _binding = FragmentFirstBinding.inflate(inflater, container, false)
//        return binding.root
//
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setHasOptionsMenu(true)  // مهم جداً عشان تظهر القائمة
//
//    }
//
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//
//        val sharedPrefs = requireContext().getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
//        val batterySettingsPrefs = requireContext().getSharedPreferences("battery_settings", Context.MODE_PRIVATE)
//        batteryReceiver = BatteryReceiver() // ✅ هذا يهيئ المتغير الذي في الكلاس
//        batteryReceiver2 = object : BroadcastReceiver() {
//            override fun onReceive(context: Context, intent: Intent) {
//                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
//                updateBatteryInfo(level)
//            }
//        }
//
//        requireContext().registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
//
//        // استرجاع القيم المحفوظة من SharedPreferences
//        val savedMinLevel = batterySettingsPrefs.getInt("min_level", 20)
//        val savedMaxLevel = batterySettingsPrefs.getInt("max_level", 80)
//        val notifyLowPref = sharedPrefs.getBoolean("notifyLow", false)
//        val notifyHighPref = sharedPrefs.getBoolean("notifyHigh", false)
//
//        // ViewModel
//        viewModel = ViewModelProvider(
//            this,
//            BatteryViewModelFactory(requireContext().applicationContext)
//        )[BatteryViewModel::class.java]
//
//        viewModel.setLowBatteryLimit(savedMinLevel)
//        viewModel.setChargeLimit(savedMaxLevel)
//        viewModel.setNotifyLow(notifyLowPref)
//        viewModel.setNotifyHigh(notifyHighPref)
//
//        // BatteryReceiver باستخدام القيم المحفوظة
//        batteryReceiver = BatteryReceiver(
//            listener = object : BatteryReceiver.BatteryListener {
//                override fun onBatteryChanged(level: Int, isCharging: Boolean) {
//                    binding.txtBatteryStatus.text =
//                        "المستوى: $level% - ${if (isCharging) "يتم الشحن" else "غير موصول"}"
//                }
//            },
//            lowBatteryLimit = savedMinLevel,
//            chargeLimit = savedMaxLevel,
//            notifyLow = notifyLowPref,
//            notifyHigh = notifyHighPref
//        )
//
//        requireContext().registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
//
//        // ضبط حالة الـ Switch من القيم المحفوظة
//        binding.switchNotifyLow.isChecked = notifyLowPref
//        binding.switchNotifyHigh.isChecked = notifyHighPref
//
//        // حفظ الحالة عند تغيير الـ Switch
//        binding.switchNotifyLow.setOnCheckedChangeListener { _, isChecked ->
//            sharedPrefs.edit().putBoolean("notifyLow", isChecked).apply()
//            viewModel.setNotifyLow(isChecked)
//        }
//        binding.switchNotifyHigh.setOnCheckedChangeListener { _, isChecked ->
//            sharedPrefs.edit().putBoolean("notifyHigh", isChecked).apply()
//            viewModel.setNotifyHigh(isChecked)
//        }
//
//        // SeekBars إعداد
//        binding.seekBarChargeLimit.progress = savedMaxLevel
//        binding.seekBarChargeLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
//            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//                viewModel.setChargeLimit(progress)
//                binding.txtChargeLimit.text = "$progress%"
//            }
//
//            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
//            override fun onStopTrackingTouch(seekBar: SeekBar?) {
//                batterySettingsPrefs.edit().putInt("max_level", binding.seekBarChargeLimit.progress).apply()
//            }
//        })
//
//        binding.seekBarLowBatteryLimit.progress = savedMinLevel
//        binding.txtLowBatteryLimit.text = "$savedMinLevel%"
//        binding.seekBarLowBatteryLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
//            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//                viewModel.setLowBatteryLimit(progress)
//                binding.txtLowBatteryLimit.text = "$progress%"
//            }
//
//            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
//            override fun onStopTrackingTouch(seekBar: SeekBar?) {
//                batterySettingsPrefs.edit().putInt("min_level", binding.seekBarLowBatteryLimit.progress).apply()
//            }
//        })
//
//        // زر تشغيل الخدمة
//        var isServiceRunning = sharedPrefs.getBoolean("service_enabled", false)
//        updateButtonText(isServiceRunning)
//
//        binding.btnStartService.setOnClickListener {
//            isServiceRunning = !isServiceRunning
//            sharedPrefs.edit().putBoolean("service_enabled", isServiceRunning).apply()
//
//            val serviceIntent = Intent(requireContext(), BatteryService::class.java)
//
//            if (isServiceRunning) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    requireContext().startForegroundService(serviceIntent)
//                } else {
//                    requireContext().startService(serviceIntent)
//                }
//                Toast.makeText(requireContext(), "تم بدء الخدمة", Toast.LENGTH_SHORT).show()
//            } else {
//                requireContext().stopService(serviceIntent)
//                Toast.makeText(requireContext(), "تم إيقاف الخدمة", Toast.LENGTH_SHORT).show()
//            }
//
//            updateButtonText(isServiceRunning)
//        }
//
//        // مراقبة التغييرات في LiveData
//        viewModel.chargeLimit.observe(viewLifecycleOwner) { value ->
//            binding.seekBarChargeLimit.progress = value
//            binding.txtChargeLimit.text = "$value%"
//        }
//
//        viewModel.lowBatteryLimit.observe(viewLifecycleOwner) { value ->
//            binding.seekBarLowBatteryLimit.progress = value
//            binding.txtLowBatteryLimit.text = "$value%"
//        }
//
//        binding.switchRootControl.isChecked = viewModel.useRootControl.value ?: false
//        binding.switchRootControl.setOnCheckedChangeListener { _, isChecked ->
//            viewModel.setUseRootControl(isChecked)
//        }
//
//        // ⬇️ جدولة العمل الدوري عبر WorkManager
//        val constraints = Constraints.Builder()
//            .setRequiresBatteryNotLow(false) // يمكن تغييره حسب الحاجة
//            .build()
//
//        val workRequest = PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES)
//            .setConstraints(constraints)
//            .build()
//
//        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
//            "BatteryWork",
//            ExistingPeriodicWorkPolicy.KEEP,
//            workRequest
//        )
//        binding.button.setOnClickListener {
//            Log.d("MenuTest", "Settings clicked")
//            Toast.makeText(requireContext(), "Settings clicked", Toast.LENGTH_SHORT).show()
//            findNavController().navigate(R.id.action_FirstFragment_to_settingsFragment)
//        }
//        menu_item()
//
//
//    }
//
//
//
//    private fun updateButtonText(isRunning: Boolean) {
//        binding.btnStartService.text = if (isRunning) "إيقاف الخدمة" else "بدء الخدمة"
//    }
//
//    private fun menu_item() {
//        // The usage of an interface lets you inject your own implementation
//        val menuHost: MenuHost = requireActivity()
//        menuHost.addMenuProvider(object : MenuProvider {
//            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
//                // Add menu items here
//                menuInflater.inflate(R.menu.menu_main, menu)
//            }
//
//            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
//
//                when(menuItem.itemId){
//                    R.id.action_settings -> {
//                        // هنا تضع الكود اللي تبي تنفذه لما المستخدم يضغط على هذا العنصر
//                        Log.d("MenuTest", "Settings clicked")
//                        Toast.makeText(requireContext(), "Settings clicked", Toast.LENGTH_SHORT).show()
//                        findNavController().navigate(R.id.action_FirstFragment_to_settingsFragment)
//                        true
//                    }
//
//                }
//                return true
//            }
//
//        },viewLifecycleOwner, Lifecycle.State.RESUMED)
//    }
//
//
//
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        try {
//            if (::batteryReceiver.isInitialized) {
//                requireContext().unregisterReceiver(batteryReceiver)
//            }
//        } catch (e: IllegalArgumentException) {
//            // ربما تم إلغاء التسجيل مسبقًا
//        }
//        _binding = null
//    }
//
//    private fun updateBatteryInfo(level: Int) {
//        binding.txtBatteryStatus.text = "$level%"
//
//        val batteryIcon = when {
//            level >= 100 -> R.drawable.battery_full
//            level >= 60 -> R.drawable.battery_90
//            level >= 50 -> R.drawable.battery_80
//            level >= 20 -> R.drawable.battery_50
//            else -> R.drawable.battery_20
//        }
//
//
//        binding.imgBattery.setImageResource(batteryIcon)
//    }
//
//    override fun onBatteryChanged(level: Int, isCharging: Boolean) {
//        activity?.runOnUiThread {
//            updateBatteryIcon(level, isCharging)
//        }
//    }
//
//    private fun updateBatteryIcon(level: Int, isCharging: Boolean) {
//        val batteryIconRes = when {
//            isCharging -> R.drawable.battery_full
//            level >= 90 -> R.drawable.battery_full
//            level >= 60 -> R.drawable.battery_90
//            level >= 50 -> R.drawable.battery_80
//            level >= 20 -> R.drawable.battery_50
//            else -> R.drawable.battery_20
//        }
//        binding.imgBattery.setImageResource(batteryIconRes)
//        binding.txtBatteryStatus.text = "$level%"
//    }
//
//}

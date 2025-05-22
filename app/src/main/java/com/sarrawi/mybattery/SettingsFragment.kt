package com.sarrawi.mybattery

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sarrawi.mybattery.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val PICK_LOW_SOUND = 1001
    private val PICK_HIGH_SOUND = 1002
    private val REQUEST_CODE_READ_STORAGE = 2001

    private lateinit var prefs: android.content.SharedPreferences

    private var pendingRequestCodeForSound: Int? = null // لتخزين أي اختيار صوت بعد الموافقة على الصلاحية

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        prefs = requireContext().getSharedPreferences("battery_settings", Context.MODE_PRIVATE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("battery_settings", Context.MODE_PRIVATE)

        // عرض المسارات المحفوظة إذا موجودة
        binding.txtLowSoundPath.text = prefs.getString("low_sound_uri", "لم يتم اختيار أي ملف")
        binding.txtHighSoundPath.text = prefs.getString("high_sound_uri", "لم يتم اختيار أي ملف")

        binding.btnPickLowSound.setOnClickListener {
            showSoundChoiceDialog(isLowSound = true)
        }

        binding.btnPickHighSound.setOnClickListener {
            showSoundChoiceDialog(isLowSound = false)
        }
    }

    private fun showSoundChoiceDialog(isLowSound: Boolean) {
        val options = arrayOf("استخدام الصوت الافتراضي", "اختيار ملف صوت خارجي")

        AlertDialog.Builder(requireContext())
            .setTitle("اختر مصدر الصوت")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // الصوت الافتراضي
                        setDefaultSound(requireContext(),isLowSound)
                    }
                    1 -> { // اختيار ملف خارجي
                        val requestCode = if (isLowSound) PICK_LOW_SOUND else PICK_HIGH_SOUND
                        checkStoragePermissionThenPick(requestCode)
                    }
                }
            }
            .show()
    }

    private fun setDefaultSound(isLowSound: Boolean) {
        val defaultUriString = if (isLowSound) {
            // URI خاص بالصوت الافتراضي
            // سنستخدم هنا Uri "android.resource://" لبناء رابط للصوت في res/raw
            "android.resource://${requireContext().packageName}/${R.raw.lowbattery}"
        } else {
            "android.resource://${requireContext().packageName}/${R.raw.lowbattery}"
        }

        val editor = prefs.edit()
        if (isLowSound) {
            editor.putString("low_sound_uri", defaultUriString)
            binding.txtLowSoundPath.text = "الصوت الافتراضي"
        } else {
            editor.putString("high_sound_uri", defaultUriString)
            binding.txtHighSoundPath.text = "الصوت الافتراضي"
        }
        editor.apply()
    }

    private fun setDefaultSound(context: Context, isLowSound: Boolean) {
        val soundResId = if (isLowSound) R.raw.lowbattery else R.raw.lowbattery

        val afd = context.resources.openRawResourceFd(soundResId) ?: return
        val mediaPlayer = MediaPlayer()

        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )

        mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()

        mediaPlayer.prepare()
        mediaPlayer.start()
    }



    private fun checkStoragePermissionThenPick(requestCode: Int) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            // طلب الصلاحية
            pendingRequestCodeForSound = requestCode
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_READ_STORAGE)
        } else {
            // الصلاحية معطاة، افتح اختيار الصوت مباشرة
            openSoundPicker(requestCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_STORAGE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // الصلاحية معطاة، افتح اختيار الصوت الذي تم طلبه
                pendingRequestCodeForSound?.let {
                    openSoundPicker(it)
                }
            } else {
                Toast.makeText(requireContext(), "الصلاحية مطلوبة لاختيار ملف الصوت", Toast.LENGTH_SHORT).show()
            }
            pendingRequestCodeForSound = null
        }
    }

    @SuppressLint("WrongConstant")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && data != null) {
            val uri: Uri? = data.data
            if (uri != null) {
                // ✅ احصل على صلاحية دائمة للـ URI
                val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)

                val editor = prefs.edit()
                when (requestCode) {
                    PICK_LOW_SOUND -> {
                        editor.putString("low_sound_uri", uri.toString())
                        binding.txtLowSoundPath.text = uri.toString()
                    }
                    PICK_HIGH_SOUND -> {
                        editor.putString("high_sound_uri", uri.toString())
                        binding.txtHighSoundPath.text = uri.toString()
                    }
                }
                editor.apply()
            }
        }
    }

    private fun openSoundPicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, requestCode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

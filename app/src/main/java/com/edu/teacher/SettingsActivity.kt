package com.edu.teacher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.edu.teacher.databinding.ActivitySettingsBinding
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    private var teacherInfo = JSONObject()
    private var isNotificationsEnabled = true
    private var isSaved = false
    private var avatarBase64: String? = null
    private var isInitializing = true

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { compressAndSetImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("teacher_app", Context.MODE_PRIVATE)

        initViews()
        loadData()
        setupListeners()
    }

    private fun initViews() {
        if (::binding.isInitialized) {
            try {
                binding.pageTitle.text = getString(R.string.title_settings)
            } catch (e: Exception) { }

            binding.notificationsSwitch.apply {
                text = getString(R.string.settings_label_notifications)
                textOn = "ON"
                textOff = "OFF"
            }

            binding.darkModeSwitch.isChecked = TeacherApp.isDarkMode()
            updateThemeIcon()
        }
    }

    private fun updateThemeIcon() {
        try {
            val themeIcon = if (TeacherApp.isDarkMode()) R.drawable.ic_sun else R.drawable.ic_moon
            val iconView = binding.themeRow.findViewById<ImageView>(R.id.themeIcon)
            iconView?.setImageResource(themeIcon)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadData() {
        if (!::binding.isInitialized) return

        try {
            val teacherInfoStr = prefs.getString("teacher_info", null)
            if (teacherInfoStr != null) {
                try {
                    teacherInfo = JSONObject(teacherInfoStr)
                } catch (e: Exception) {
                    e.printStackTrace()
                    teacherInfo = JSONObject()
                }

                val name = teacherInfo.optString("name", getString(R.string.default_teacher))
                val id = teacherInfo.optString("id", getString(R.string.default_id))

                binding.nameInput.setText(name)
                binding.teacherNameDisplay.text = name
                binding.teacherIdDisplay.text = id

                avatarBase64 = teacherInfo.optString("avatar", "")

                val snapshot = avatarBase64
                if (!snapshot.isNullOrEmpty()) {
                    try {
                        val base64String = if (snapshot.contains(",")) {
                            snapshot.substringAfter(",")
                        } else {
                            snapshot
                        }

                        if (base64String.isNotEmpty()) {
                            val imageBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            binding.avatarImage.setImageBitmap(bitmap)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val savedNotifications = prefs.getBoolean("notifications_enabled", true)
            isNotificationsEnabled = savedNotifications
            binding.notificationsSwitch.isChecked = isNotificationsEnabled
        } catch (e: Exception) {
            e.printStackTrace()
            binding.notificationsSwitch.isChecked = true
        }
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.bottomNav.setOnItemSelectedListener { position ->
            when (position) {
                0 -> startActivity(android.content.Intent(this, DashboardActivity::class.java))
                1 -> startActivity(android.content.Intent(this, StatsActivity::class.java))
                2 -> startActivity(android.content.Intent(this, AddLessonActivity::class.java))
                3 -> startActivity(android.content.Intent(this, StudentsActivity::class.java))
                4 -> { }
            }
        }
        binding.bottomNav.setActiveTab(4)

        binding.cameraButton.setOnClickListener {
            try {
                pickImageLauncher.launch("image/*")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        binding.notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            try {
                isNotificationsEnabled = isChecked
                prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isInitializing) return@setOnCheckedChangeListener
            TeacherApp.applyThemeWithDebounce(isChecked, 400)
        }
        
        isInitializing = false

        binding.togglePasswordButton.setOnClickListener {
            val input = binding.passwordInput
            if (input.inputType == (android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            input.setSelection(input.text?.length ?: 0)
        }

        binding.saveButton.setOnClickListener { saveSettings() }
        binding.logoutButton.setOnClickListener { logout() }
        binding.resetButton.setOnClickListener { resetAllData() }
    }

    private fun compressAndSetImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 300, 300, true)
            binding.avatarImage.setImageBitmap(scaledBitmap)

            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val imageBytes = stream.toByteArray()
            avatarBase64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.error_image_load, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        val newName = binding.nameInput.text.toString().trim()
        val newPassword = binding.passwordInput.text.toString().trim()

        teacherInfo.put("name", newName)
        if (avatarBase64 != null) {
            teacherInfo.put("avatar", "data:image/jpeg;base64,$avatarBase64")
        }
        teacherInfo.put("theme", if (TeacherApp.isDarkMode()) "dark" else "light")
        
        val timestamp = try {
            DataManager.getSyncTimestamp()
        } catch (e: Exception) {
            e.printStackTrace()
            System.currentTimeMillis().toString()
        }
        teacherInfo.put("updatedAt", timestamp)

        DataManager.saveTeacherInfo(this, teacherInfo)

        if (newPassword.isNotEmpty() && newPassword.length >= 4) {
            prefs.edit().putString("teacher_password", newPassword).apply()
        }

        binding.teacherNameDisplay.text = newName

        isSaved = true
        binding.saveButton.text = getString(R.string.btn_save_success)
        binding.saveButton.isEnabled = false

        Toast.makeText(this, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show()

        binding.saveButton.postDelayed({
            isSaved = false
            binding.saveButton.text = getString(R.string.btn_save_settings)
            binding.saveButton.isEnabled = true
        }, 2000)
    }

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_logout)
            .setMessage(R.string.msg_confirm_logout_detailed)
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                prefs.edit().remove("is_logged_in").apply()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun resetAllData() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_factory_reset)
            .setMessage(R.string.msg_confirm_factory_reset)
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                prefs.edit().clear().apply()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }
}

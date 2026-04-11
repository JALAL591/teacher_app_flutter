package com.edu.teacher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    // ==================== المتغيرات ====================

    private lateinit var prefs: SharedPreferences
    private lateinit var backButton: Button
    private lateinit var themeToggleButton: Button
    private lateinit var pageTitle: TextView
    private lateinit var avatarImage: ImageView
    private lateinit var cameraButton: View
    private lateinit var nameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var togglePasswordButton: Button
    private lateinit var saveButton: Button
    private lateinit var logoutButton: Button
    private lateinit var resetButton: Button

    private var teacherInfo = JSONObject()
    private var isDarkMode = false
    private var showPassword = false
    private var isSaved = false
    private var newPassword = ""
    private var avatarBase64: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            compressAndSetImage(it)
        }
    }

    // ==================== دورة حياة النشاط ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("teacher_app", Context.MODE_PRIVATE)

        initViews()
        loadData()
        setupListeners()
        applyTheme()
    }

    // ==================== تهيئة العناصر ====================

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        themeToggleButton = findViewById(R.id.themeToggleButton)
        pageTitle = findViewById(R.id.pageTitle)
        avatarImage = findViewById(R.id.avatarImage)
        cameraButton = findViewById(R.id.cameraButton)
        nameInput = findViewById(R.id.nameInput)
        passwordInput = findViewById(R.id.passwordInput)
        togglePasswordButton = findViewById(R.id.togglePasswordButton)
        saveButton = findViewById(R.id.saveButton)
        logoutButton = findViewById(R.id.logoutButton)
        resetButton = findViewById(R.id.resetButton)

        pageTitle.text = "الإعدادات"
    }

    // ==================== تحميل البيانات ====================

    private fun loadData() {
        val teacherInfoStr = prefs.getString("teacher_info", null)
        if (teacherInfoStr != null) {
            teacherInfo = JSONObject(teacherInfoStr)
            val name = teacherInfo.optString("name", "المعلم")
            nameInput.setText(name)
            avatarBase64 = teacherInfo.optString("avatar", null)
            
            if (avatarBase64 != null && avatarBase64!!.isNotEmpty()) {
                val imageBytes = android.util.Base64.decode(avatarBase64!!.substringAfter(","), android.util.Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                avatarImage.setImageBitmap(bitmap)
            }
        }

        val savedTheme = prefs.getString("theme", "light")
        isDarkMode = savedTheme == "dark"
        updateThemeButton()
    }

    // ==================== إعداد المستمعين ====================

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }
        
        themeToggleButton.setOnClickListener {
            isDarkMode = !isDarkMode
            updateThemeButton()
            applyTheme()
            saveThemePreference()
        }
        
        cameraButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        
        togglePasswordButton.setOnClickListener {
            showPassword = !showPassword
            if (showPassword) {
                passwordInput.inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                togglePasswordButton.text = "👁️"
            } else {
                passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePasswordButton.text = "👁️‍🗨️"
            }
            passwordInput.setSelection(passwordInput.text.length)
        }
        
        saveButton.setOnClickListener { saveSettings() }
        logoutButton.setOnClickListener { logout() }
        resetButton.setOnClickListener { resetAllData() }
    }

    // ==================== ضغط الصورة وحفظها ====================

    private fun compressAndSetImage(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 300, 300, true)
        avatarImage.setImageBitmap(scaledBitmap)

        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val imageBytes = stream.toByteArray()
        avatarBase64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)
    }

    // ==================== إعدادات الوضع الليلي ====================

    private fun updateThemeButton() {
        if (isDarkMode) {
            themeToggleButton.text = "☀️"
            themeToggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.amber_500))
        } else {
            themeToggleButton.text = "🌙"
            themeToggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.indigo_600))
        }
    }

    private fun applyTheme() {
        val rootView = findViewById<View>(android.R.id.content).rootView
        if (isDarkMode) {
            rootView.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        } else {
            rootView.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background))
        }
    }

    private fun saveThemePreference() {
        prefs.edit().putString("theme", if (isDarkMode) "dark" else "light").apply()
    }

    // ==================== حفظ الإعدادات ====================

    private fun saveSettings() {
        val newName = nameInput.text.toString().trim()
        
        teacherInfo.put("name", newName)
        if (avatarBase64 != null) {
            teacherInfo.put("avatar", "data:image/jpeg;base64,$avatarBase64")
        }
        teacherInfo.put("theme", if (isDarkMode) "dark" else "light")
        
        prefs.edit().putString("teacher_info", teacherInfo.toString()).apply()
        
        if (newPassword.isNotEmpty()) {
            prefs.edit().putString("teacher_password", newPassword).apply()
        }
        
        isSaved = true
        saveButton.text = "✓ تم حفظ البيانات"
        saveButton.setBackgroundColor(ContextCompat.getColor(this, R.color.emerald_500))
        
        Toast.makeText(this, "✅ تم حفظ الإعدادات بنجاح", Toast.LENGTH_SHORT).show()
        
        saveButton.postDelayed({
            isSaved = false
            saveButton.text = "💾 حفظ التعديلات"
            saveButton.setBackgroundColor(ContextCompat.getColor(this, R.color.indigo_600))
        }, 2000)
    }

    // ==================== تسجيل الخروج ====================

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("تسجيل الخروج")
            .setMessage("هل تريد تسجيل الخروج؟")
            .setPositiveButton("نعم") { _, _ ->
                prefs.edit().remove("is_logged_in").apply()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ==================== إعادة ضبط المصنع ====================

    private fun resetAllData() {
        AlertDialog.Builder(this)
            .setTitle("إعادة ضبط المصنع")
            .setMessage("سيتم مسح كل بيانات الطلاب والصفوف! هل أنت متأكد؟")
            .setPositiveButton("نعم") { _, _ ->
                prefs.edit().clear().apply()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
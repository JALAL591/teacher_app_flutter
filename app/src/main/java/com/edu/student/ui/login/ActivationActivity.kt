package com.edu.student.ui.login

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.edu.teacher.databinding.*
import com.edu.student.data.repository.StudentRepository
import com.edu.student.ui.dashboard.DashboardActivity
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ActivationActivity : AppCompatActivity() {
    
    private lateinit var binding: StudentActivityActivationBinding
    private lateinit var repository: StudentRepository
    
    private var avatarBase64: String? = null
    
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImage(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivityActivationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = StudentRepository(this)
        
        if (repository.isLoggedIn()) {
            navigateToDashboard()
            return
        }
        
        setupViews()
    }
    
    private fun setupViews() {
        binding.avatarContainer.setOnClickListener {
            pickImage.launch("image/*")
        }
        
        binding.activateButton.setOnClickListener {
            activateStudent()
        }
    }
    
    private fun handleImage(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            avatarBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT)
            
            binding.avatarImage.setImageBitmap(bitmap)
            binding.avatarPlaceholder.visibility = View.GONE
            binding.avatarImage.visibility = View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, "فشل تحميل الصورة", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun activateStudent() {
        val name = binding.nameInput.text.toString().trim()
        
        if (name.length < 3) {
            binding.errorText.visibility = View.VISIBLE
            binding.errorText.text = "من فضلك اكتب اسمك الحقيقي (3 أحرف على الأقل)"
            return
        }
        
        binding.errorText.visibility = View.GONE
        
        val student = repository.createStudent(name, avatarBase64)
        
        val teacherPrefs = getSharedPreferences("teacher_app", MODE_PRIVATE)
        teacherPrefs.edit().apply {
            putString("user_role", "student")
            putBoolean("is_logged_in", true)
            apply()
        }
        
        Toast.makeText(this, "مرحباً بك يا ${student.name}! 🌟", Toast.LENGTH_LONG).show()
        navigateToDashboard()
    }
    
    private fun navigateToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}

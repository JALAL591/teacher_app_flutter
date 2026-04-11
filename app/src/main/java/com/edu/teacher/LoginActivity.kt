package com.edu.teacher

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Random

class LoginActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        prefs = getSharedPreferences("teacher_app", MODE_PRIVATE)
        
        nameInput = findViewById(R.id.nameInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        
        loginButton.setOnClickListener {
            handleLogin()
        }
    }
    
    private fun generateTeacherID(name: String): String {
        val randomDigits = Random().nextInt(9000) + 1000
        val cleanName = name.trim().split(" ")[0]
        return "${cleanName}_$randomDigits"
    }
    
    private fun handleLogin() {
        val name = nameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        
        if (name.length < 3) {
            Toast.makeText(this, "الاسم قصير جداً", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 4) {
            Toast.makeText(this, "كلمة المرور يجب أن تكون 4 رموز على الأقل", Toast.LENGTH_SHORT).show()
            return
        }
        
        val teacherId = generateTeacherID(name)
        
        // حفظ بيانات المعلم
        prefs.edit().apply {
            putString("teacher_id", teacherId)
            putString("teacher_name", name)
            putString("teacher_password", password)
            putBoolean("is_logged_in", true)
            putString("user_role", "teacher")
            commit()
        }
        
        Toast.makeText(this, "أهلاً بك أستاذ $name! معرفك: $teacherId", Toast.LENGTH_LONG).show()
        
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}
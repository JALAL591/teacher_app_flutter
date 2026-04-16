package com.edu.teacher

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.edu.teacher.databinding.ActivityLoginBinding
import org.json.JSONArray
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("teacher_app", MODE_PRIVATE)

        // Check if user is already logged in
        checkExistingLogin()

        setupButtons()
    }

    private fun checkExistingLogin() {
        val role = prefs.getString("user_role", "teacher")
        
        if (prefs.getBoolean("is_logged_in", false)) {
            when (role) {
                "teacher" -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
                "student" -> {
                    startActivity(Intent(this, com.edu.student.ui.dashboard.DashboardActivity::class.java))
                    finish()
                }
            }
        }
    }

    private fun setupButtons() {
        binding.loginButton.setOnClickListener {
            handleLogin()
        }
    }

    private fun handleLogin() {
        val name = binding.nameInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()

        if (name.length < 3) {
            Toast.makeText(this, R.string.warning_enter_name_short, Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 4) {
            Toast.makeText(this, R.string.warning_password_short, Toast.LENGTH_SHORT).show()
            return
        }

        // Generate unique ID using UUID
        val teacherId = DataManager.generateTeacherId(name)

        // Save teacher data in organized JSON
        val classesArray = JSONArray()
        val teacherInfo = JSONObject().apply {
            put("id", teacherId as Any)
            put("name", name as Any)
            put("classes", classesArray as Any)
            put("createdAt", DataManager.getSyncTimestamp() as Any)
        }

        prefs.edit().apply {
            putString("teacher_info", teacherInfo.toString())
            putString("teacher_password", password)
            putBoolean("is_logged_in", true)
            putString("user_role", "teacher")
            commit()
        }

        Toast.makeText(this, getString(R.string.welcome_teacher, name, teacherId), Toast.LENGTH_LONG).show()

        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}

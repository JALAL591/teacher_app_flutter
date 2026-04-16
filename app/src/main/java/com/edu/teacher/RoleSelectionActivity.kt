package com.edu.teacher

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.edu.teacher.databinding.ActivityRoleSelectionBinding
import com.edu.student.ui.login.ActivationActivity

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("teacher_app", MODE_PRIVATE)

        checkExistingRole()

        binding.teacherButton.setOnClickListener {
            prefs.edit().putString("user_role", "teacher").apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.studentButton.setOnClickListener {
            prefs.edit().putString("user_role", "student").apply()
            startActivity(Intent(this, ActivationActivity::class.java))
            finish()
        }
    }

    private fun checkExistingRole() {
        val role = prefs.getString("user_role", "")
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)

        if (isLoggedIn && !role.isNullOrEmpty()) {
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
}
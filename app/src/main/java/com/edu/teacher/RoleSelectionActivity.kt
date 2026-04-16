package com.edu.teacher

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.edu.teacher.databinding.ActivityRoleSelectionBinding
import com.edu.student.ui.login.ActivationActivity

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.teacherButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.studentButton.setOnClickListener {
            startActivity(Intent(this, ActivationActivity::class.java))
            finish()
        }
    }
}
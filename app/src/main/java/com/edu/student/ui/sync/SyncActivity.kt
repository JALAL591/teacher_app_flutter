package com.edu.student.ui.sync

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.edu.teacher.R
import com.edu.teacher.databinding.*
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.Student

class SyncActivity : AppCompatActivity() {
    
    private lateinit var binding: StudentActivitySyncBinding
    private lateinit var repository: StudentRepository
    
    private val academicLevels = listOf(
        "الأول الابتدائي", "الثاني الابتدائي", "الثالث الابتدائي",
        "الرابع الابتدائي", "الخامس الابتدائي", "السادس الابتدائي",
        "الأول المتوسط", "الثاني المتوسط", "الثالث المتوسط",
        "الأول الثانوي", "الثاني الثانوي", "الثالث الثانوي"
    )
    
    private val sections = listOf("أ", "ب", "ج", "د", "هـ")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivitySyncBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = StudentRepository(this)
        
        setupViews()
        loadStudentData()
    }
    
    private fun setupViews() {
        binding.backButton.setOnClickListener { finish() }
        
        val gradeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, academicLevels)
        gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.gradeSpinner.adapter = gradeAdapter
        
        val sectionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sections.map { "شعبة $it" })
        sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.sectionSpinner.adapter = sectionAdapter
        
        binding.saveButton.setOnClickListener {
            saveStudentData()
        }
        
        binding.addTeacherButton.setOnClickListener {
            addTeacher()
        }
    }
    
    private fun loadStudentData() {
        val student = repository.getStudent() ?: return
        
        binding.studentName.text = student.name
        binding.nameInput.setText(student.name)
        
        val gradeIndex = academicLevels.indexOf(student.grade)
        if (gradeIndex >= 0) {
            binding.gradeSpinner.setSelection(gradeIndex)
        }
        
        val sectionIndex = sections.indexOf(student.section)
        if (sectionIndex >= 0) {
            binding.sectionSpinner.setSelection(sectionIndex)
        }
        
        updateThemeSwitch()
    }
    
    private fun updateThemeSwitch() {
        val isDark = repository.getTheme() == "dark"
        binding.themeSwitch.isChecked = isDark
    }
    
    private fun saveStudentData() {
        val student = repository.getStudent() ?: return
        
        val newName = binding.nameInput.text.toString().trim()
        if (newName.length < 3) {
            Toast.makeText(this, "الاسم قصير جداً", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedGrade = binding.gradeSpinner.selectedItem.toString()
        val selectedSection = sections.getOrNull(binding.sectionSpinner.selectedItemPosition) ?: "أ"
        
        val updatedStudent = student.copy(
            name = newName,
            grade = selectedGrade,
            section = selectedSection
        )
        
        repository.updateStudent(updatedStudent)
        Toast.makeText(this, "تم حفظ البيانات ✓", Toast.LENGTH_SHORT).show()
    }
    
    private fun addTeacher() {
        val teacherCode = binding.teacherCodeInput.text.toString().trim()
        
        if (teacherCode.isEmpty()) {
            Toast.makeText(this, "من فضلك أدخل كود المعلم", Toast.LENGTH_SHORT).show()
            return
        }
        
        repository.getStudent()?.let { student ->
            val updatedStudent = student.copy()
            repository.updateStudent(updatedStudent)
            
            val prefs = com.edu.student.data.preferences.StudentPreferences(this)
            prefs.setAssignedTeacherId(teacherCode)
            
            Toast.makeText(this, "تمت إضافة المعلم بنجاح ✅", Toast.LENGTH_SHORT).show()
            binding.teacherCodeInput.text?.clear()
        }
    }
    
    fun onThemeToggle(@Suppress("UNUSED_PARAMETER") view: View) {
        val isDark = binding.themeSwitch.isChecked
        repository.setTheme(if (isDark) "dark" else "light")
    }
}

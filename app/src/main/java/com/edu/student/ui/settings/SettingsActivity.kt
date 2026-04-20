package com.edu.student.ui.settings

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.R
import com.edu.teacher.databinding.StudentActivitySettingsBinding
import com.edu.student.StudentApp
import com.edu.student.data.preferences.StudentPreferences
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.JoinedClass
import com.edu.student.domain.model.Student
import com.edu.student.services.TeacherClient
import com.edu.student.ui.common.SyncSubjectAdapter
import com.edu.student.ui.dashboard.DashboardActivity
import com.edu.student.ui.login.ActivationActivity
import com.edu.student.ui.stats.StatsActivity
import java.io.ByteArrayOutputStream

class SettingsActivity : AppCompatActivity(), TeacherClient.ClientCallback {

    private lateinit var binding: StudentActivitySettingsBinding
    private lateinit var repository: StudentRepository
    private lateinit var prefs: StudentPreferences
    private val teacherClient: TeacherClient by lazy { 
        StudentApp.getTeacherClient(this) 
    }

    private var isTeacherNearby = false
    private var syncingId: String? = null

    private val academicLevels = listOf(
        "الأول الابتدائي", "الثاني الابتدائي", "الثالث الابتدائي",
        "الرابع الابتدائي", "الخامس الابتدائي", "السادس الابتدائي",
        "الأول المتوسط", "الثاني المتوسط", "الثالث المتوسط",
        "الأول الثانوي", "الثاني الثانوي", "الثالث الثانوي"
    )

    private val sections = listOf("أ", "ب", "ج", "د", "هـ")

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = StudentRepository(this)
        prefs = StudentPreferences(this)
        teacherClient.setCallback(this)
        updateConnectionUI()

        setupViews()
        loadStudentData()
    }
    
    private fun updateConnectionUI() {
        isTeacherNearby = teacherClient.isConnected
        if (teacherClient.isConnected) {
            binding.connectionCard.setBackgroundColor(android.graphics.Color.parseColor("#10B981"))
            binding.connectionStatus.text = "المعلم متاح الآن بالقرب منك!"
            binding.connectionHint.text = "يمكنك استلام الدروس مباشرة"
        } else {
            binding.connectionCard.setBackgroundColor(android.graphics.Color.parseColor("#9CA3AF"))
            binding.connectionStatus.text = "يبحث عن رادار المعلم..."
            binding.connectionHint.text = "تأكد من فتح المعلم لشبكة الـ Hotspot"
        }
    }

    private fun setupViews() {
        binding.backButton.setOnClickListener { finish() }

        binding.themeButton.setOnClickListener {
            toggleTheme()
        }

        binding.avatarContainer.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.editButton.setOnClickListener {
            binding.editForm.isVisible = !binding.editForm.isVisible
            binding.editButton.text = if (binding.editForm.isVisible) "إغلاق التعديل" else "تعديل البيانات"
        }

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

        binding.themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (StudentApp.isInitialized()) {
                StudentApp.toggleTheme(isChecked)
            }
        }

        binding.logoutButton.setOnClickListener {
            logout()
        }

        binding.bottomNav.setOnItemSelectedListener { position ->
            when (position) {
                0 -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
                1 -> {
                    startActivity(Intent(this, StatsActivity::class.java))
                    finish()
                }
                2 -> { }
            }
        }

        binding.bottomNav.setActiveTab(2)
        binding.subjectsRecycler.layoutManager = LinearLayoutManager(this)
    }

    private fun loadStudentData() {
        val student = repository.getStudent() ?: return

        binding.studentName.text = student.name
        binding.nameInput.setText(student.name)

        val gradeText = "${student.grade} - ${student.section}"
        binding.gradeSectionText.text = gradeText

        val gradeIndex = academicLevels.indexOf(student.grade)
        if (gradeIndex >= 0) {
            binding.gradeSpinner.setSelection(gradeIndex)
        }

        val sectionIndex = sections.indexOf(student.section)
        if (sectionIndex >= 0) {
            binding.sectionSpinner.setSelection(sectionIndex)
        }

        if (student.avatar?.isNotEmpty() == true) {
            try {
                val bytes = Base64.decode(student.avatar, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                binding.avatarImage.setImageBitmap(bitmap)
                binding.avatarImage.visibility = View.VISIBLE
                binding.avatarPlaceholder.visibility = View.GONE
            } catch (e: Exception) { }
        }

        binding.themeSwitch.isChecked = 
            if (StudentApp.isInitialized()) StudentApp.isDarkMode() else false

        loadJoinedClasses()
    }

    private fun loadJoinedClasses() {
        val student = repository.getStudent()
        val joinedClasses = student?.joinedClasses ?: emptyList()

        if (joinedClasses.isEmpty()) {
            binding.emptySubjectsText.visibility = View.VISIBLE
            binding.subjectsRecycler.visibility = View.GONE
        } else {
            binding.emptySubjectsText.visibility = View.GONE
            binding.subjectsRecycler.visibility = View.VISIBLE
            binding.subjectsRecycler.adapter = SyncSubjectAdapter(joinedClasses) { cls ->
                syncWithRadar(cls)
            }
        }
    }

    private fun syncWithRadar(joinedClass: JoinedClass) {
        val syncKey = "${joinedClass.teacherId}_${joinedClass.subject}"

        if (syncingId != null) {
            Toast.makeText(this, "جاري المزامنة...", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isTeacherNearby) {
            Toast.makeText(this, "المعلم غير متصل بالرادار", Toast.LENGTH_SHORT).show()
            return
        }

        syncingId = syncKey
        Toast.makeText(this, "جاري سحب دروس ${joinedClass.subject} من المعلم... 📡", Toast.LENGTH_SHORT).show()

        val student = repository.getStudent()
        if (student != null) {
            teacherClient.requestLessons(student.grade, student.section)
        }

        binding.subjectsRecycler.adapter?.notifyDataSetChanged()

        binding.root.postDelayed({
            syncingId = null
            Toast.makeText(this, "اكتملت المزامنة بنجاح ✅", Toast.LENGTH_SHORT).show()
            binding.subjectsRecycler.adapter?.notifyDataSetChanged()
        }, 1500)
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

        binding.editForm.visibility = View.GONE
        binding.editButton.text = "تعديل البيانات"
        loadStudentData()
    }

    private fun addTeacher() {
        val teacherCode = binding.teacherCodeInput.text.toString().trim().lowercase()

        if (teacherCode.isEmpty()) {
            Toast.makeText(this, "من فضلك أدخل كود المعلم", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.setAssignedTeacherId(teacherCode)

        val student = repository.getStudent()
        if (student != null) {
            val newClass = JoinedClass(
                teacherId = teacherCode,
                teacherName = "المعلم الحالي",
                subject = "مادة جديدة",
                lastSync = "الآن",
                status = "active"
            )
            val updatedClasses = student.joinedClasses + newClass
            val updatedStudent = student.copy(joinedClasses = updatedClasses)
            repository.updateStudent(updatedStudent)
        }

        if (isTeacherNearby) {
            teacherClient.sendCustomData(mapOf(
                "type" to "REGISTER_STUDENT",
                "studentId" to (student?.id ?: ""),
                "studentName" to (student?.name ?: ""),
                "name" to (student?.name ?: ""),
                "grade" to (student?.grade ?: ""),
                "section" to (student?.section ?: ""),
                "teacherId" to teacherCode
            ))
        }

        Toast.makeText(this, "تمت إضافة المعلم بنجاح ✅", Toast.LENGTH_SHORT).show()
        binding.teacherCodeInput.text?.clear()
        loadJoinedClasses()
    }

    private fun handleImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            val avatarBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT)

            val student = repository.getStudent()
            if (student != null) {
                repository.updateStudent(student.copy(avatar = avatarBase64))
            }

            binding.avatarImage.setImageBitmap(bitmap)
            binding.avatarImage.visibility = View.VISIBLE
            binding.avatarPlaceholder.visibility = View.GONE
        } catch (e: Exception) {
            Toast.makeText(this, "فشل تحميل الصورة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleTheme() {
        val isDark = repository.getTheme() == "dark"
        repository.setTheme(if (isDark) "light" else "dark")
    }

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("تسجيل الخروج")
            .setMessage("هل أنت متأكد من تسجيل الخروج وإعادة التفعيل؟")
            .setPositiveButton("نعم") { _, _ ->
                prefs.clearAll()
                getSharedPreferences("teacher_app", MODE_PRIVATE).edit().clear().apply()
                startActivity(Intent(this, ActivationActivity::class.java))
                finishAffinity()
            }
            .setNegativeButton("لا", null)
            .show()
    }

    override fun onConnected(ip: String) {
        runOnUiThread {
            isTeacherNearby = true
            binding.connectionCard.setBackgroundColor(android.graphics.Color.parseColor("#10B981"))
            binding.connectionStatus.text = "المعلم متاح الآن بالقرب منك!"
            binding.connectionHint.text = "يمكنك استلام الدروس مباشرة"
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            isTeacherNearby = false
            binding.connectionCard.setBackgroundColor(android.graphics.Color.parseColor("#9CA3AF"))
            binding.connectionStatus.text = "يبحث عن رادار المعلم..."
            binding.connectionHint.text = "تأكد من فتح المعلم لشبكة الـ Hotspot"
        }
    }

    override fun onLessonsReceived(lessons: List<com.edu.student.domain.model.Lesson>) {
        repository.saveLessons(lessons)
        runOnUiThread {
            loadJoinedClasses()
            Toast.makeText(this, "تم تحديث الدروس من المعلم!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            syncingId = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        teacherClient.setCallback(null)
    }
}

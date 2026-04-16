package com.edu.teacher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.databinding.ActivityStatsBinding
import com.edu.teacher.databinding.ItemStudentStatsBinding
import com.edu.teacher.databinding.ItemSubjectBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    private var subjectsList = mutableListOf<JSONObject>()
    private var activeSubject: JSONObject? = null
    private var studentsList = mutableListOf<StudentItem>()
    private var searchTerm = ""
    private var teacherId: String = ""

    // ألوان عشوائية للأفاتار
    private val avatarColors = listOf(
        R.color.tg_blue,
        R.color.tg_green,
        R.color.tg_red,
        R.color.tg_yellow,
        R.color.primary_blue,
        R.color.emerald_500,
        R.color.amber_500,
        R.color.rose_500
    )

    data class StudentItem(
        val id: String,
        val name: String,
        var points: Int,
        val attendanceHistory: MutableList<AttendanceRecord>
    )

    data class AttendanceRecord(val date: String, val status: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        teacherId = DataManager.getTeacherId(this) ?: run {
            Toast.makeText(this, getString(R.string.error_teacher_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        loadSubjects()
    }

    private fun initViews() {
        binding.subjectsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.studentsRecyclerView.layoutManager = LinearLayoutManager(this)

        binding.backButton.setOnClickListener { activeSubject = null; showSubjectsList() }
        binding.syncButton.setOnClickListener { syncData() }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { searchTerm = s.toString(); filterStudents() }
        })

        // Telegram-style bottom navigation
        binding.bottomNav.setOnItemSelectedListener { position ->
            when (position) {
                0 -> startActivity(android.content.Intent(this, DashboardActivity::class.java))
                1 -> { } // Stats - current page
                2 -> startActivity(android.content.Intent(this, AddLessonActivity::class.java))
                3 -> startActivity(android.content.Intent(this, StudentsActivity::class.java))
                4 -> startActivity(android.content.Intent(this, SettingsActivity::class.java))
            }
        }
        binding.bottomNav.setActiveTab(1)
    }

    private fun loadSubjects() {
        subjectsList.clear()
        subjectsList.addAll(DataManager.getSubjects(this, teacherId))
        showSubjectsList()
    }

    private fun showSubjectsList() {
        binding.subjectTitle.visibility = View.GONE
        binding.gradeSectionText.visibility = View.GONE
        binding.backButton.visibility = View.GONE
        binding.syncButton.visibility = View.GONE
        binding.searchInputLayout.visibility = View.GONE
        binding.studentsRecyclerView.visibility = View.GONE
        binding.summaryCard.visibility = View.GONE
        binding.subjectsRecyclerView.visibility = View.VISIBLE

        binding.subjectsRecyclerView.adapter = SubjectsAdapter(subjectsList) { subject ->
            activeSubject = subject
            showStudentsList()
            syncData()
        }
    }

    private fun showStudentsList() {
        binding.subjectsRecyclerView.visibility = View.GONE
        binding.searchInputLayout.visibility = View.VISIBLE
        binding.studentsRecyclerView.visibility = View.VISIBLE
        binding.subjectTitle.visibility = View.VISIBLE
        binding.gradeSectionText.visibility = View.VISIBLE
        binding.backButton.visibility = View.VISIBLE
        binding.syncButton.visibility = View.VISIBLE
        binding.summaryCard.visibility = View.VISIBLE

        activeSubject?.let {
            binding.subjectTitle.text = it.optString("name", getString(R.string.default_subject))
            binding.gradeSectionText.text = getString(R.string.text_manage_attendance)
        }
    }

    private fun syncData() {
        if (activeSubject == null) return

        binding.syncButton.isEnabled = false

        val subjectId = activeSubject?.optString("id") ?: ""
        val classes = DataManager.getClasses(this, teacherId, subjectId)

        // محاكاة بيانات الطلاب
        val mockStudents = listOf(
            StudentItem(DataManager.generateSubmissionId(), getString(R.string.mock_student_1), 85, mutableListOf()),
            StudentItem(DataManager.generateSubmissionId(), getString(R.string.mock_student_2), 92, mutableListOf()),
            StudentItem(DataManager.generateSubmissionId(), getString(R.string.mock_student_3), 78, mutableListOf()),
            StudentItem(DataManager.generateSubmissionId(), getString(R.string.mock_student_4), 96, mutableListOf())
        )

        studentsList.clear()
        studentsList.addAll(mockStudents)

        // تحميل سجل الحضور
        classes.forEach { cls ->
            val classId = cls.optString("id")
            val historyMap = DataManager.getAttendance(this, classId)
            studentsList.forEach { student ->
                val historyArray = historyMap.optJSONArray(student.id)
                if (historyArray != null) {
                    student.attendanceHistory.clear()
                    for (i in 0 until historyArray.length()) {
                        val record = historyArray.getJSONObject(i)
                        student.attendanceHistory.add(AttendanceRecord(
                            record.optString("date"), record.optString("status")
                        ))
                    }
                }
            }
        }

        // تحديث الإحصائيات
        updateStats()

        binding.studentsRecyclerView.adapter = StudentsAdapter(studentsList) { student, status ->
            markAttendance(student, status)
        }

        Toast.makeText(this, getString(R.string.toast_students_fetched, studentsList.size), Toast.LENGTH_SHORT).show()
        binding.syncButton.isEnabled = true
    }

    private fun updateStats() {
        binding.statsStudentsNumber.text = studentsList.size.toString()
        binding.statsClassesNumber.text = activeSubject?.let {
            DataManager.getClasses(this, teacherId, it.optString("id")).size.toString()
        } ?: "0"
        val totalPoints = studentsList.sumOf { it.points }
        binding.statsPointsNumber.text = totalPoints.toString()
    }

    private fun markAttendance(student: StudentItem, status: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val filtered = student.attendanceHistory.filter { it.date != today }.toMutableList()
        filtered.add(AttendanceRecord(today, status))
        student.attendanceHistory.clear()
        student.attendanceHistory.addAll(filtered)

        if (status == "present") student.points += 5

        // حفظ الحضور
        val activeClassId = activeSubject?.let {
            DataManager.getClasses(this, teacherId, it.optString("id")).firstOrNull()?.optString("id")
        } ?: ""

        if (activeClassId.isNotEmpty()) {
            val historyMap = JSONObject()
            studentsList.forEach { s ->
                val historyArray = JSONArray()
                s.attendanceHistory.forEach { record ->
                    val recordObj = JSONObject()
                    recordObj.put("date", record.date as Any)
                    recordObj.put("status", record.status as Any)
                    historyArray.put(recordObj)
                }
                historyMap.put(s.id, historyArray as Any)
            }
            DataManager.saveAttendance(this, activeClassId, historyMap)
        }

        binding.studentsRecyclerView.adapter?.notifyDataSetChanged()
        updateStats()
        Toast.makeText(this, if (status == "present") getString(R.string.toast_present) else getString(R.string.toast_absent), Toast.LENGTH_SHORT).show()
    }

    private fun filterStudents() {
        val filtered = if (searchTerm.isEmpty()) studentsList
        else studentsList.filter { it.name.contains(searchTerm, ignoreCase = true) }
        (binding.studentsRecyclerView.adapter as? StudentsAdapter)?.updateList(filtered)
    }

    inner class SubjectsAdapter(
        private val subjects: List<JSONObject>,
        private val onItemClick: (JSONObject) -> Unit
    ) : RecyclerView.Adapter<SubjectsAdapter.SubjectViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
            val binding = ItemSubjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SubjectViewHolder(binding)
        }

        override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
            val subject = subjects[position]
            holder.binding.subjectText.text = subject.optString("name", getString(R.string.default_subject))
            val classesCount = DataManager.getClasses(this@StatsActivity, teacherId, subject.optString("id")).size
            holder.binding.gradeText.text = getString(R.string.text_classes_count, classesCount)
            holder.binding.root.setOnClickListener { onItemClick(subject) }
        }

        override fun getItemCount(): Int = subjects.size

        inner class SubjectViewHolder(val binding: ItemSubjectBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class StudentsAdapter(
        private var students: List<StudentItem>,
        private val onAttendanceClick: (StudentItem, String) -> Unit
    ) : RecyclerView.Adapter<StudentsAdapter.StudentViewHolder>() {

        fun updateList(newList: List<StudentItem>) { students = newList; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
            val binding = ItemStudentStatsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return StudentViewHolder(binding)
        }

        override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
            val student = students[position]
            holder.binding.studentName.text = student.name
            holder.binding.studentPoints.text = getString(R.string.points_format, student.points)
            holder.binding.studentDetails.text = activeSubject?.optString("name", getString(R.string.default_subject)) ?: ""

            // تعيين لون عشوائي للأفاتار
            val colorRes = avatarColors[position % avatarColors.size]
            val avatarBg = holder.binding.studentAvatar.background
            if (avatarBg is GradientDrawable) {
                avatarBg.setColor(ContextCompat.getColor(this@StatsActivity, colorRes))
            }

            holder.binding.studentEmoji.setImageResource(R.drawable.ic_student)

            holder.binding.presentButton.setOnClickListener { onAttendanceClick(student, "present") }
            holder.binding.absentButton.setOnClickListener { onAttendanceClick(student, "absent") }
        }

        override fun getItemCount(): Int = students.size

        inner class StudentViewHolder(val binding: ItemStudentStatsBinding) : RecyclerView.ViewHolder(binding.root)
    }
}

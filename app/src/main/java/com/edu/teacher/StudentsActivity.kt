package com.edu.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.databinding.ActivityStudentsBinding
import com.edu.teacher.databinding.ItemStudentBinding

/**
 * Students screen - displays list of all students from all classes
 * in Telegram Chat List Style
 */
class StudentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentsBinding

    private var studentsList = mutableListOf<StudentItem>()
    private var teacherId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        teacherId = DataManager.getTeacherId(this) ?: run {
            Toast.makeText(this, R.string.error_teacher_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        loadStudents()
    }

    private fun initViews() {
        binding.pageTitle.text = getString(R.string.title_students)
        binding.backButton.setOnClickListener { finish() }
        binding.studentsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.studentsRecyclerView.adapter = StudentsAdapter(studentsList)

        // Telegram-style bottom navigation
        binding.bottomNav.setOnItemSelectedListener { position ->
            when (position) {
                0 -> startActivity(android.content.Intent(this, DashboardActivity::class.java))
                1 -> startActivity(android.content.Intent(this, StatsActivity::class.java))
                2 -> startActivity(android.content.Intent(this, AddLessonActivity::class.java))
                3 -> { } // Students/Attendance - navigate to StudentsActivity for attendance
                4 -> startActivity(android.content.Intent(this, SettingsActivity::class.java))
            }
        }
        binding.bottomNav.setActiveTab(3)
    }

    private fun loadStudents() {
        studentsList.clear()

        // Collect all students from all classes and subjects
        val subjects = DataManager.getSubjects(this, teacherId)
        subjects.forEach { subject ->
            val subjectId = subject.optString("id")
            val subjectName = subject.optString("name", getString(R.string.default_subject))
            val classes = DataManager.getClasses(this, teacherId, subjectId)

            classes.forEach { classData ->
                val className = classData.optString("name", getString(R.string.default_class))
                val grade = classData.optString("grade", "")
                val section = classData.optString("section", "")

                val studentCount = classData.optInt("studentCount", 0)
                if (studentCount > 0) {
                    studentsList.add(
                        StudentItem(
                            name = className,
                            details = "$grade \u2022 $section",
                            subject = subjectName,
                            count = studentCount,
                            emoji = getSubjectEmoji(subjectName)
                        )
                    )
                }
            }
        }

        // If no students, show empty state
        if (studentsList.isEmpty()) {
            studentsList.add(
                StudentItem(
                    name = getString(R.string.empty_no_students),
                    details = getString(R.string.empty_students),
                    subject = "",
                    count = 0,
                    emoji = EmojiConstants.PEOPLE
                )
            )
        }

        binding.pageTitle.text = getString(R.string.title_students) + " (${studentsList.size})"
        binding.studentsRecyclerView.adapter?.notifyDataSetChanged()
    }

    private fun getSubjectEmoji(subject: String): String {
        val s = subject.lowercase()
        return when {
            s.contains("قرآن") || s.contains("إسلام") -> EmojiConstants.QURAN_ISLAMIC
            s.contains("رياضيات") || s.contains("حساب") -> EmojiConstants.MATH
            s.contains("علوم") || s.contains("فيزياء") || s.contains("كيمياء") -> EmojiConstants.SCIENCE
            s.contains("عربي") || s.contains("لغة") -> EmojiConstants.ARABIC
            s.contains("إنجليزي") || s.contains("انجليزي") -> EmojiConstants.ENGLISH
            s.contains("تاريخ") || s.contains("جغرافيا") -> EmojiConstants.HISTORY_GEOGRAPHY
            s.contains("رياضة") || s.contains("بدنية") -> EmojiConstants.PE_SPORTS
            s.contains("فن") || s.contains("رسم") -> EmojiConstants.ART
            else -> EmojiConstants.DEFAULT_SUBJECT
        }
    }

    data class StudentItem(
        val name: String,
        val details: String,
        val subject: String,
        val count: Int,
        val emoji: String
    )

    inner class StudentsAdapter(
        private val students: List<StudentItem>
    ) : RecyclerView.Adapter<StudentsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val student = students[position]

            holder.binding.studentEmoji.setImageResource(R.drawable.ic_student)
            holder.binding.studentName.text = student.name
            holder.binding.studentDetails.text = if (student.count > 0) {
                getString(R.string.text_student_details, student.subject, student.count.toString())
            } else {
                student.details
            }

            holder.binding.root.setOnClickListener {
                Toast.makeText(this@StudentsActivity, student.name, Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount(): Int = students.size

        inner class ViewHolder(val binding: ItemStudentBinding) : RecyclerView.ViewHolder(binding.root)
    }
}

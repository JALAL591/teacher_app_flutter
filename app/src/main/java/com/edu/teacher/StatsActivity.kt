package com.edu.teacher

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsActivity : AppCompatActivity() {

    // ==================== المتغيرات ====================

    private lateinit var prefs: SharedPreferences
    private lateinit var subjectsRecyclerView: RecyclerView
    private lateinit var studentsRecyclerView: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var backButton: Button
    private lateinit var syncButton: Button
    private lateinit var subjectTitle: TextView
    private lateinit var gradeSectionText: TextView

    private var subjectsList = mutableListOf<JSONObject>()
    private var activeSubject: JSONObject? = null
    private var studentsList = mutableListOf<StudentItem>()
    private var isLoading = false
    private var searchTerm = ""

    data class StudentItem(
        val id: String,
        val name: String,
        var points: Int,
        val attendanceHistory: MutableList<AttendanceRecord>
    )

    data class AttendanceRecord(
        val date: String,
        val status: String
    )

    companion object {
        private const val TEACHER_ID = "TCH-001"
    }

    // ==================== دورة حياة النشاط ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        prefs = getSharedPreferences("teacher_app", Context.MODE_PRIVATE)

        initViews()
        loadSubjects()
    }

    // ==================== تهيئة العناصر ====================

    private fun initViews() {
        subjectsRecyclerView = findViewById(R.id.subjectsRecyclerView)
        studentsRecyclerView = findViewById(R.id.studentsRecyclerView)
        searchInput = findViewById(R.id.searchInput)
        backButton = findViewById(R.id.backButton)
        syncButton = findViewById(R.id.syncButton)
        subjectTitle = findViewById(R.id.subjectTitle)
        gradeSectionText = findViewById(R.id.gradeSectionText)

        subjectsRecyclerView.layoutManager = LinearLayoutManager(this)
        studentsRecyclerView.layoutManager = LinearLayoutManager(this)

        backButton.setOnClickListener {
            activeSubject = null
            showSubjectsList()
        }

        syncButton.setOnClickListener { syncData() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchTerm = s.toString()
                filterStudents()
            }
        })
    }

    // ==================== تحميل المواد ====================

    private fun loadSubjects() {
        val teacherInfoStr = prefs.getString("teacher_info", null)
        if (teacherInfoStr != null) {
            val teacherInfo = JSONObject(teacherInfoStr)
            val classesArray = teacherInfo.optJSONArray("classes")
            if (classesArray != null) {
                for (i in 0 until classesArray.length()) {
                    subjectsList.add(classesArray.getJSONObject(i))
                }
            }
        }
        showSubjectsList()
    }

    // ==================== عرض قائمة المواد ====================

    private fun showSubjectsList() {
        subjectTitle.visibility = View.GONE
        gradeSectionText.visibility = View.GONE
        backButton.visibility = View.GONE
        syncButton.visibility = View.GONE
        searchInput.visibility = View.GONE
        studentsRecyclerView.visibility = View.GONE

        subjectsRecyclerView.visibility = View.VISIBLE
        subjectsRecyclerView.adapter = SubjectsAdapter(subjectsList) { subject ->
            activeSubject = subject
            showStudentsList()
            syncData()
        }
    }

    // ==================== عرض قائمة الطلاب ====================

    private fun showStudentsList() {
        subjectsRecyclerView.visibility = View.GONE
        searchInput.visibility = View.VISIBLE
        studentsRecyclerView.visibility = View.VISIBLE
        subjectTitle.visibility = View.VISIBLE
        gradeSectionText.visibility = View.VISIBLE
        backButton.visibility = View.VISIBLE
        syncButton.visibility = View.VISIBLE

        activeSubject?.let {
            subjectTitle.text = it.optString("subject", "المادة")
            gradeSectionText.text = "${it.optString("grade")} / ${it.optString("section")}"
        }
    }

    // ==================== مزامنة بيانات الطلاب ====================

    private fun syncData() {
        if (activeSubject == null) return

        isLoading = true
        syncButton.isEnabled = false

        // محاكاة جلب البيانات (سيتم ربطها بالسيرفر لاحقاً)
        val mockStudents = listOf(
            StudentItem("1", "أحمد محمد", 85, mutableListOf()),
            StudentItem("2", "فاطمة علي", 92, mutableListOf()),
            StudentItem("3", "يوسف حسن", 78, mutableListOf()),
            StudentItem("4", "سارة خالد", 96, mutableListOf())
        )

        studentsList.clear()
        studentsList.addAll(mockStudents)

        // تحميل سجل الحضور من التخزين المحلي
        val storageKey = "attendance_${activeSubject!!.optString("id")}_$TEACHER_ID"
        val savedHistory = prefs.getString(storageKey, "{}")
        val historyMap = JSONObject(savedHistory)

        studentsList.forEach { student ->
            val historyArray = historyMap.optJSONArray(student.id)
            if (historyArray != null) {
                student.attendanceHistory.clear()
                for (i in 0 until historyArray.length()) {
                    val record = historyArray.getJSONObject(i)
                    student.attendanceHistory.add(
                        AttendanceRecord(
                            record.optString("date"),
                            record.optString("status")
                        )
                    )
                }
            }
        }

        studentsRecyclerView.adapter = StudentsAdapter(studentsList) { student, status ->
            markAttendance(student, status)
        }

        Toast.makeText(this, "تم جلب ${studentsList.size} طالب", Toast.LENGTH_SHORT).show()

        isLoading = false
        syncButton.isEnabled = true
    }

    // ==================== تسجيل الحضور والغياب ====================

    private fun markAttendance(student: StudentItem, status: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        // إزالة تسجيل اليوم إن وجد
        val filtered = student.attendanceHistory.filter { it.date != today }.toMutableList()
        filtered.add(AttendanceRecord(today, status))

        student.attendanceHistory.clear()
        student.attendanceHistory.addAll(filtered)

        // تحديث النقاط
        if (status == "present") {
            student.points += 5
        }

        // حفظ في التخزين المحلي
        val storageKey = "attendance_${activeSubject!!.optString("id")}_$TEACHER_ID"
        val historyMap = JSONObject()

        studentsList.forEach { s ->
            val historyArray = JSONArray()
            s.attendanceHistory.forEach { record ->
                val recordObj = JSONObject()
                recordObj.put("date", record.date)
                recordObj.put("status", record.status)
                historyArray.put(recordObj)
            }
            historyMap.put(s.id, historyArray)
        }

        prefs.edit().putString(storageKey, historyMap.toString()).apply()
        studentsRecyclerView.adapter?.notifyDataSetChanged()

        val message = if (status == "present") "✅ تم تسجيل الحضور" else "❌ تم تسجيل الغياب"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ==================== تصفية الطلاب ====================

    private fun filterStudents() {
        val filtered = if (searchTerm.isEmpty()) {
            studentsList
        } else {
            studentsList.filter { it.name.contains(searchTerm, ignoreCase = true) }
        }
        (studentsRecyclerView.adapter as? StudentsAdapter)?.updateList(filtered)
    }

    // ==================== Adapter للمواد ====================

    inner class SubjectsAdapter(
        private val subjects: List<JSONObject>,
        private val onItemClick: (JSONObject) -> Unit
    ) : RecyclerView.Adapter<SubjectsAdapter.SubjectViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subject, parent, false)
            return SubjectViewHolder(view)
        }

        override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
            val subject = subjects[position]
            holder.subjectText.text = subject.optString("subject", "مادة")
            holder.gradeText.text = "${subject.optString("grade")} - ${subject.optString("section")}"
            holder.itemView.setOnClickListener { onItemClick(subject) }
        }

        override fun getItemCount(): Int = subjects.size

        inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val subjectText: TextView = itemView.findViewById(R.id.subjectText)
            val gradeText: TextView = itemView.findViewById(R.id.gradeText)
        }
    }

    // ==================== Adapter للطلاب ====================

    inner class StudentsAdapter(
        private var students: List<StudentItem>,
        private val onAttendanceClick: (StudentItem, String) -> Unit
    ) : RecyclerView.Adapter<StudentsAdapter.StudentViewHolder>() {

        fun updateList(newList: List<StudentItem>) {
            students = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student, parent, false)
            return StudentViewHolder(view)
        }

        override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
            val student = students[position]
            holder.nameText.text = student.name
            holder.pointsText.text = "${student.points} نقطة"

            holder.presentButton.setOnClickListener { onAttendanceClick(student, "present") }
            holder.absentButton.setOnClickListener { onAttendanceClick(student, "absent") }

            // عرض آخر 4 تسجيلات حضور
            holder.attendanceContainer.removeAllViews()
            val lastRecords = student.attendanceHistory.takeLast(4)
            lastRecords.forEach { record ->
                val chip = TextView(this@StatsActivity).apply {
                    text = "${record.date.substring(5)} • ${if (record.status == "present") "حاضر" else "غائب"}"
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(this@StatsActivity, 
                        if (record.status == "present") R.color.emerald_600 else R.color.rose_600))
                    setBackgroundResource(if (record.status == "present") R.drawable.chip_present else R.drawable.chip_absent)
                    setPadding(20, 8, 20, 8)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = 8 }
                }
                holder.attendanceContainer.addView(chip)
            }
        }

        override fun getItemCount(): Int = students.size

        inner class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameText: TextView = itemView.findViewById(R.id.studentName)
            val pointsText: TextView = itemView.findViewById(R.id.studentPoints)
            val presentButton: Button = itemView.findViewById(R.id.presentButton)
            val absentButton: Button = itemView.findViewById(R.id.absentButton)
            val attendanceContainer: LinearLayout = itemView.findViewById(R.id.attendanceContainer)
        }
    }
}
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncActivity : AppCompatActivity() {

    // ==================== المتغيرات ====================

    private lateinit var prefs: SharedPreferences
    private lateinit var syncButton: Button
    private lateinit var searchInput: EditText
    private lateinit var subjectsRecyclerView: RecyclerView
    private lateinit var studentsRecyclerView: RecyclerView
    private lateinit var backButton: Button
    private lateinit var headerTitle: TextView
    private lateinit var headerSubtitle: TextView

    private var subjectsList = mutableListOf<JSONObject>()
    private var activeSubject: JSONObject? = null
    private var submissionsList = mutableListOf<SubmissionItem>()
    private var filteredStudents = mutableListOf<SubmissionItem>()
    private var viewingStudent: SubmissionItem? = null
    private var selectedSubmission: SubmissionItem? = null
    private var zoomedImageUrl: String? = null
    private var isLoading = false
    private var searchTerm = ""

    data class SubmissionItem(
        val id: String,
        val name: String,
        val grade: String,
        val section: String,
        val subjectName: String,
        val lessonTitle: String,
        val points: Int,
        val homeworkImage: String?,
        val quizResults: List<QuizResult>,
        val timestamp: String
    )

    data class QuizResult(
        val question: String,
        val studentAnswer: String
    )

    companion object {
        private const val TEACHER_ID = "TCH-001"
    }

    // ==================== دورة حياة النشاط ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync)

        prefs = getSharedPreferences("teacher_app", Context.MODE_PRIVATE)

        initViews()
        loadLocalData()
        startAutoSync()
    }

    // ==================== تهيئة العناصر ====================

    private fun initViews() {
        syncButton = findViewById(R.id.syncButton)
        searchInput = findViewById(R.id.searchInput)
        subjectsRecyclerView = findViewById(R.id.subjectsRecyclerView)
        studentsRecyclerView = findViewById(R.id.studentsRecyclerView)
        backButton = findViewById(R.id.backButton)
        headerTitle = findViewById(R.id.headerTitle)
        headerSubtitle = findViewById(R.id.headerSubtitle)

        subjectsRecyclerView.layoutManager = LinearLayoutManager(this)
        studentsRecyclerView.layoutManager = LinearLayoutManager(this)

        syncButton.setOnClickListener { syncData(false) }

        backButton.setOnClickListener {
            activeSubject = null
            showSubjectsList()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchTerm = s.toString()
                filterStudents()
            }
        })
    }

    // ==================== تحميل البيانات المحلية ====================

    private fun loadLocalData() {
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

        val savedSubmissions = prefs.getString("submissions_$TEACHER_ID", "[]")
        val submissionsArray = JSONArray(savedSubmissions)
        submissionsList.clear()
        for (i in 0 until submissionsArray.length()) {
            val obj = submissionsArray.getJSONObject(i)
            submissionsList.add(parseSubmission(obj))
        }

        showSubjectsList()
    }

    // ==================== تحويل JSON إلى SubmissionItem ====================

    private fun parseSubmission(obj: JSONObject): SubmissionItem {
        val quizArray = obj.optJSONArray("quizResults")
        val quizResults = mutableListOf<QuizResult>()
        if (quizArray != null) {
            for (i in 0 until quizArray.length()) {
                val q = quizArray.getJSONObject(i)
                quizResults.add(QuizResult(
                    q.optString("question"),
                    q.optString("studentAnswer")
                ))
            }
        }
        return SubmissionItem(
            id = obj.optString("id"),
            name = obj.optString("name", "طالب بدون اسم"),
            grade = obj.optString("grade"),
            section = obj.optString("section"),
            subjectName = obj.optString("subjectName"),
            lessonTitle = obj.optString("lessonTitle", "نشاط عام"),
            points = obj.optInt("points", 0),
            homeworkImage = obj.optString("homeworkImage").takeIf { it.isNotEmpty() },
            quizResults = quizResults,
            timestamp = obj.optString("timestamp", System.currentTimeMillis().toString())
        )
    }
// ==================== المزامنة مع السيرفر ====================

private fun syncData(silent: Boolean) {
    if (!silent) {
        isLoading = true
        syncButton.isEnabled = false
    }

    // محاكاة جلب البيانات (سيتم ربطها بالسيرفر لاحقاً)
    val mockSubmissions = listOf(
        SubmissionItem("1", "أحمد محمد", "الأول الابتدائي", "أ", "الرياضيات", "جمع الكسور", 85, null, listOf(), System.currentTimeMillis().toString()),
        SubmissionItem("2", "فاطمة علي", "الأول الابتدائي", "أ", "العلوم", "الجهاز الهضمي", 92, null, listOf(), System.currentTimeMillis().toString()),
        SubmissionItem("3", "يوسف حسن", "الأول الابتدائي", "أ", "اللغة العربية", "قواعد النحو", 78, null, listOf(), System.currentTimeMillis().toString())
    )

    submissionsList.clear()
    submissionsList.addAll(mockSubmissions)

    // حفظ في SharedPreferences
    val submissionsArray = JSONArray()
    submissionsList.forEach { sub ->
        val obj = JSONObject()
        obj.put("id", sub.id)
        obj.put("name", sub.name)
        obj.put("grade", sub.grade)
        obj.put("section", sub.section)
        obj.put("subjectName", sub.subjectName)
        obj.put("lessonTitle", sub.lessonTitle)
        obj.put("points", sub.points)
        obj.put("timestamp", sub.timestamp)
        submissionsArray.put(obj)
    }
    prefs.edit().putString("submissions_$TEACHER_ID", submissionsArray.toString()).apply()

    if (!silent) {
        Toast.makeText(this, "✅ تم تحديث ${mockSubmissions.size} سجل جديد", Toast.LENGTH_SHORT).show()
    }

    if (activeSubject != null) {
        filterStudentsBySubject()
    }

    if (!silent) {
        isLoading = false
        syncButton.isEnabled = true
    }
}

private fun startAutoSync() {
    syncData(true)
    // سيتم إضافة定时器 لاحقاً
}

// ==================== عرض قائمة المواد ====================

private fun showSubjectsList() {
    headerTitle.text = "المزامنة"
    headerSubtitle.visibility = View.GONE
    backButton.visibility = View.GONE
    searchInput.visibility = View.GONE
    studentsRecyclerView.visibility = View.GONE
    subjectsRecyclerView.visibility = View.VISIBLE

    subjectsRecyclerView.adapter = SubjectsAdapter(subjectsList) { subject ->
        activeSubject = subject
        filterStudentsBySubject()
        showStudentsList()
    }
}

// ==================== عرض قائمة الطلاب ====================

private fun showStudentsList() {
    headerTitle.text = activeSubject?.optString("subject", "المادة") ?: "المادة"
    headerSubtitle.text = "${activeSubject?.optString("grade")} - ${activeSubject?.optString("section")}"
    headerSubtitle.visibility = View.VISIBLE
    backButton.visibility = View.VISIBLE
    searchInput.visibility = View.VISIBLE
    subjectsRecyclerView.visibility = View.GONE
    studentsRecyclerView.visibility = View.VISIBLE

    studentsRecyclerView.adapter = StudentsAdapter(filteredStudents) { student ->
        showStudentDetails(student)
    }
}

// ==================== تصفية الطلاب حسب المادة ====================

private fun filterStudentsBySubject() {
    val subject = activeSubject ?: return
    val subjectGrade = subject.optString("grade")
    val subjectSection = subject.optString("section")
    val subjectName = subject.optString("subject")

    val filtered = submissionsList.filter { sub ->
        sub.grade.equals(subjectGrade, ignoreCase = true) &&
        sub.section.equals(subjectSection, ignoreCase = true) &&
        (sub.subjectName.equals(subjectName, ignoreCase = true) || sub.subjectName.isEmpty())
    }.distinctBy { it.id }

    filteredStudents.clear()
    filteredStudents.addAll(filtered)
    filterStudents()
}

// ==================== تصفية الطلاب حسب البحث ====================

private fun filterStudents() {
    val filtered = if (searchTerm.isEmpty()) {
        filteredStudents
    } else {
        filteredStudents.filter { it.name.contains(searchTerm, ignoreCase = true) }
    }
    (studentsRecyclerView.adapter as? StudentsAdapter)?.updateList(filtered)
}

// ==================== عرض تفاصيل الطالب ====================

private fun showStudentDetails(student: SubmissionItem) {
    viewingStudent = student
    val dialog = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.bottom_sheet_student_details, null)

    val nameText = view.findViewById<TextView>(R.id.studentName)
    val gradeText = view.findViewById<TextView>(R.id.studentGrade)
    val pointsText = view.findViewById<TextView>(R.id.studentPoints)
    val historyRecyclerView = view.findViewById<RecyclerView>(R.id.historyRecyclerView)

    nameText.text = student.name
    gradeText.text = "${student.grade} • ${student.section}"
    pointsText.text = "${student.points} نقطة"

    val historyList = submissionsList.filter { it.id == student.id }
    historyRecyclerView.layoutManager = LinearLayoutManager(this)
    historyRecyclerView.adapter = HistoryAdapter(historyList) { submission ->
        dialog.dismiss()
        showSubmissionDetails(submission)
    }

    dialog.setContentView(view)
    dialog.show()
}    // ==================== عرض تفاصيل الواجب ====================

    private fun showSubmissionDetails(submission: SubmissionItem) {
        selectedSubmission = submission
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_submission_details, null)

        val nameText = view.findViewById<TextView>(R.id.submissionName)
        val lessonText = view.findViewById<TextView>(R.id.lessonTitle)
        val pointsText = view.findViewById<TextView>(R.id.points)
        val homeworkImage = view.findViewById<ImageView>(R.id.homeworkImage)
        val quizContainer = view.findViewById<LinearLayout>(R.id.quizContainer)

        nameText.text = submission.name
        lessonText.text = submission.lessonTitle
        pointsText.text = "${submission.points} نقطة"

        if (submission.homeworkImage != null) {
            Glide.with(this).load(submission.homeworkImage).into(homeworkImage)
            homeworkImage.visibility = View.VISIBLE
            homeworkImage.setOnClickListener {
                showZoomedImage(submission.homeworkImage!!)
            }
        }

        quizContainer.removeAllViews()
        submission.quizResults.forEachIndexed { index, q ->
            val itemView = layoutInflater.inflate(R.layout.item_quiz_result, null)
            val questionText = itemView.findViewById<TextView>(R.id.questionText)
            val answerText = itemView.findViewById<TextView>(R.id.answerText)

            questionText.text = "${index + 1}. ${q.question}"
            answerText.text = q.studentAnswer

            quizContainer.addView(itemView)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showZoomedImage(imageUrl: String) {
        val dialog = AlertDialog.Builder(this)
            .setView(layoutInflater.inflate(R.layout.dialog_zoomed_image, null))
            .create()
        val imageView = dialog.findViewById<ImageView>(R.id.zoomedImage)
        Glide.with(this).load(imageUrl).into(imageView!!)
        imageView.setOnClickListener { dialog.dismiss() }
        dialog.show()
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
            holder.gradeText.text = "${subject.optString("grade")} • شعبة ${subject.optString("section")}"
            
            val count = submissionsList.count { it.grade == subject.optString("grade") && it.section == subject.optString("section") }
            holder.countText.text = if (count > 0) "$count سجلات مكتشفة" else ""
            holder.countText.visibility = if (count > 0) View.VISIBLE else View.GONE
            
            holder.itemView.setOnClickListener { onItemClick(subject) }
        }

        override fun getItemCount(): Int = subjects.size

        inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val subjectText: TextView = itemView.findViewById(R.id.subjectText)
            val gradeText: TextView = itemView.findViewById(R.id.gradeText)
            val countText: TextView = itemView.findViewById(R.id.countText)
        }
    }

    // ==================== Adapter للطلاب ====================

    inner class StudentsAdapter(
        private var students: List<SubmissionItem>,
        private val onItemClick: (SubmissionItem) -> Unit
    ) : RecyclerView.Adapter<StudentsAdapter.StudentViewHolder>() {

        fun updateList(newList: List<SubmissionItem>) {
            students = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student_sync, parent, false)
            return StudentViewHolder(view)
        }

        override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
            val student = students[position]
            holder.nameText.text = student.name
            holder.lessonText.text = student.lessonTitle
            holder.initialText.text = student.name.take(1)
            
            if (student.homeworkImage != null) {
                Glide.with(this@SyncActivity).load(student.homeworkImage).into(holder.homeworkImage)
                holder.homeworkImage.visibility = View.VISIBLE
            } else {
                holder.homeworkImage.visibility = View.GONE
            }
            
            holder.itemView.setOnClickListener { onItemClick(student) }
        }

        override fun getItemCount(): Int = students.size

        inner class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val initialText: TextView = itemView.findViewById(R.id.studentInitial)
            val nameText: TextView = itemView.findViewById(R.id.studentName)
            val lessonText: TextView = itemView.findViewById(R.id.lessonTitle)
            val homeworkImage: ImageView = itemView.findViewById(R.id.homeworkImage)
        }
    }

    // ==================== Adapter للسجل التاريخي ====================

    inner class HistoryAdapter(
        private val history: List<SubmissionItem>,
        private val onItemClick: (SubmissionItem) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
            return HistoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val item = history[position]
            holder.lessonText.text = item.lessonTitle
            holder.dateText.text = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(item.timestamp.toLong()))
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = history.size

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val lessonText: TextView = itemView.findViewById(R.id.lessonTitle)
            val dateText: TextView = itemView.findViewById(R.id.dateText)
        }
    }
}
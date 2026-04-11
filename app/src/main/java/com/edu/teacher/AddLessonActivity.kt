package com.edu.teacher

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AddLessonActivity : AppCompatActivity() {

    // ==================== المتغيرات ====================

    private lateinit var prefs: SharedPreferences
    private lateinit var backButton: Button
    private lateinit var saveButton: Button
    private lateinit var classSpinner: Spinner
    private lateinit var unitSpinner: Spinner
    private lateinit var titleInput: TextInputEditText
    private lateinit var contentInput: EditText
    private lateinit var questionsRecyclerView: RecyclerView
    private lateinit var addChoiceButton: Button
    private lateinit var addBooleanButton: Button
    private lateinit var addEssayButton: Button

    private var teacherInfo = JSONObject()
    private var classesList = mutableListOf<JSONObject>()
    private var selectedClassId = ""
    private var selectedUnit = "الوحدة الأولى"
    private var questionsList = mutableListOf<QuestionItem>()
    private var isLoading = false

    private val allUnits = listOf(
        "الوحدة الأولى", "الوحدة الثانية", "الوحدة الثالثة",
        "الوحدة الرابعة", "الوحدة الخامسة", "الوحدة السادسة",
        "الوحدة السابعة", "الوحدة الثامنة", "الوحدة التاسعة"
    )

    data class QuestionItem(
        var id: String,
        var type: String,
        var text: String,
        var options: MutableList<String>,
        var answer: String
    )

    // ==================== دورة حياة النشاط ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_lesson)

        prefs = getSharedPreferences("teacher_app", Context.MODE_PRIVATE)

        initViews()
        loadTeacherData()
        setupSpinners()
        setupButtons()
        setupRecyclerView()
    }

    // ==================== تهيئة العناصر ====================

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        saveButton = findViewById(R.id.saveButton)
        classSpinner = findViewById(R.id.classSpinner)
        unitSpinner = findViewById(R.id.unitSpinner)
        titleInput = findViewById(R.id.titleInput)
        contentInput = findViewById(R.id.contentInput)
        questionsRecyclerView = findViewById(R.id.questionsRecyclerView)
        addChoiceButton = findViewById(R.id.addChoiceButton)
        addBooleanButton = findViewById(R.id.addBooleanButton)
        addEssayButton = findViewById(R.id.addEssayButton)

        backButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { saveLesson() }
    }

    // ==================== تحميل بيانات المعلم ====================

    private fun loadTeacherData() {
        val teacherInfoStr = prefs.getString("teacher_info", null)
        if (teacherInfoStr != null) {
            teacherInfo = JSONObject(teacherInfoStr)
            val classesArray = teacherInfo.optJSONArray("classes")
            if (classesArray != null) {
                for (i in 0 until classesArray.length()) {
                    classesList.add(classesArray.getJSONObject(i))
                }
            }
            if (classesList.isNotEmpty()) {
                selectedClassId = classesList[0].optString("id")
            }
        }
    }

    // ==================== إعداد القوائم المنسدلة ====================

    private fun setupSpinners() {
        val classNames = classesList.map {
            "${it.optString("subject")} - ${it.optString("grade")} (${it.optString("section")})"
        }
        val classAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, classNames)
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        classSpinner.adapter = classAdapter

        classSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedClassId = classesList[position].optString("id")
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, allUnits)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        unitSpinner.adapter = unitAdapter

        unitSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedUnit = allUnits[position]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    // ==================== إعداد الأزرار ====================

    private fun setupButtons() {
        addChoiceButton.setOnClickListener { addQuestion("choice") }
        addBooleanButton.setOnClickListener { addQuestion("boolean") }
        addEssayButton.setOnClickListener { addQuestion("essay") }
    }

    // ==================== إضافة سؤال جديد ====================

    private fun addQuestion(type: String) {
        val newQuestion = QuestionItem(
            id = UUID.randomUUID().toString(),
            type = type,
            text = "",
            options = when (type) {
                "choice" -> mutableListOf("", "", "")
                "boolean" -> mutableListOf("صح", "خطأ")
                else -> mutableListOf()
            },
            answer = when (type) {
                "boolean" -> "صح"
                else -> "1"
            }
        )
        questionsList.add(newQuestion)
        questionsRecyclerView.adapter?.notifyItemInserted(questionsList.size - 1)
    }

    // ==================== إعداد RecyclerView ====================

    private fun setupRecyclerView() {
        questionsRecyclerView.layoutManager = LinearLayoutManager(this)
        questionsRecyclerView.adapter = QuestionsAdapter(questionsList) { position ->
            questionsList.removeAt(position)
            questionsRecyclerView.adapter?.notifyItemRemoved(position)
        }
    }

    // ==================== حفظ الدرس ====================

    private fun saveLesson() {
        val title = titleInput.text.toString().trim()

        if (selectedClassId.isEmpty()) {
            Toast.makeText(this, "⚠️ اختر الفصل الدراسي", Toast.LENGTH_SHORT).show()
            return
        }

        if (title.isEmpty()) {
            Toast.makeText(this, "⚠️ اكتب عنوان الدرس", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        saveButton.isEnabled = false

        val selectedClass = classesList.find { it.optString("id") == selectedClassId }
        val lessonId = "lesson_${System.currentTimeMillis()}"

        val questionsArray = JSONArray()

        questionsList.forEach { q ->
            val qObj = JSONObject()
            qObj.put("id", q.id)
            qObj.put("type", q.type)
            qObj.put("text", q.text)
            qObj.put("options", JSONArray(q.options))
            qObj.put("answer", q.answer)
            questionsArray.put(qObj)
        }

        val newLesson = JSONObject().apply {
            put("id", lessonId)
            put("teacherId", teacherInfo.optString("id"))
            put("classId", selectedClassId)
            put("subjectTitle", selectedClass?.optString("subject") ?: "مادة دراسية")
            put("grade", selectedClass?.optString("grade") ?: "")
            put("section", selectedClass?.optString("section") ?: "أ")
            put("unit", selectedUnit)
            put("title", title)
            put("content", contentInput.text.toString())
            put("questions", questionsArray)
            put("createdAt", System.currentTimeMillis())
        }

        val storageKey = "lessons_${teacherInfo.optString("id")}"
        val existingStr = prefs.getString(storageKey, "[]")
        val existingArray = JSONArray(existingStr)
        existingArray.put(0, newLesson)
        prefs.edit().putString(storageKey, existingArray.toString()).apply()

        Toast.makeText(this, "✅ تم حفظ الدرس بنجاح!", Toast.LENGTH_LONG).show()

        isLoading = false
        finish()
    }

    // ==================== Adapter للأسئلة ====================

    inner class QuestionsAdapter(
        private val questions: MutableList<QuestionItem>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<QuestionsAdapter.QuestionViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_question, parent, false)
            return QuestionViewHolder(view)
        }

        override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
            val q = questions[position]
            holder.questionNumber.text = "سؤال ${position + 1}"
            holder.questionType.text = when (q.type) {
                "choice" -> "اختيار من متعدد"
                "boolean" -> "صح/خطأ"
                else -> "مقالي"
            }
            holder.questionText.setText(q.text)

            holder.questionText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    q.text = s.toString()
                }
            })

            holder.deleteButton.setOnClickListener { onDelete(position) }
        }

        override fun getItemCount(): Int = questions.size

        inner class QuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val questionNumber: TextView = itemView.findViewById(R.id.questionNumber)
            val questionType: TextView = itemView.findViewById(R.id.questionType)
            val questionText: EditText = itemView.findViewById(R.id.questionText)
            val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)
        }
    }
}
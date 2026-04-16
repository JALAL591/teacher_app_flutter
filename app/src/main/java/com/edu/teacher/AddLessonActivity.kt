package com.edu.teacher

import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.databinding.ActivityAddLessonBinding
import com.edu.teacher.databinding.ItemQuestionBinding
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AddLessonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddLessonBinding

    private var teacherId: String = ""
    private var classesList = mutableListOf<JSONObject>()
    private var selectedClassId = ""
    private var selectedUnit = ""
    private var selectedPdfPath: String = ""
    private var questionsList = mutableListOf<QuestionItem>()
    
    private var selectedSubjectId: String = ""
    private var selectedSubjectTitle: String = ""
    private var selectedGrade: String = ""
    private var selectedSection: String = ""

    data class QuestionItem(
        var id: String,
        var type: String,
        var text: String,
        var options: MutableList<String>,
        var correctAnswer: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddLessonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        teacherId = DataManager.getTeacherId(this) ?: run {
            Toast.makeText(this, R.string.error_teacher_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        selectedUnit = resources.getStringArray(R.array.unit_names)[0]
        initViews()
        loadClasses()
        setupSpinners()
        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun initViews() {
        try {
            binding.backButton.setOnClickListener { finish() }
            
            // Question type buttons
            binding.addChoiceButton.setOnClickListener { addQuestion("choice") }
            binding.addBooleanButton.setOnClickListener { addQuestion("boolean") }
            binding.addEssayButton.setOnClickListener { addQuestion("essay") }
            
            binding.selectPdfBtn.setOnClickListener { pickPdfLauncher.launch("application/pdf") }
            binding.saveButton.setOnClickListener { saveLesson() }

            // Telegram-style bottom navigation
            binding.bottomNav.setOnItemSelectedListener { position ->
                when (position) {
                    0 -> startActivity(android.content.Intent(this, DashboardActivity::class.java))
                    1 -> startActivity(android.content.Intent(this, StatsActivity::class.java))
                    2 -> { } // Add Lesson - current page
                    3 -> startActivity(android.content.Intent(this, StudentsActivity::class.java))
                    4 -> startActivity(android.content.Intent(this, SettingsActivity::class.java))
                }
            }
            binding.bottomNav.setActiveTab(2)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            copyPdfToAppStorage(uri)
        }
    }

    private fun copyPdfToAppStorage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val fileName = "lesson_pdf_${System.currentTimeMillis()}.pdf"
                val file = File(filesDir, fileName)
                file.createNewFile()
                inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                selectedPdfPath = file.absolutePath
                binding.pdfStatusText.text = getString(R.string.toast_pdf_attached)
                binding.pdfStatusText.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.error_pdf_copy, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadClasses() {
        val subjects = DataManager.getSubjects(this, teacherId)
        classesList.clear()
        subjects.forEach { subject ->
            val subjectId = subject.optString("id")
            val subjectName = subject.optString("name", getString(R.string.default_subject))
            val classes = DataManager.getClasses(this, teacherId, subjectId)
            classes.forEach { cls ->
                cls.put("subjectName", subjectName as Any)
                cls.put("subjectId", subjectId as Any)
                classesList.add(cls)
            }
        }

        if (classesList.isEmpty()) {
            Toast.makeText(this, R.string.warning_add_class_first, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupSpinners() {
        val classNames = classesList.map {
            "${it.optString("subjectName", getString(R.string.default_subject))} - ${it.optString("name", getString(R.string.default_class))}"
        }
        val classAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, classNames)
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.classSpinner.adapter = classAdapter

        if (classesList.isNotEmpty()) {
            val firstClass = classesList[0]
            selectedClassId = firstClass.optString("id")
            selectedSubjectId = firstClass.optString("subjectId")
            selectedSubjectTitle = firstClass.optString("subjectName", getString(R.string.default_subject))
            selectedGrade = firstClass.optString("grade", "")
            selectedSection = firstClass.optString("section", "")
        }

        binding.classSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                if (position < classesList.size) {
                    val selectedClass = classesList[position]
                    selectedClassId = selectedClass.optString("id")
                    selectedSubjectId = selectedClass.optString("subjectId")
                    selectedSubjectTitle = selectedClass.optString("subjectName", getString(R.string.default_subject))
                    selectedGrade = selectedClass.optString("grade", "")
                    selectedSection = selectedClass.optString("section", "")
                }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resources.getStringArray(R.array.unit_names))
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.unitSpinner.adapter = unitAdapter
        binding.unitSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                selectedUnit = resources.getStringArray(R.array.unit_names)[position]
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }
    }

    private fun addQuestion(type: String) {
        val newQuestion = QuestionItem(
            id = UUID.randomUUID().toString(),
            type = type,
            text = "",
            options = if (type == "choice") mutableListOf("", "") else mutableListOf(
                getString(R.string.answer_true),
                getString(R.string.answer_false)
            ),
            correctAnswer = if (type == "choice") "1" else getString(R.string.answer_true)
        )
        questionsList.add(newQuestion)
        binding.questionsRecyclerView.adapter?.notifyItemInserted(questionsList.size - 1)
    }

    private fun setupRecyclerView() {
        binding.questionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.questionsRecyclerView.adapter = QuestionsAdapter(questionsList)

        // إضافة فاصل بين الأسئلة
        val divider = object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                outRect.bottom = 1
            }
        }
        binding.questionsRecyclerView.addItemDecoration(divider)
    }

    private fun saveLesson() {
        val title = binding.titleInput.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(this, R.string.warning_enter_lesson_title, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedClassId.isEmpty()) {
            Toast.makeText(this, R.string.warning_select_class, Toast.LENGTH_SHORT).show()
            return
        }

        val questionsArray = JSONArray()
        questionsList.forEach { q ->
            val qObj = JSONObject()
            qObj.put("id", q.id as Any)
            qObj.put("type", q.type as Any)
            qObj.put("text", q.text as Any)
            qObj.put("options", JSONArray(q.options) as Any)
            qObj.put("answer", q.correctAnswer as Any)
            questionsArray.put(qObj)
        }

        val lessonObj = JSONObject().apply {
            put("id", DataManager.generateLessonId() as Any)
            put("teacherId", teacherId as Any)
            put("classId", selectedClassId as Any)
            put("subjectId", selectedSubjectId as Any)
            put("subjectTitle", selectedSubjectTitle as Any)
            put("grade", selectedGrade as Any)
            put("section", selectedSection as Any)
            put("unit", selectedUnit as Any)
            put("title", title as Any)
            put("content", binding.contentInput.text.toString() as Any)
            put("pdfUri", selectedPdfPath as Any)
            put("questions", questionsArray as Any)
            put("createdAt", DataManager.getSyncTimestamp() as Any)
            put("isPublished", false as Any)
        }

        DataManager.addLesson(this, teacherId, selectedClassId, lessonObj)
        Toast.makeText(this, R.string.toast_lesson_published, Toast.LENGTH_SHORT).show()
        finish()
    }

    inner class QuestionsAdapter(
        private val questions: MutableList<QuestionItem>
    ) : RecyclerView.Adapter<QuestionsAdapter.QuestionViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
            val binding = ItemQuestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return QuestionViewHolder(binding)
        }

        override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
            val q = questions[position]
            holder.binding.questionNumber.text = getString(R.string.question_label, position + 1)
            holder.binding.questionType.text = when (q.type) {
                "choice" -> getString(R.string.question_type_mcq)
                "boolean" -> getString(R.string.question_type_tf)
                else -> getString(R.string.question_type_essay)
            }
            holder.binding.questionText.setText(q.text)

            holder.binding.questionText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { q.text = s.toString() }
            })

            if (q.type == "choice") {
                holder.binding.mcqOptionsArea.visibility = View.VISIBLE
                holder.binding.tfArea.visibility = View.GONE
                val adapter = ArrayAdapter(
                    holder.binding.root.context,
                    android.R.layout.simple_spinner_item,
                    listOf(getString(R.string.option_1), getString(R.string.option_2))
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                holder.binding.correctOptSpinner.adapter = adapter
                holder.binding.correctOptSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                        q.correctAnswer = (pos + 1).toString()
                    }
                    override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
                }
            } else if (q.type == "boolean") {
                holder.binding.mcqOptionsArea.visibility = View.GONE
                holder.binding.tfArea.visibility = View.VISIBLE
                holder.binding.tfGroup.setOnCheckedChangeListener { _, checkedId ->
                    q.correctAnswer = if (checkedId == R.id.radioTrue) {
                        getString(R.string.answer_true)
                    } else {
                        getString(R.string.answer_false)
                    }
                }
            } else {
                holder.binding.mcqOptionsArea.visibility = View.GONE
                holder.binding.tfArea.visibility = View.GONE
            }

            holder.binding.deleteButton.setOnClickListener {
                questions.removeAt(position)
                notifyDataSetChanged()
            }
        }

        override fun getItemCount() = questions.size

        inner class QuestionViewHolder(val binding: ItemQuestionBinding) : RecyclerView.ViewHolder(binding.root)
    }
}

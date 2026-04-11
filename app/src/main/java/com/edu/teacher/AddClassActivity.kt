package com.edu.teacher

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddClassActivity : AppCompatActivity() {

    // ==================== المتغيرات ====================

    private lateinit var prefs: SharedPreferences
    private lateinit var backButton: Button
    private lateinit var pageTitle: TextView
    private lateinit var subjectInput: TextInputEditText
    private lateinit var gradeSpinner: Spinner
    private lateinit var sectionsContainer: LinearLayout
    private lateinit var saveButton: Button

    private val allGrades = listOf(
        "الأول الابتدائي", "الثاني الابتدائي", "الثالث الابتدائي",
        "الرابع الابتدائي", "الخامس الابتدائي", "السادس الابتدائي",
        "الأول المتوسط", "الثاني المتوسط", "الثالث المتوسط",
        "الأول الثانوي", "الثاني الثانوي", "الثالث الثانوي"
    )

    private val sections = listOf("أ", "ب", "ج", "د", "هـ")
    private var selectedGrade = allGrades[0]
    private var selectedSection = sections[0]
    private var isLoading = false

    companion object {
        private const val TEACHER_ID = "TCH-001"
    }

    // ==================== دورة حياة النشاط ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_class)

        prefs = getSharedPreferences("teacher_app", Context.MODE_PRIVATE)

        initViews()
        setupGradeSpinner()
        setupSectionsButtons()
        setupListeners()
    }

    // ==================== تهيئة العناصر ====================

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        pageTitle = findViewById(R.id.pageTitle)
        subjectInput = findViewById(R.id.subjectInput)
        gradeSpinner = findViewById(R.id.gradeSpinner)
        sectionsContainer = findViewById(R.id.sectionsContainer)
        saveButton = findViewById(R.id.saveButton)

        pageTitle.text = "تجهيز فصل جديد"
    }

    // ==================== إعداد قائمة المراحل الدراسية ====================

    private fun setupGradeSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, allGrades)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gradeSpinner.adapter = adapter

        gradeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedGrade = allGrades[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ==================== إعداد أزرار الشعب ====================

    private fun setupSectionsButtons() {
        sectionsContainer.removeAllViews()
        
        sections.forEach { section ->
            val button = MaterialButton(this).apply {
                text = section
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginEnd = 8 }
                setBackgroundColor(ContextCompat.getColor(context, R.color.slate_50))
                setTextColor(ContextCompat.getColor(context, R.color.slate_400))
                cornerRadius = 20
                setPadding(0, 20, 0, 20)
                isAllCaps = false
                
                setOnClickListener {
                    resetSectionButtons()
                    selectedSection = section
                    setBackgroundColor(ContextCompat.getColor(context, R.color.indigo_600))
                    setTextColor(ContextCompat.getColor(context, R.color.white))
                }
            }
            sectionsContainer.addView(button)
        }
    }

    private fun resetSectionButtons() {
        for (i in 0 until sectionsContainer.childCount) {
            val button = sectionsContainer.getChildAt(i) as? MaterialButton
            button?.apply {
                setBackgroundColor(ContextCompat.getColor(this@AddClassActivity, R.color.slate_50))
                setTextColor(ContextCompat.getColor(this@AddClassActivity, R.color.slate_400))
            }
        }
    }

    // ==================== إعداد المستمعين ====================

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { saveClass() }
    }

    // ==================== حفظ الفصل ====================

    private fun saveClass() {
        val subject = subjectInput.text.toString().trim()
        
        if (subject.isEmpty()) {
            Toast.makeText(this, "يرجى كتابة اسم المادة أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        saveButton.isEnabled = false
        saveButton.text = "جاري الحفظ..."

        val newClass = JSONObject().apply {
            put("id", "cls_${System.currentTimeMillis()}")
            put("tid", TEACHER_ID)
            put("subject", subject)
            put("grade", selectedGrade)
            put("section", selectedSection)
            put("createdAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
        }

        // حفظ في SharedPreferences
        val teacherInfoStr = prefs.getString("teacher_info", null)
        if (teacherInfoStr != null) {
            val teacherInfo = JSONObject(teacherInfoStr)
            val classesArray = teacherInfo.optJSONArray("classes") ?: JSONArray()
            classesArray.put(newClass)
            teacherInfo.put("classes", classesArray)
            prefs.edit().putString("teacher_info", teacherInfo.toString()).apply()
        }

        Toast.makeText(this, "✅ تم إنشاء الفصل بنجاح", Toast.LENGTH_LONG).show()

        isLoading = false
        finish()
    }
}
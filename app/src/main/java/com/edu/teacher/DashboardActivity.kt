package com.edu.teacher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.edu.teacher.databinding.ActivityDashboardBinding
import com.edu.teacher.databinding.ItemClassBinding
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var statsStudents: TextView
    private lateinit var statsClasses: TextView
    private lateinit var statsPoints: TextView

    private var classesList = mutableListOf<JSONObject>()
    private var teacherId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        scheduleAutoSync()
    }

    override fun onResume() {
        super.onResume()
        loadData()
        updateThemeToggleIcon()
    }

    private fun updateThemeToggleIcon() {
        try {
            val isDark = TeacherApp.isDarkMode()
            binding.themeToggleButton.setImageResource(
                if (isDark) R.drawable.ic_sun else R.drawable.ic_moon
            )
            binding.themeToggleButton.contentDescription = if (isDark) {
                getString(R.string.settings_label_dark_mode)
            } else {
                "الوضع النهاري"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initViews() {
        statsStudents = binding.statsStudentsNumber
        statsClasses = binding.statsClassesNumber
        statsPoints = binding.statsPointsNumber

        binding.classesRecyclerView.layoutManager = LinearLayoutManager(this)
        
        val itemDecoration = object : RecyclerView.ItemDecoration() {
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
        binding.classesRecyclerView.addItemDecoration(itemDecoration)

        binding.copyButton.setOnClickListener { copyTeacherId() }
        binding.logoutButton.setOnClickListener { logout() }
        binding.themeToggleButton.setOnClickListener {
            val isDark = TeacherApp.isDarkMode()
            val targetDark = !isDark
            
            TeacherApp.applyThemeWithDebounce(targetDark, 400)
            
            binding.root.postDelayed({
                updateThemeToggleIcon()
            }, 100)
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.hotspotButton.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
        binding.addClassButton.setOnClickListener {
            startActivity(Intent(this, AddClassActivity::class.java))
        }
        binding.bottomNav.setOnItemSelectedListener { position ->
            when (position) {
                0 -> { }
                1 -> startActivity(Intent(this, StatsActivity::class.java))
                2 -> startActivity(Intent(this, AddLessonActivity::class.java))
                3 -> startActivity(Intent(this, StudentsActivity::class.java))
                4 -> startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
    }

    private fun loadData() {
        teacherId = DataManager.getTeacherId(this) ?: run {
            Toast.makeText(this, R.string.error_teacher_not_found, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val teacherInfo = DataManager.getTeacherInfo(this) ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val name = teacherInfo.optString("name", getString(R.string.default_teacher))
        val id = teacherInfo.optString("id", getString(R.string.default_id))
        val firstName = if (name.contains(" ")) name.split(" ")[0] else name
        binding.teacherNameText.text = firstName
        binding.teacherIdText.text = id

        classesList.clear()
        val subjects = DataManager.getSubjects(this, teacherId)
        subjects.forEach { subject ->
            val subjectId = subject.optString("id")
            val subjectName = subject.optString("name", getString(R.string.default_subject))
            val classes = DataManager.getClasses(this, teacherId, subjectId)
            classes.forEach { cls ->
                cls.put("subjectName", subjectName as Any)
                classesList.add(cls)
            }
        }

        updateStats()
        binding.classesRecyclerView.adapter = ClassesAdapter(classesList) { classData ->
            val intent = Intent(this, ClassManagerActivity::class.java).apply {
                putExtra("class_id", classData.optString("id"))
                putExtra("class_name", classData.optString("name", getString(R.string.default_class)))
                putExtra("subject_id", classData.optString("subjectId"))
                putExtra("subject_name", classData.optString("subjectName", getString(R.string.default_subject)))
            }
            startActivity(intent)
        }
    }

    private fun updateStats() {
        statsClasses.text = classesList.size.toString()
        statsStudents.text = "0"
        statsPoints.text = "0"
    }

    private fun copyTeacherId() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("teacher_id", binding.teacherIdText.text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.toast_copied_id, Toast.LENGTH_SHORT).show()
    }

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_logout)
            .setMessage(R.string.msg_confirm_logout)
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                DataManager.getPrefs(this).edit().clear().apply()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun getEmoji(subject: String): String {
        val s = subject.lowercase()
        return when {
            s.contains(getString(R.string.default_subject)) -> EmojiConstants.DEFAULT_SUBJECT
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

    private fun scheduleAutoSync() {
        val teacherId = DataManager.getTeacherId(this) ?: return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncData = SyncWorker.createFullSyncData(teacherId)

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            SyncWorker.SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setInputData(syncData)
            .setConstraints(constraints)
            .addTag("auto_sync")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    inner class ClassesAdapter(
        private val classes: List<JSONObject>,
        private val onItemClick: (JSONObject) -> Unit
    ) : RecyclerView.Adapter<ClassesAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemClassBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val classData = classes[position]
            val subjectName = classData.optString("subjectName", classData.optString("subject", getString(R.string.default_subject)))
            val className = classData.optString("name", getString(R.string.default_class))
            val grade = classData.optString("grade", "")
            val section = classData.optString("section", "")
            val studentCount = classData.optInt("studentCount", 0)

            holder.binding.subjectText.text = className
            holder.binding.gradeText.text = if (studentCount > 0) {
                getString(R.string.text_students_count, subjectName, studentCount)
            } else {
                subjectName
            }
            holder.binding.sectionText.text = if (grade.isNotEmpty() && section.isNotEmpty()) "$grade/$section" else section
            holder.binding.emojiText.text = getEmoji(subjectName)

            holder.binding.deleteButton.setOnClickListener {
                AlertDialog.Builder(this@DashboardActivity)
                    .setTitle(R.string.dialog_delete_class)
                    .setMessage(getString(R.string.msg_confirm_delete_class, className))
                    .setPositiveButton(R.string.btn_delete) { _, _ -> deleteClass(position) }
                    .setNegativeButton(R.string.btn_no, null)
                    .show()
            }
            holder.binding.root.setOnClickListener { onItemClick(classData) }
        }

        override fun getItemCount(): Int = classes.size

        inner class ViewHolder(val binding: ItemClassBinding) : RecyclerView.ViewHolder(binding.root)

        private fun deleteClass(position: Int) {
            val classData = classes[position]
            val classId = classData.optString("id")
            val subjectId = classData.optString("subjectId")

            DataManager.removeClass(this@DashboardActivity, teacherId, subjectId, classId)
            classesList.removeAt(position)
            notifyItemRemoved(position)
            updateStats()
            Toast.makeText(this@DashboardActivity, R.string.toast_class_deleted, Toast.LENGTH_SHORT).show()
        }
    }
}

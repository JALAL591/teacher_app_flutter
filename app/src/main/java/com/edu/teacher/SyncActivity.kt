package com.edu.teacher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.databinding.ActivitySyncBinding
import com.edu.teacher.databinding.ItemConnectedStudentBinding
import com.edu.teacher.databinding.ItemSubjectBinding
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class SyncActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyncBinding

    private var subjectsList = mutableListOf<JSONObject>()
    private var activeSubject: JSONObject? = null
    private var connectedStudents = mutableListOf<ConnectedStudentItem>()
    private var teacherId: String = ""
    private var isRadarActive = false
    private var syncTimer: Timer? = null
    private val SYNC_INTERVAL_MS = 15 * 60 * 1000L

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

    data class ConnectedStudentItem(
        val id: String,
        val name: String,
        val grade: String,
        val section: String,
        val connectedAt: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        teacherId = DataManager.getTeacherId(this) ?: run {
            Toast.makeText(this, getString(R.string.error_teacher_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        loadSubjects()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoSync()
    }

    private fun initViews() {
        binding.subjectsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.studentsRecyclerView.layoutManager = LinearLayoutManager(this)

        binding.syncButton.setOnClickListener { syncData(false) }
        binding.backButton.setOnClickListener { activeSubject = null; showSubjectsList() }

        binding.activateRadarButton.setOnClickListener {
            if (isRadarActive) {
                stopRadar()
            } else {
                startRadar()
            }
        }
    }

    private fun loadSubjects() {
        subjectsList.clear()
        subjectsList.addAll(DataManager.getSubjects(this, teacherId))
        showSubjectsList()
    }

    private fun showSubjectsList() {
        binding.headerTitle.text = getString(R.string.title_sync)
        binding.studentsRecyclerView.visibility = View.GONE
        binding.connectedStudentsTitle.visibility = View.GONE
        binding.subjectsRecyclerView.visibility = View.VISIBLE

        binding.subjectsRecyclerView.adapter = SubjectsAdapter(subjectsList) { subject ->
            activeSubject = subject
            showStudentsList()
            syncData(true)
        }
    }

    private fun showStudentsList() {
        binding.headerTitle.text = activeSubject?.optString("name", getString(R.string.default_subject))
            ?: getString(R.string.default_subject)
        binding.subjectsRecyclerView.visibility = View.GONE
        binding.studentsRecyclerView.visibility = View.VISIBLE
        binding.connectedStudentsTitle.visibility = View.VISIBLE

        loadMockConnectedStudents()

        binding.studentsRecyclerView.adapter = ConnectedStudentsAdapter(connectedStudents)
        updateConnectedCount()
    }

    private fun loadMockConnectedStudents() {
        connectedStudents.clear()
        val mockNames = listOf(
            getString(R.string.mock_student_1),
            getString(R.string.mock_student_2),
            getString(R.string.mock_student_3),
            getString(R.string.mock_student_4)
        )
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        mockNames.forEachIndexed { _, name ->
            connectedStudents.add(
                ConnectedStudentItem(
                    id = DataManager.generateSubmissionId(),
                    name = name,
                    grade = getString(R.string.mock_grade),
                    section = getString(R.string.mock_section),
                    connectedAt = currentTime
                )
            )
        }
    }

    private fun startRadar() {
        isRadarActive = true
        binding.radarStatusText.text = getString(R.string.radar_status_running)
        binding.activateRadarButton.text = getString(R.string.btn_stop_radar)
        binding.activateRadarButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.tg_red)
        )

        // تأثير النبض للمؤشر
        startPulseAnimation()

        // بدء المزامنة التلقائية
        startAutoSync()
        updateConnectedCount()
    }

    private fun stopRadar() {
        isRadarActive = false
        binding.radarStatusText.text = getString(R.string.radar_status_stopped)
        binding.activateRadarButton.text = getString(R.string.btn_start_radar)
        binding.activateRadarButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.tg_blue)
        )

        // إيقاف تأثير النبض
        stopPulseAnimation()

        // إيقاف المزامنة التلقائية
        stopAutoSync()
    }

    private var pulseAnimator: ValueAnimator? = null

    private fun startPulseAnimation() {
        binding.radarCircle.alpha = 0.3f
        pulseAnimator = ValueAnimator.ofFloat(0.3f, 0.8f, 0.3f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                binding.radarCircle.alpha = animator.animatedValue as Float
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.radarCircle.alpha = 0.3f
    }

    private fun syncData(silent: Boolean) {
        if (!silent) {
            binding.syncButton.isEnabled = false
        }

        Handler(Looper.getMainLooper()).postDelayed({
            // محاكاة بيانات الطلاب المتصلين
            if (isRadarActive) {
                loadMockConnectedStudents()
                binding.studentsRecyclerView.adapter?.notifyDataSetChanged()
                updateConnectedCount()
            }

            if (!silent) {
                val lastSync = System.currentTimeMillis()
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastSync))
                Toast.makeText(
                    this,
                    getString(R.string.toast_sync_result, connectedStudents.size, timeStr),
                    Toast.LENGTH_LONG
                ).show()
                binding.syncButton.isEnabled = true
            }
        }, if (silent) 500 else 1500)
    }

    private fun startAutoSync() {
        syncData(true)
        syncTimer = Timer()
        syncTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread { syncData(true) }
            }
        }, SYNC_INTERVAL_MS, SYNC_INTERVAL_MS)
    }

    private fun stopAutoSync() {
        syncTimer?.cancel()
        syncTimer = null
    }

    private fun updateConnectedCount() {
        val count = if (isRadarActive) connectedStudents.size else 0
        binding.connectedCountText.text = "$count طلاب متصلون"
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
            val classesCount = DataManager.getClasses(this@SyncActivity, teacherId, subject.optString("id")).size
            holder.binding.gradeText.text = getString(R.string.text_classes_count, classesCount)
            holder.binding.countText.text = if (classesCount > 0) getString(R.string.text_active_classes, classesCount) else getString(R.string.text_no_classes)
            holder.binding.countText.visibility = View.VISIBLE
            holder.binding.root.setOnClickListener { onItemClick(subject) }
        }

        override fun getItemCount(): Int = subjects.size

        inner class SubjectViewHolder(val binding: ItemSubjectBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class ConnectedStudentsAdapter(
        private val students: List<ConnectedStudentItem>
    ) : RecyclerView.Adapter<ConnectedStudentsAdapter.StudentViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
            val binding = ItemConnectedStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return StudentViewHolder(binding)
        }

        override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
            val student = students[position]
            holder.binding.studentName.text = student.name
            holder.binding.onlineStatusText.text = getString(R.string.label_connected_now)
            holder.binding.connectionTimeText.text = student.connectedAt

            // تعيين أول حرف من الاسم كإيموجي
            val firstLetter = if (student.name.isNotEmpty()) student.name[0].toString() else "?"
            holder.binding.studentEmoji.text = firstLetter

            // تعيين لون عشوائي للأفاتار
            val colorRes = avatarColors[position % avatarColors.size]
            val avatarBg = holder.binding.studentAvatar.background
            if (avatarBg is GradientDrawable) {
                avatarBg.setColor(ContextCompat.getColor(this@SyncActivity, colorRes))
            }
        }

        override fun getItemCount(): Int = students.size

        inner class StudentViewHolder(val binding: ItemConnectedStudentBinding) : RecyclerView.ViewHolder(binding.root)
    }
}

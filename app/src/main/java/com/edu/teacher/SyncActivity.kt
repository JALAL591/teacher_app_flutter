package com.edu.teacher

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.databinding.ActivitySyncBinding
import com.edu.teacher.databinding.ItemConnectedStudentBinding
import com.edu.teacher.databinding.ItemSubjectBinding
import com.edu.teacher.utils.WifiDirectManager
import com.edu.teacher.utils.WifiP2pBroadcastListener
import org.json.JSONObject
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class SyncActivity : AppCompatActivity(), WifiP2pBroadcastListener {

    private lateinit var binding: ActivitySyncBinding
    private var teacherServer: TeacherServer? = null
    private var wifiDirectManager: WifiDirectManager? = null
    
    private var connectionMode = ConnectionMode.HOTSPOT

    private var subjectsList = mutableListOf<JSONObject>()
    private var activeSubject: JSONObject? = null
    private var connectedStudents = mutableListOf<ConnectedStudentItem>()
    private var teacherId: String = ""
    private var isRadarActive = false
    private var syncTimer: Timer? = null
    private val SYNC_INTERVAL_MS = 15 * 60 * 1000L

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

    enum class ConnectionMode {
        HOTSPOT,
        WIFI_DIRECT,
        BLUETOOTH
    }

    data class ConnectedStudentItem(
        val id: String,
        val name: String,
        val grade: String,
        val section: String,
        val connectedAt: String
    )
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            initWifiDirect()
        } else {
            Toast.makeText(this, "صلاحيات الموقع مطلوبة لـ WiFi Direct", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        teacherId = DataManager.getTeacherId(this) ?: run {
            Toast.makeText(this, getString(R.string.error_teacher_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initServer()
        initViews()
        loadSubjects()
        checkPermissions()
    }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            initWifiDirect()
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun initWifiDirect() {
        wifiDirectManager = WifiDirectManager(this)
        wifiDirectManager?.initialize()
        wifiDirectManager?.setReceiverListener(this)
        wifiDirectManager?.register()
        
        wifiDirectManager?.onWifiP2pEnabled = { enabled ->
            runOnUiThread {
                if (enabled) {
                    Toast.makeText(this, "WiFi Direct متاح", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        wifiDirectManager?.onPeersAvailable = { peers ->
            runOnUiThread {
                if (peers.isNotEmpty()) {
                    showPeersDialog(peers)
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun showPeersDialog(peers: List<WifiP2pDevice>) {
        val deviceNames = peers.map { it.deviceName.ifEmpty { it.deviceAddress } }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("الطلاب المتوفرين")
            .setItems(deviceNames) { _, which ->
                wifiDirectManager?.connect(peers[which])
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
    
    @SuppressLint("MissingPermission")
    private fun showConnectionModeDialog() {
        val modes = arrayOf("نقطة البث (Hotspot)", "WiFi Direct", "البلوتوث")
        
        AlertDialog.Builder(this)
            .setTitle("اختر طريقة الاتصال")
            .setItems(modes) { _, which ->
                when (which) {
                    0 -> {
                        connectionMode = ConnectionMode.HOTSPOT
                        startHotspotMode()
                    }
                    1 -> {
                        connectionMode = ConnectionMode.WIFI_DIRECT
                        startWifiDirectMode()
                    }
                    2 -> {
                        connectionMode = ConnectionMode.BLUETOOTH
                        startBluetoothMode()
                    }
                }
            }
            .show()
    }
    
    private fun startHotspotMode() {
        Toast.makeText(this, "وضع نقطة البث - IP: 192.168.43.1", Toast.LENGTH_LONG).show()
    }
    
    @SuppressLint("MissingPermission")
    private fun startWifiDirectMode() {
        if (wifiDirectManager?.hasPermissions() == true) {
            wifiDirectManager?.discoverPeers()
            Toast.makeText(this, "جاري البحث عن الطلاب...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "مفقود صلاحيات WiFi Direct", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startBluetoothMode() {
        Toast.makeText(this, "وضع البلوتوث - جاري التطوير", Toast.LENGTH_SHORT).show()
    }

    private fun initServer() {
        teacherServer = TeacherServer(this)
        
        teacherServer?.onStudentConnected = { studentId, studentName, grade, section ->
            runOnUiThread {
                val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val existingIndex = connectedStudents.indexOfFirst { it.id == studentId }
                
                val newStudent = ConnectedStudentItem(
                    id = studentId,
                    name = studentName,
                    grade = grade.ifEmpty { "غير محدد" },
                    section = section.ifEmpty { "أ" },
                    connectedAt = currentTime
                )
                
                if (existingIndex >= 0) {
                    connectedStudents[existingIndex] = newStudent
                } else {
                    connectedStudents.add(newStudent)
                }
                
                binding.studentsRecyclerView.adapter?.notifyDataSetChanged()
                updateConnectedCount()
                Toast.makeText(this, "طالب متصل: $studentName", Toast.LENGTH_SHORT).show()
            }
        }
        
        teacherServer?.onStudentDisconnected = { studentId ->
            runOnUiThread {
                connectedStudents.removeAll { it.id == studentId }
                binding.studentsRecyclerView.adapter?.notifyDataSetChanged()
                updateConnectedCount()
            }
        }
        
        teacherServer?.onLessonsRequested = { grade, section, _ ->
            runOnUiThread {
                Toast.makeText(this, "طلب دروس: $grade - $section", Toast.LENGTH_SHORT).show()
                broadcastLessonsToStudents(grade, section)
            }
        }
    }

    private fun broadcastLessonsToStudents(grade: String, section: String) {
        val prefs = getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
        val lessonsJson = prefs.getString("lessons_$teacherId", "[]") ?: "[]"
        
        val lessons = mutableListOf<JSONObject>()
        try {
            val jsonArray = org.json.JSONArray(lessonsJson)
            for (i in 0 until jsonArray.length()) {
                val lesson = jsonArray.getJSONObject(i)
                val lessonGrade = lesson.optString("grade", "")
                val lessonSection = lesson.optString("section", "")
                if ((grade.isEmpty() || lessonGrade == grade) && 
                    (section.isEmpty() || lessonSection == section)) {
                    lessons.add(lesson)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        teacherServer?.broadcastLessonsUpdate(lessons)
    }

    override fun onResume() {
        super.onResume()
        if (isRadarActive) {
            teacherServer?.start()
        }
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
                showConnectionModeDialog()
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

        binding.studentsRecyclerView.adapter = ConnectedStudentsAdapter(connectedStudents)
        updateConnectedCount()
    }

    private fun startRadar() {
        if (!checkLocationEnabled()) {
            showLocationSettingsDialog()
            return
        }
        
        isRadarActive = true
        teacherServer?.start()
        (application as? TeacherApp)?.teacherServer = teacherServer
        
        binding.radarStatusText.text = getString(R.string.radar_status_running)
        binding.activateRadarButton.text = getString(R.string.btn_stop_radar)
        binding.activateRadarButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.tg_red)
        )

        startPulseAnimation()
        startAutoSync()
        updateConnectedCount()
        
        Toast.makeText(this, "تم تشغيل الرادار - الطلاب يمكنهم الاتصال الآن", Toast.LENGTH_LONG).show()
    }
    
    private fun checkLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    private fun showLocationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("تفعيل الموقع")
            .setMessage("يجب تفعيل الموقع (GPS) لاستخدام الرادار. هل تريد فتح الإعدادات؟")
            .setPositiveButton("فتح الإعدادات") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun stopRadar() {
        isRadarActive = false
        teacherServer?.stop()
        (application as? TeacherApp)?.teacherServer = null
        
        binding.radarStatusText.text = getString(R.string.radar_status_stopped)
        binding.activateRadarButton.text = getString(R.string.btn_start_radar)
        binding.activateRadarButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.tg_blue)
        )

        stopPulseAnimation()
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
            if (isRadarActive) {
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
        val count = teacherServer?.getConnectedCount() ?: connectedStudents.size
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

            holder.binding.studentEmoji.setImageResource(R.drawable.ic_student)

            val colorRes = avatarColors[position % avatarColors.size]
            val avatarBg = holder.binding.studentAvatar.background
            if (avatarBg is GradientDrawable) {
                avatarBg.setColor(ContextCompat.getColor(this@SyncActivity, colorRes))
            }
        }

        override fun getItemCount(): Int = students.size

        inner class StudentViewHolder(val binding: ItemConnectedStudentBinding) : RecyclerView.ViewHolder(binding.root)
    }
    
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        if (intent.action == android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
            wifiDirectManager?.requestConnectionInfo()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRadar()
        teacherServer?.stop()
        wifiDirectManager?.unregister()
    }
}

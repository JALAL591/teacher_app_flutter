package com.edu.student.services

import android.content.Context
import android.util.Log
import com.edu.common.ConnectionManager
import com.edu.student.data.preferences.StudentPreferences
import com.edu.student.domain.model.AnswerSubmission
import com.edu.student.domain.model.HomeworkSubmission
import com.edu.student.domain.model.SubmissionSummary
import com.edu.student.network.TeacherDiscovery
import com.edu.student.utils.StudentWifiDirectManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class TeacherClient(private val context: Context) {
    
    companion object {
        private const val TAG = "TeacherClient"
        const val SERVER_PORT = 9999
        private const val HOTSPOT_IP = "192.168.43.1"
    }
    
    private val prefs = StudentPreferences(context)
    private val gson = Gson()
    
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    @Volatile
    private var isRunning = false
    private var currentServerIP: String = HOTSPOT_IP
    
    private var wifiDirectManager: StudentWifiDirectManager? = null
    private var connectionManager: ConnectionManager? = null
    
    @Volatile
    var isConnected: Boolean = false
        private set
    
    @Volatile
    private var isConnecting = false
    
    private var hasAutoRequested = false
    private var currentGrade: String? = null
    private var currentSection: String? = null
    
    private val discovery = TeacherDiscovery(context)
    
    // استخدام CopyOnWriteArrayList لمنع ConcurrentModificationException
    private val listeners = mutableMapOf<String, MutableList<(Any?) -> Unit>>()
    private var retryJob: Job? = null
    @Volatile
    private var isManualDisconnect = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenJob: Job? = null
    private var connectionMonitorJob: Job? = null
    private var lastPingTime = 0L
    private var retryCount = 0
    private val maxRetries = 3
    
    interface ClientCallback {
        fun onConnected(ip: String)
        fun onDisconnected()
        fun onLessonsReceived(lessons: List<com.edu.student.domain.model.Lesson>)
        fun onError(error: String)
    }
    
    @Volatile
    private var callback: ClientCallback? = null
    
    fun setCallback(cb: ClientCallback?) {
        callback = cb
    }
    
    fun setStudentInfo(grade: String, section: String) {
        currentGrade = grade
        currentSection = section
    }
    
    fun init() {
        isManualDisconnect = false
        isRunning = true
        
        connectionManager = ConnectionManager(context)
        
        discoverAndConnect()
    }

private fun discoverTeacherWithConnectionManager() {
        scope.launch(Dispatchers.IO) {
            try {
                val teacherIp = connectionManager?.discoverTeacher(object : ConnectionManager.ConnectionCallback {
                    override fun onModeDetected(mode: ConnectionManager.ConnectionMode) {
                        Log.d(TAG, "Trying connection mode: $mode")
                        runOnUiThread {
                            when (mode) {
                                ConnectionManager.ConnectionMode.BLE_DISCOVERY -> 
                                    safeEmitCallback { callback?.onError("جاري البحث عبر البلوتوث...") }
                                ConnectionManager.ConnectionMode.WIFI_DIRECT -> 
                                    safeEmitCallback { callback?.onError("جاري البحث عبر WiFi Direct...") }
                                ConnectionManager.ConnectionMode.SAME_WIFI -> 
                                    safeEmitCallback { callback?.onError("جاري البحث على الشبكة المحلية...") }
                                ConnectionManager.ConnectionMode.HOTSPOT -> 
                                    safeEmitCallback { callback?.onError("جاري البحث عبر نقطة اتصال...") }
                            }
                        }
                    }
                    
                    override fun onTeacherFound(ip: String, mode: ConnectionManager.ConnectionMode) {
                        Log.d(TAG, "Teacher found via $mode at $ip")
                    }
                    
                    override fun onConnectionFailed() {
                        Log.e(TAG, "All connection modes failed")
                    }
                })
                
                if (teacherIp != null) {
                    currentServerIP = teacherIp
                    runOnUiThread {
                        safeEmitCallback { callback?.onError("تم العثور على المعلم! جاري اتصال...") }
                    }
                    connectToServer(teacherIp)
                } else {
                    runOnUiThread {
                        safeEmitCallback { callback?.onError("لم يتم العثور على المعلم. تأكد من:\n" +
                            "١. تفعيل WiFi على الجهازين\n" +
                            "٢. أن المعلم قريب منك\n" +
                            "٣. تطبيق المعلم مفتوح وفي وضع 'بدء الدرس'") }
                    }
                    scheduleRetry()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery failed: ${e.message}", e)
                scheduleRetry()
            }
        }
    }
    
    private fun initWifiDirect() {
        wifiDirectManager = StudentWifiDirectManager(context)
        wifiDirectManager?.initialize()
        wifiDirectManager?.register()
        
        wifiDirectManager?.onWifiP2pEnabled = { enabled ->
            if (isRunning) {
                Log.d(TAG, "WiFi Direct enabled: $enabled")
            }
        }
        
        wifiDirectManager?.onPeersAvailable = { peers ->
            if (isRunning) {
                Log.d(TAG, "Found ${peers.size} teachers nearby")
                if (!isConnected && peers.isNotEmpty()) {
                    Log.d(TAG, "Auto-connecting to first teacher: ${peers.first().deviceName}")
                    wifiDirectManager?.connectToTeacher(peers.first())
                }
            }
        }
        
        wifiDirectManager?.onConnectedToTeacher = { groupOwnerAddress ->
            if (isRunning && !isConnected) {
                val teacherIp = groupOwnerAddress.hostAddress
                Log.d(TAG, "Connected via WiFi Direct to: $teacherIp")
                currentServerIP = teacherIp ?: HOTSPOT_IP
                connectToServer(currentServerIP)
            }
        }
        
        wifiDirectManager?.onDisconnected = {
            if (isRunning) {
                Log.d(TAG, "Disconnected from WiFi Direct")
            }
        }
    }
    
    private fun connect() {
        if (!isRunning || isConnecting || isConnected) return
        val targetIP = currentServerIP.ifEmpty { HOTSPOT_IP }
        scope.launch(Dispatchers.IO) {
            connectToServer(targetIP)
        }
    }

    fun discoverAndConnect() {
        if (!isRunning) {
            isRunning = true
        }
        
        scope.launch(Dispatchers.Main) {
            safeEmitCallback { callback?.onError("🔍 جاري البحث عن المعلم...") }
        }
        
        scope.launch(Dispatchers.IO) {
            discovery.discoverTeacher(object : TeacherDiscovery.DiscoveryListener {
                override fun onTeacherFound(ip: String) {
                    Log.d(TAG, "Teacher discovered at: $ip")
                    
                    val cleanIp = ip.split(":").first()
                    currentServerIP = cleanIp
                    
                    scope.launch(Dispatchers.Main) {
                        safeEmitCallback { callback?.onError("✅ تم العثور على المعلم! جاري الاتصال...") }
                    }
                    
                    connectToServer(cleanIp)
                }
                
                override fun onDiscoveryFailed() {
                    Log.w(TAG, "Teacher discovery failed")
                    tryFallbackIPs()
                }
            })
        }
    }

    private fun tryFallbackIPs() {
        val fallbackIPs = listOf(
            "192.168.0.101",
            "192.168.49.1",
            "192.168.1.1",
            "192.168.43.1"
        )
        
        scope.launch(Dispatchers.Main) {
            safeEmitCallback { callback?.onError("🔄 جاري تجربة اتصال بديل...") }
        }
        
        tryNextFallbackIP(fallbackIPs, 0)
    }
    
    private fun tryNextFallbackIP(ips: List<String>, index: Int) {
        if (index >= ips.size) {
            scope.launch(Dispatchers.Main) {
                safeEmitCallback { callback?.onError("❌ لم يتم العثور على المعلم") }
            }
            scheduleRetry()
            return
        }
        
        val ip = ips[index]
        Log.d(TAG, "Trying fallback IP: $ip")
        
        scope.launch(Dispatchers.IO) {
            connectToServer(ip)
            
            delay(2000)
            if (!isConnected) {
                tryNextFallbackIP(ips, index + 1)
            }
        }
    }
    
    private fun connectToServer(serverAddress: String) {
        if (!isRunning) return
        try {
            val address = InetAddress.getByName(serverAddress)
            connectToServer(address)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid address: $serverAddress")
            if (serverAddress != HOTSPOT_IP) {
                connectToServer(HOTSPOT_IP)
            }
        }
    }
    
    private fun connectToServer(serverAddress: InetAddress) {
        if (!isRunning) return
        if (isConnecting || isConnected) return
        
        scope.launch(Dispatchers.IO) {
            try {
                isConnecting = true
                val ip = serverAddress.hostAddress ?: HOTSPOT_IP
                Log.d(TAG, "Attempting to connect to teacher at $ip:$SERVER_PORT")
                
                closeConnectionSafely()
                
                socket = Socket().apply {
                    soTimeout = 0
                    keepAlive = true
                    tcpNoDelay = true
                    receiveBufferSize = 128 * 1024
                    sendBufferSize = 128 * 1024
                    connect(InetSocketAddress(serverAddress, SERVER_PORT), 15000)
                }
                
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), "UTF-8"), 8192)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                
                isConnected = true
                isConnecting = false
                currentServerIP = ip
                retryCount = 0
                Log.d(TAG, "SUCCESS: Connected to teacher at $ip:$SERVER_PORT")
                
                // تسجيل الطالب
                val student = prefs.getStudent()
                val grade = currentGrade ?: student?.grade ?: ""
                val section = currentSection ?: student?.section ?: ""
                
                if (student != null) {
                    registerStudent(student.id, student.name, grade, section)
                }
                
                if (!hasAutoRequested && isRunning) {
                    hasAutoRequested = true
                    if (grade.isNotEmpty() && section.isNotEmpty()) {
                        Log.d(TAG, "Requesting lessons for $grade-$section")
                        requestLessons(grade, section)
                    }
                }
                
                safeEmitCallback { callback?.onConnected(ip) }
                safeEmit("connected", ip)
                
                startConnectionMonitor()
                startListening()
                
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Connection failed: ${e.message}", e)
                    handleConnectionFailed()
                }
            }
        }
    }
    
    private fun startListening() {
        listenJob?.cancel()
        listenJob = scope.launch(Dispatchers.IO) {
            listenForMessages()
        }
    }
    
    private fun startConnectionMonitor() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = scope.launch(Dispatchers.IO) {
            while (isRunning && isConnected) {
                delay(10000)
                if (isRunning && isConnected) {
                    sendPing()
                }
            }
        }
    }
    
    private fun sendPing() {
        if (!isConnected || !isRunning || writer == null) return
        
        try {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastPingTime > 5000) {
                writer?.println("""{"type":"PING","timestamp":"$currentTime"}""")
                lastPingTime = currentTime
                Log.d(TAG, "Ping sent")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ping: ${e.message}")
            handleConnectionLost()
        }
    }
    
    private suspend fun listenForMessages() {
        try {
            while (isRunning && isConnected) {
                try {
                    val line = reader?.readLine()
                    if (line == null) {
                        if (isRunning) {
                            Log.w(TAG, "End of stream reached")
                        }
                        break
                    }
                    if (line.isNotEmpty() && isRunning) {
                        handleIncoming(line)
                    }
                } catch (e: java.io.IOException) {
                    if (isRunning) {
                        Log.e(TAG, "Read error: ${e.message}")
                        break
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Read error: ${e.message}")
                        break
                    }
                }
            }
        } finally {
            if (isRunning && !isManualDisconnect) {
                Log.d(TAG, "Connection lost, attempting immediate reconnect")
                handleConnectionLost()
                if (isRunning && !isConnected) {
                    delay(1000)
                    if (isRunning && retryCount < maxRetries) {
                        retryCount++
                        Log.d(TAG, "Reconnect attempt $retryCount/$maxRetries")
                        connectToServer(currentServerIP)
                    }
                }
            }
        }
    }
    
    private fun handleConnectionLost() {
        if (!isRunning) return
        
        isConnected = false
        safeEmitCallback { callback?.onDisconnected() }
        safeEmit("disconnected", null)
        
        if (isRunning) {
            wifiDirectManager?.discoverTeachers()
            scheduleRetry()
        }
    }
    
    private fun handleConnectionFailed() {
        if (!isRunning) return
        
        isConnected = false
        closeConnectionSafely()
        
        val lastIP = currentServerIP
        
        if (isRunning) {
            Log.w(TAG, "Connection to $lastIP failed. Retrying...")
            wifiDirectManager?.discoverTeachers()
            
            val fallbackIP = if (lastIP != HOTSPOT_IP && lastIP.isNotEmpty()) HOTSPOT_IP else null
            if (fallbackIP != null) {
                scope.launch {
                    delay(2000)
                    if (!isConnected && isRunning) {
                        Log.d(TAG, "Fallback: trying default IP $HOTSPOT_IP")
                        connectToServer(HOTSPOT_IP)
                    }
                }
            }
            
            safeEmitCallback { callback?.onError("جاري محاولة الاتصال...") }
            safeEmit("disconnected", null)
            scheduleRetry()
        }
    }
    
    private suspend fun handleIncoming(data: String) {
        if (!isRunning) return
        
        try {
            val trimmedData = data.trim()
            if (trimmedData.isEmpty()) return
            
            val json: JSONObject = try {
                JSONObject(trimmedData)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid JSON format: $trimmedData")
                return
            }
            
            // معالجة الدرس المباشر
            if (json.has("id") && json.has("title") && json.has("questions")) {
                processDirectLesson(json)
                return
            }
            
            val action = json.optString("action", "")
            val status = json.optString("status", "")
            
            when {
                action == "LESSONS_DATA" || action == "lessons_updated" -> {
                    val lessonsJson = json.optJSONArray("lessons")
                    if (lessonsJson != null && lessonsJson.length() > 0) {
                        processLessonsArray(lessonsJson)
                    }
                }
                
                action == "LESSON_BROADCAST" -> {
                    android.util.Log.d("TeacherClient", "=== RECEIVED LESSON_BROADCAST ===")
                    val lessonJson = json.optJSONObject("lesson")
                    if (lessonJson != null) {
                        android.util.Log.d("TeacherClient", "Processing lesson: ${lessonJson.optString("title", "unknown")}")
                        processDirectLesson(lessonJson)
                    } else {
                        val lessonStr = json.optString("lesson", "")
                        if (lessonStr.isNotEmpty()) {
                            try {
                                val parsedLessonJson = JSONObject(lessonStr)
                                android.util.Log.d("TeacherClient", "Lesson from string: ${parsedLessonJson.optString("title", "unknown")}")
                                if (isLessonForThisStudent(parsedLessonJson)) {
                                    val lesson = gson.fromJson(lessonStr, com.edu.student.domain.model.Lesson::class.java)
                                    android.util.Log.d("TeacherClient", "Processing lesson for this student")
                                    processLesson(lesson)
                                } else {
                                    Log.d(TAG, "Broadcasted lesson not for this student, skipping")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing lesson from string", e)
                            }
                        }
                    }
                    android.util.Log.d("TeacherClient", "=== LESSON_BROADCAST PROCESSED ===")
                }
                
                status == "REGISTERED" -> {
                    val teacherIPFromServer = json.optString("teacherIP", "")
                    if (teacherIPFromServer.isNotEmpty() && teacherIPFromServer != HOTSPOT_IP) {
                        currentServerIP = teacherIPFromServer
                        Log.d(TAG, "Updated server IP from teacher: $currentServerIP")
                    }
                    Log.d(TAG, "Registered with teacher")
                    safeEmit("registered", json.optString("id"))
                }
                
                status == "PONG" -> {
                    Log.d(TAG, "Pong received")
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error parsing message: ${e.message}")
            }
        }
    }
    
    private suspend fun processDirectLesson(json: JSONObject) {
        try {
            if (!isLessonForThisStudent(json)) {
                Log.d(TAG, "Lesson not for this student, skipping")
                return
            }
            val lesson = gson.fromJson(json.toString(), com.edu.student.domain.model.Lesson::class.java)
            processLesson(lesson)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing direct lesson: ${e.message}")
        }
    }
    
    private suspend fun processLessonsArray(lessonsJson: JSONArray) {
        try {
            val teacherId = prefs.getAssignedTeacherId() ?: ""
            prefs.saveCachedLessons(teacherId, lessonsJson.toString())
            
            val lessons = mutableListOf<com.edu.student.domain.model.Lesson>()
            for (i in 0 until lessonsJson.length()) {
                try {
                    val lessonJson = lessonsJson.getJSONObject(i)
                    if (isLessonForThisStudent(lessonJson)) {
                        val lesson = gson.fromJson(lessonJson.toString(), com.edu.student.domain.model.Lesson::class.java)
                        lessons.add(lesson)
                    } else {
                        Log.d(TAG, "Skipping lesson ${lessonJson.optString("title", "")} - not for this student")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing lesson at index $i")
                }
            }
            
            if (lessons.isNotEmpty() && isRunning) {
                Log.d(TAG, "Received ${lessons.size} filtered lessons")
                safeEmitCallback { callback?.onLessonsReceived(lessons) }
                safeEmit("lessons_updated", lessons)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing lessons array: ${e.message}")
        }
    }
    
    private fun isLessonForThisStudent(lessonJson: JSONObject): Boolean {
        val lessonGrade = lessonJson.optString("grade", "")
        val lessonSection = lessonJson.optString("section", "")
        
        val studentGrade = getStudentGrade()
        val studentSection = getStudentSection()
        
        Log.d(TAG, "Filtering: lesson=$lessonGrade-$lessonSection, student=$studentGrade-$studentSection")
        
        return when {
            lessonGrade.isNotEmpty() && lessonSection.isNotEmpty() -> {
                lessonGrade == studentGrade && lessonSection == studentSection
            }
            lessonGrade.isNotEmpty() -> {
                lessonGrade == studentGrade
            }
            else -> true
        }
    }
    
    private fun getStudentGrade(): String {
        return currentGrade ?: prefs.getStudent()?.grade ?: ""
    }
    
    private fun getStudentSection(): String {
        return currentSection ?: prefs.getStudent()?.section ?: ""
    }
    
    private suspend fun processLesson(lesson: com.edu.student.domain.model.Lesson) {
        try {
            val lessonJson = JSONObject(gson.toJson(lesson))
            if (!isLessonForThisStudent(lessonJson)) {
                Log.d(TAG, "Lesson ${lesson.title} not for this student, skipping")
                return
            }
            
            Log.d(TAG, "Processing lesson: ${lesson.title}")
            
            val teacherId = prefs.getAssignedTeacherId() ?: ""
            val cached = prefs.getCachedLessons(teacherId)
            val lessons = if (cached != null) {
                try {
                    val type = object : TypeToken<MutableList<com.edu.student.domain.model.Lesson>>() {}.type
                    gson.fromJson<MutableList<com.edu.student.domain.model.Lesson>>(cached, type)
                } catch (e: Exception) { 
                    mutableListOf() 
                }
            } else { 
                mutableListOf() 
            }
            
            if (lessons.none { it.id == lesson.id }) {
                lessons.add(0, lesson)
                prefs.saveCachedLessons(teacherId, gson.toJson(lessons))
            }
            
            if (isRunning) {
                safeEmitCallback { callback?.onLessonsReceived(listOf(lesson)) }
                safeEmit("lesson_broadcast", lesson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing lesson: ${e.message}")
        }
    }
    
    private fun sendData(payload: Map<String, Any?>) {
        Log.d(TAG, "--- SEND DATA START ---")
        Log.d(TAG, "Payload type: ${payload["type"]}")
        Log.d(TAG, "isConnected: $isConnected")
        Log.d(TAG, "isRunning: $isRunning")
        
        if (!isConnected || !isRunning) {
            Log.e(TAG, "❌ Cannot send: isConnected=$isConnected, isRunning=$isRunning")
            runOnUiThread {
                android.widget.Toast.makeText(context, "❌ غير متصل بالمعلم", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        if (writer == null) {
            Log.e(TAG, "❌ Writer is null! Cannot send data")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val data = payload.toMutableMap()
                data["timestamp"] = System.currentTimeMillis().toString()
                val json = JSONObject(data).toString()
                
                Log.d(TAG, "JSON to send (first 300 chars): ${json.take(300)}")
                Log.d(TAG, "JSON length: ${json.length} bytes")
                
                writer?.println(json)
                
                val hasError = writer?.checkError() ?: true
                if (hasError) {
                    Log.e(TAG, "❌❌❌ WRITE ERROR - checkError() returned true ❌❌❌")
                    runOnUiThread {
                        android.widget.Toast.makeText(context, "❌ فشل إرسال الواجب", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    if (isRunning) {
                        handleConnectionLost()
                    }
                } else {
                    Log.d(TAG, "✅✅✅ DATA SENT SUCCESSFULLY ✅✅✅")
                    runOnUiThread {
                        android.widget.Toast.makeText(context, "✅ تم إرسال الواجب", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                
                Log.d(TAG, "--- SEND DATA END ---")
                
            } catch (e: java.io.IOException) {
                Log.e(TAG, "❌ IOException: ${e.message}", e)
                runOnUiThread {
                    android.widget.Toast.makeText(context, "❌ خطأ في الاتصال", android.widget.Toast.LENGTH_SHORT).show()
                }
                if (isRunning) {
                    handleConnectionLost()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Unexpected error: ${e.message}", e)
                runOnUiThread {
                    android.widget.Toast.makeText(context, "❌ خطأ غير متوقع", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun sendCustomData(payload: Map<String, Any?>) {
        sendData(payload)
    }
    
    fun registerStudent(studentId: String, studentName: String, grade: String, section: String) {
        sendData(mapOf(
            "type" to "REGISTER_STUDENT",
            "studentId" to studentId,
            "studentName" to studentName,
            "grade" to grade,
            "section" to section
        ))
    }
    
    fun requestLessons(grade: String, section: String) {
        sendData(mapOf(
            "type" to "REQUEST_LESSONS",
            "teacherId" to (prefs.getAssignedTeacherId() ?: ""),
            "grade" to grade,
            "section" to section
        ))
    }
    
    fun submitHomework(data: Map<String, Any?>) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "=== SUBMIT HOMEWORK CALLED ===")
        Log.d(TAG, "========================================")
        Log.d(TAG, "isConnected: $isConnected")
        Log.d(TAG, "isRunning: $isRunning")
        Log.d(TAG, "writer is null: ${writer == null}")
        Log.d(TAG, "socket is null: ${socket == null}")
        Log.d(TAG, "socket is connected: ${socket?.isConnected}")
        Log.d(TAG, "socket is closed: ${socket?.isClosed}")
        Log.d(TAG, "Data keys: ${data.keys}")
        Log.d(TAG, "========================================")
        
        if (!isConnected) {
            Log.e(TAG, "❌❌❌ NOT CONNECTED TO TEACHER ❌❌❌")
            runOnUiThread {
                android.widget.Toast.makeText(context, "❌ غير متصل بالمعلم", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        
        if (!isRunning) {
            Log.e(TAG, "❌❌❌ CLIENT NOT RUNNING ❌❌❌")
            return
        }
        
        if (writer == null) {
            Log.e(TAG, "❌❌❌ WRITER IS NULL ❌❌❌")
            return
        }
        
        sendData(mapOf(
            "type" to "HOMEWORK_SUBMISSION"
        ) + data)
    }
    
    fun ping() {
        sendData(mapOf("type" to "PING"))
    }
    
    fun submitSavedHomework(lessonId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val solutionStr = prefs.getHomeworkSolution(lessonId)
        if (solutionStr == null) {
            onError("لا يوجد واجب محفوظ لهذا الدرس")
            return
        }
        
        try {
            val solution = JSONObject(solutionStr)
            val student = prefs.getStudent()
            
            val submission = HomeworkSubmission(
                submissionId = "sub_${System.currentTimeMillis()}",
                studentId = student?.id ?: "",
                studentName = student?.name ?: "",
                studentGrade = currentGrade ?: student?.grade ?: "",
                studentSection = currentSection ?: student?.section ?: "",
                avatar = student?.avatar,
                lessonId = solution.optString("lessonId", lessonId),
                lessonTitle = solution.optString("lessonTitle", ""),
                subjectId = solution.optString("subjectId", ""),
                subjectTitle = solution.optString("subjectTitle", ""),
                classId = solution.optString("classId", ""),
                timestamp = System.currentTimeMillis(),
                answers = parseAnswersFromJson(solution.optJSONArray("answers")),
                summary = parseSummaryFromJson(solution.optJSONObject("summary"))
            )
            
            submitHomework(mapOf("submission" to gson.toJson(submission)))
            onSuccess()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting saved homework: ${e.message}", e)
            onError("فشل إرسال الواجب: ${e.message}")
        }
    }
    
    private fun parseAnswersFromJson(answersArray: JSONArray?): List<AnswerSubmission> {
        val answers = mutableListOf<AnswerSubmission>()
        answersArray?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                answers.add(AnswerSubmission(
                    questionId = obj.optString("questionId", ""),
                    questionText = obj.optString("questionText", ""),
                    questionType = obj.optString("questionType", ""),
                    selectedAnswer = obj.optString("selectedAnswer", "").takeIf { it.isNotEmpty() },
                    correctAnswer = obj.optString("correctAnswer", "").takeIf { it.isNotEmpty() },
                    isCorrect = obj.optBoolean("isCorrect", false),
                    textAnswer = obj.optString("textAnswer", "").takeIf { it.isNotEmpty() },
                    imageAnswer = obj.optString("imageAnswer", "").takeIf { it.isNotEmpty() }
                ))
            }
        }
        return answers
    }
    
    private fun parseSummaryFromJson(summaryJson: JSONObject?): SubmissionSummary {
        return if (summaryJson != null) {
            SubmissionSummary(
                totalQuestions = summaryJson.optInt("totalQuestions", 0),
                correctAnswers = summaryJson.optInt("correctAnswers", 0),
                wrongAnswers = summaryJson.optInt("wrongAnswers", 0),
                essayQuestions = summaryJson.optInt("essayQuestions", 0),
                score = summaryJson.optInt("score", 0),
                percentage = summaryJson.optInt("percentage", 0)
            )
        } else {
            SubmissionSummary(0, 0, 0, 0, 0, 0)
        }
    }
    
    fun on(event: String, callback: (Any?) -> Unit): () -> Unit {
        synchronized(listeners) {
            if (!listeners.containsKey(event)) {
                listeners[event] = mutableListOf()
            }
            listeners[event]?.add(callback)
        }
        return { 
            synchronized(listeners) {
                listeners[event]?.remove(callback) 
            }
        }
    }
    
    private fun safeEmit(event: String, data: Any?) {
        if (!isRunning) return
        
        scope.launch(Dispatchers.Main) {
            try {
                val eventListeners = synchronized(listeners) {
                    listeners[event]?.toList() ?: emptyList()
                }
                eventListeners.forEach { 
                    try {
                        it.invoke(data) 
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in listener for event $event: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting event $event: ${e.message}")
            }
        }
    }
    
    private fun safeEmitCallback(action: () -> Unit) {
        if (!isRunning) return
        
        try {
            runOnUiThread {
                try {
                    action()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in callback: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in safeEmitCallback: ${e.message}")
        }
    }
    
    private fun scheduleRetry() {
        if (isManualDisconnect || !isRunning || isConnecting) return
        
        if (retryCount >= maxRetries) {
            Log.w(TAG, "Max retries reached, stopping retry attempts")
            return
        }
        
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(3000)
            if (!isManualDisconnect && isRunning && !isConnected && !isConnecting) {
                Log.d(TAG, "Retrying connection...")
                hasAutoRequested = false
                connect()
            }
        }
    }
    
    private fun runOnUiThread(action: () -> Unit) {
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    action()
                } catch (e: Exception) {
                    Log.e(TAG, "UI thread action failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            // إذا فشل النشر على UI thread، ننفذ مباشرة
            try {
                action()
            } catch (e2: Exception) {
                // تجاهل
            }
        }
    }
    
    private fun closeConnectionSafely() {
        try {
            reader?.close()
        } catch (e: Exception) { }
        try {
            writer?.close()
        } catch (e: Exception) { }
        try {
            socket?.close()
        } catch (e: Exception) { }
        
        reader = null
        writer = null
        socket = null
        isConnected = false
        isConnecting = false
    }
    
    fun destroy() {
        Log.d(TAG, "Destroying TeacherClient")
        
        isManualDisconnect = true
        isRunning = false
        
        // إلغاء جميع المهام
        retryJob?.cancel()
        retryJob = null
        
        connectionMonitorJob?.cancel()
        connectionMonitorJob = null
        
        listenJob?.cancel()
        listenJob = null
        
        // إلغاء WiFi Direct
        try {
            wifiDirectManager?.unregister()
        } catch (e: Exception) { }
        wifiDirectManager = null
        
        try {
            connectionManager?.cleanup()
        } catch (e: Exception) { }
        connectionManager = null
        
        // إغلاق الاتصال
        closeConnectionSafely()
        
        // تنظيف المستمعين
        synchronized(listeners) {
            listeners.clear()
        }
        
        callback = null
        
        Log.d(TAG, "TeacherClient destroyed")
    }
    
    fun manualReconnect() {
        hasAutoRequested = false
        isManualDisconnect = false
        destroy()
        
        scope.launch {
            delay(1000)
            isRunning = true
            init()
        }
    }
    
    fun startWifiDirectSearch() {
        if (isRunning) {
            wifiDirectManager?.discoverTeachers()
        }
    }
}
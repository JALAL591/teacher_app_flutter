package com.edu.student.services

import android.content.Context
import android.util.Log
import com.edu.student.data.preferences.StudentPreferences
import com.edu.student.domain.model.AnswerSubmission
import com.edu.student.domain.model.HomeworkSubmission
import com.edu.student.domain.model.SubmissionSummary
import com.edu.student.network.TeacherDiscovery
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

class TeacherClient(private val context: Context) {
    
    companion object {
        private const val TAG = "TeacherClient"
        const val SERVER_PORT = 9999
    }
    
    private val prefs = StudentPreferences(context)
    private val gson = Gson()
    
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    @Volatile
    private var isRunning = false
    private var currentServerIP: String = ""
    
    @Volatile
    var isConnected: Boolean = false
        private set
    
    @Volatile
    private var isConnecting = false
    
    private var hasAutoRequested = false
    private var currentGrade: String? = null
    private var currentSection: String? = null
    
    private val discovery = TeacherDiscovery(context)
    
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
        discoverAndConnect()
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
                    prefs.setTeacherIP(cleanIp)
                    
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

    private fun getCurrentNetworkPrefix(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.")) {
                            val parts = ip.split(".")
                            return "${parts[0]}.${parts[1]}.${parts[2]}"
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun tryFallbackIPs() {
        val currentNetworkPrefix = getCurrentNetworkPrefix()
        
        val fallbackIPs = buildList {
            if (currentNetworkPrefix != null) {
                add("${currentNetworkPrefix}.1")
                add("${currentNetworkPrefix}.100")
                add("${currentNetworkPrefix}.101")
                add("${currentNetworkPrefix}.102")
            }
            add("192.168.0.101")
            add("192.168.0.1")
            add("192.168.1.1")
            add("192.168.43.1")
            add("192.168.49.1")
        }.distinct()
        
        scope.launch(Dispatchers.Main) {
            safeEmitCallback { callback?.onError("🔄 جاري تجربة اتصال بديل...") }
        }
        
        tryNextFallbackIP(fallbackIPs, 0)
    }
    
    private fun tryNextFallbackIP(ips: List<String>, index: Int) {
        if (index >= ips.size || !isRunning || isConnected) {
            if (!isConnected) {
                scope.launch(Dispatchers.Main) {
                    safeEmitCallback { callback?.onError("❌ لم يتم العثور على المعلم") }
                }
                scheduleRetry()
            }
            return
        }
        
        val ip = ips[index]
        Log.d(TAG, "Trying fallback IP: $ip")
        
        scope.launch(Dispatchers.IO) {
            try {
                val testSocket = Socket()
                testSocket.connect(InetSocketAddress(ip, SERVER_PORT), 2000)
                testSocket.close()
                
                currentServerIP = ip
                prefs.setTeacherIP(ip)
                connectToServer(ip)
            } catch (e: Exception) {
                Log.w(TAG, "Fallback $ip failed: ${e.message}")
                tryNextFallbackIP(ips, index + 1)
            }
        }
    }
    
    private fun connectToServer(serverAddress: String) {
        if (!isRunning) return
        if (isConnecting || isConnected) return
        
        try {
            val address = InetAddress.getByName(serverAddress)
            connectToServer(address)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid address: $serverAddress", e)
            scheduleRetry()
        }
    }
    
    private fun connectToServer(serverAddress: InetAddress) {
        if (!isRunning) return
        if (isConnecting || isConnected) return
        
        scope.launch(Dispatchers.IO) {
            try {
                isConnecting = true
                val ip = serverAddress.hostAddress ?: return@launch
                Log.d(TAG, "Attempting to connect to teacher at $ip:$SERVER_PORT")
                
                closeConnectionSafely()
                
                socket = Socket().apply {
                    soTimeout = 0
                    keepAlive = true
                    tcpNoDelay = true
                    receiveBufferSize = 128 * 1024
                    sendBufferSize = 128 * 1024
                    connect(InetSocketAddress(serverAddress, SERVER_PORT), 3000)
                }
                
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), "UTF-8"), 8192)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                
                isConnected = true
                isConnecting = false
                currentServerIP = ip
                retryCount = 0
                prefs.setTeacherIP(ip)
                
                Log.d(TAG, "SUCCESS: Connected to teacher at $ip:$SERVER_PORT")
                
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
                isConnecting = false
                if (isRunning) {
                    Log.e(TAG, "Connection failed: ${e.message}", e)
                    scheduleRetry()
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
                delay(15000)
                try {
                    if (socket?.isConnected == true && !socket!!.isClosed) {
                        val ping = JSONObject().apply {
                            put("type", "PING")
                            put("timestamp", System.currentTimeMillis())
                        }
                        writer?.println(ping.toString())
                        if (writer?.checkError() == true) {
                            Log.w(TAG, "Keepalive failed - connection lost")
                            handleDisconnect()
                            break
                        }
                        lastPingTime = System.currentTimeMillis()
                    } else {
                        handleDisconnect()
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Keepalive error: ${e.message}")
                    handleDisconnect()
                    break
                }
            }
        }
    }
    
    private fun handleDisconnect() {
        if (!isManualDisconnect && isRunning) {
            isConnected = false
            closeConnectionSafely()
            runOnUiThread {
                safeEmitCallback { callback?.onDisconnected() }
            }
            if (retryCount < maxRetries) {
                retryCount++
                scheduleRetry()
            }
        }
    }
    
    private suspend fun listenForMessages() {
        try {
            while (isRunning && isConnected) {
                try {
                    val line = reader?.readLine()
                    if (line == null) {
                        if (isRunning) Log.w(TAG, "End of stream reached")
                        break
                    }
                    if (line.isNotEmpty() && isRunning) {
                        handleIncoming(line)
                    }
                } catch (e: java.io.IOException) {
                    if (isRunning) Log.e(TAG, "Read error: ${e.message}")
                    break
                }
            }
        } finally {
            handleDisconnect()
        }
    }
    
    private suspend fun handleIncoming(data: String) {
        if (!isRunning) return
        
        try {
            val trimmedData = data.trim()
            if (trimmedData.isEmpty()) return
            
            val json = JSONObject(trimmedData)
            
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
                    val lessonJson = json.optJSONObject("lesson")
                    if (lessonJson != null) {
                        processDirectLesson(lessonJson)
                    }
                }
                
                status == "REGISTERED" -> {
                    val teacherIPFromServer = json.optString("teacherIP", "")
                    if (teacherIPFromServer.isNotEmpty()) {
                        currentServerIP = teacherIPFromServer
                        prefs.setTeacherIP(teacherIPFromServer)
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
            if (isRunning) Log.e(TAG, "Error parsing message: ${e.message}")
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
        val studentGrade = currentGrade ?: prefs.getStudent()?.grade ?: ""
        val studentSection = currentSection ?: prefs.getStudent()?.section ?: ""
        
        return when {
            lessonGrade.isNotEmpty() && lessonSection.isNotEmpty() -> 
                lessonGrade == studentGrade && lessonSection == studentSection
            lessonGrade.isNotEmpty() -> lessonGrade == studentGrade
            else -> true
        }
    }
    
    private suspend fun processLesson(lesson: com.edu.student.domain.model.Lesson) {
        try {
            Log.d(TAG, "Processing lesson: ${lesson.title}")
            
            val teacherId = prefs.getAssignedTeacherId() ?: ""
            val cached = prefs.getCachedLessons(teacherId)
            val lessons = if (cached != null) {
                try {
                    val type = object : TypeToken<MutableList<com.edu.student.domain.model.Lesson>>() {}.type
                    gson.fromJson<MutableList<com.edu.student.domain.model.Lesson>>(cached, type)
                } catch (e: Exception) { mutableListOf() }
            } else { mutableListOf() }
            
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
        if (!isConnected || !isRunning || writer == null) {
            Log.e(TAG, "Cannot send: isConnected=$isConnected, isRunning=$isRunning, writer=${writer != null}")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val data = payload.toMutableMap()
                data["timestamp"] = System.currentTimeMillis().toString()
                val json = JSONObject(data).toString()
                
                writer?.println(json)
                
                if (writer?.checkError() == true) {
                    Log.e(TAG, "Write error - checkError returned true")
                    if (isRunning) handleDisconnect()
                } else {
                    Log.d(TAG, "Data sent successfully: ${payload["type"]}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send failed: ${e.message}", e)
                if (isRunning) handleDisconnect()
            }
        }
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
    
    fun sendCustomData(payload: Map<String, Any?>) {
        sendData(payload)
    }
    
    fun submitHomework(data: Map<String, Any?>) {
        if (!isConnected || writer == null) {
            Log.e(TAG, "Cannot submit homework - not connected")
            runOnUiThread {
                android.widget.Toast.makeText(context, "❌ غير متصل بالمعلم", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val message = JSONObject().apply {
                    put("type", "HOMEWORK_SUBMISSION")
                    data.forEach { (key, value) -> put(key, value) }
                }
                
                val messageStr = message.toString()
                writer?.println(messageStr)
                
                if (writer?.checkError() == true) {
                    Log.e(TAG, "Failed to send homework - writer error")
                    runOnUiThread {
                        android.widget.Toast.makeText(context, "❌ فشل إرسال الواجب", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    handleDisconnect()
                } else {
                    Log.d(TAG, "Homework submitted successfully")
                    runOnUiThread {
                        android.widget.Toast.makeText(context, "✅ تم إرسال الواجب", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error submitting homework: ${e.message}", e)
                runOnUiThread {
                    android.widget.Toast.makeText(context, "❌ خطأ في الإرسال", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                    try { it.invoke(data) } catch (e: Exception) {} 
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
                try { action() } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
    }
    
    private fun scheduleRetry() {
        if (isManualDisconnect || !isRunning || isConnecting) return
        if (retryCount >= maxRetries) return
        
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(3000)
            if (!isManualDisconnect && isRunning && !isConnected && !isConnecting) {
                Log.d(TAG, "Retrying connection...")
                hasAutoRequested = false
                val savedIP = prefs.getTeacherIP()
                if (savedIP != null) {
                    currentServerIP = savedIP
                    connectToServer(savedIP)
                } else {
                    discoverAndConnect()
                }
            }
        }
    }
    
    private fun runOnUiThread(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }
    
    private fun closeConnectionSafely() {
        try { reader?.close() } catch (e: Exception) {}
        try { writer?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
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
        retryJob?.cancel()
        connectionMonitorJob?.cancel()
        listenJob?.cancel()
        closeConnectionSafely()
        synchronized(listeners) { listeners.clear() }
        callback = null
    }
    
    fun manualReconnect() {
        hasAutoRequested = false
        isManualDisconnect = false
        retryCount = 0
        destroy()
        scope.launch {
            delay(1000)
            isRunning = true
            init()
        }
    }
}
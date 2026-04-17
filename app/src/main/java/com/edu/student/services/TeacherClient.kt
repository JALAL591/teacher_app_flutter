package com.edu.student.services

import android.content.Context
import android.util.Log
import com.edu.student.data.preferences.StudentPreferences
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
    
    @Volatile
    var isConnected: Boolean = false
        private set
    
    private var hasAutoRequested = false
    private var currentGrade: String? = null
    private var currentSection: String? = null
    
    // استخدام CopyOnWriteArrayList لمنع ConcurrentModificationException
    private val listeners = mutableMapOf<String, MutableList<(Any?) -> Unit>>()
    private var retryJob: Job? = null
    @Volatile
    private var isManualDisconnect = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenJob: Job? = null
    
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
        
        initWifiDirect()
        wifiDirectManager?.discoverTeachers()
        connect()
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
                Log.d(TAG, "Connected via WiFi Direct to: ${groupOwnerAddress.hostAddress}")
                currentServerIP = groupOwnerAddress.hostAddress ?: HOTSPOT_IP
                connectToServer(groupOwnerAddress)
            }
        }
        
        wifiDirectManager?.onDisconnected = {
            if (isRunning) {
                Log.d(TAG, "Disconnected from WiFi Direct")
            }
        }
    }
    
    private fun connect() {
        if (!isRunning) return
        scope.launch(Dispatchers.IO) {
            connectToServer(HOTSPOT_IP)
        }
    }
    
    private fun connectToServer(serverAddress: String) {
        if (!isRunning) return
        connectToServer(InetAddress.getByName(serverAddress))
    }
    
    private fun connectToServer(serverAddress: InetAddress) {
        if (!isRunning) return
        
        scope.launch(Dispatchers.IO) {
            try {
                val ip = serverAddress.hostAddress ?: HOTSPOT_IP
                Log.d(TAG, "Connecting to teacher at $ip:$SERVER_PORT")
                
                // إغلاق الاتصال القديم أولاً
                closeConnectionSafely()
                
                socket = Socket().apply {
                    soTimeout = 30000
                    keepAlive = true
                    tcpNoDelay = true
                    connect(InetSocketAddress(serverAddress, SERVER_PORT), 10000)
                }
                
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                writer = PrintWriter(socket!!.getOutputStream(), true)
                
                isConnected = true
                currentServerIP = ip
                Log.d(TAG, "Connected to teacher at $ip:$SERVER_PORT")
                
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
                handleConnectionLost()
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
        
        if (isRunning) {
            wifiDirectManager?.discoverTeachers()
            safeEmitCallback { callback?.onError("لا يمكن الاتصال بالمعلم - جاري البحث...") }
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
                    val lessonJson = json.optJSONObject("lesson")
                    if (lessonJson != null) {
                        processDirectLesson(lessonJson)
                    } else {
                        val lessonStr = json.optString("lesson", "")
                        if (lessonStr.isNotEmpty()) {
                            try {
                                val lessonJson = JSONObject(lessonStr)
                                if (isLessonForThisStudent(lessonJson)) {
                                    val lesson = gson.fromJson(lessonStr, com.edu.student.domain.model.Lesson::class.java)
                                    processLesson(lesson)
                                } else {
                                    Log.d(TAG, "Broadcasted lesson not for this student, skipping")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing lesson from string", e)
                            }
                        }
                    }
                }
                
                status == "REGISTERED" -> {
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
        if (!isConnected || !isRunning) {
            Log.w(TAG, "Cannot send data: not connected or not running")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val data = payload.toMutableMap()
                data["timestamp"] = System.currentTimeMillis().toString()
                
                val json = JSONObject(data).toString()
                writer?.println(json)
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Write error: ${e.message}")
                if (isRunning) {
                    handleConnectionLost()
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
        sendData(mapOf(
            "type" to "HOMEWORK_SUBMISSION"
        ) + data)
    }
    
    fun ping() {
        sendData(mapOf("type" to "PING"))
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
        if (isManualDisconnect || !isRunning) return
        
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(3000)
            if (!isManualDisconnect && isRunning && !isConnected) {
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
    }
    
    fun destroy() {
        Log.d(TAG, "Destroying TeacherClient")
        
        isManualDisconnect = true
        isRunning = false
        
        // إلغاء جميع المهام
        retryJob?.cancel()
        retryJob = null
        
        listenJob?.cancel()
        listenJob = null
        
        // إلغاء WiFi Direct
        try {
            wifiDirectManager?.unregister()
        } catch (e: Exception) { }
        wifiDirectManager = null
        
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
package com.edu.teacher

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.edu.common.BleDiscoveryManager
import com.edu.teacher.network.TeacherBeacon
import com.edu.teacher.utils.NetworkHelper
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.UUID

class TeacherServer(private val context: Context) {
    
    companion object {
        private const val TAG = "TeacherServer"
        const val SERVER_PORT = 9999
    }
    
    private val bleManager = BleDiscoveryManager(context)
    private val beacon = TeacherBeacon(context)
    private var serverSocket: ServerSocket? = null
    var isRunning = false
        private set
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val connectedClients = mutableSetOf<PrintWriter>()
    private val clientReaders = mutableMapOf<PrintWriter, BufferedReader>()
    private val clientSockets = mutableMapOf<PrintWriter, Socket>()
    
    var teacherId: String = ""
        private set
    
    var teacherIP: String = "192.168.43.1"
        private set
    
    var onStudentConnected: ((String, String, String, String) -> Unit)? = null
    var onStudentDisconnected: ((String) -> Unit)? = null
    var onLessonsRequested: ((String, String, String) -> Unit)? = null
    var onHomeworkSubmitted: ((Map<String, Any?>) -> Unit)? = null
    
    private val registeredStudents = mutableMapOf<String, StudentInfo>()
    
    data class StudentInfo(
        val id: String,
        val name: String,
        val grade: String,
        val section: String,
        var points: Int = 0
    )
    
    fun getRegisteredStudents(): List<StudentInfo> = registeredStudents.values.toList()
    
    @Suppress("UNUSED_PARAMETER")
    fun getStudentsBySubject(subjectId: String, grade: String, section: String): List<StudentInfo> {
        return registeredStudents.values.filter { student ->
            student.grade == grade && student.section == section
        }
    }
    
    fun start() {
        if (isRunning) return
        
        val prefs = context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
        teacherId = prefs.getString("teacher_id", "") ?: ""
        
        if (teacherId.isEmpty()) {
            teacherId = DataManager.getTeacherId(context) ?: ""
        }
        
        isRunning = true
        
        scope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", SERVER_PORT))
                }
                
                val localIp = NetworkHelper.getLocalIpAddress() ?: "192.168.43.1"
                
                Log.d(TAG, "======================================")
                Log.d(TAG, "Server bound to 0.0.0.0:$SERVER_PORT")
                Log.d(TAG, "Local IP: $localIp")
                Log.d(TAG, "Teacher ID: $teacherId")
                Log.d(TAG, "Server isRunning: $isRunning")
                Log.d(TAG, "======================================")
                
                teacherIP = localIp
                prefs.edit().putString("teacher_ip", localIp).apply()
                
                startBleAdvertising(localIp)
                
                beacon.startBeacon(SERVER_PORT)
                Log.d(TAG, "Teacher beacon started")
                
                while (isRunning) {
                    try {
                        val client = serverSocket!!.accept()
                        Log.d(TAG, "Student connected from: ${client.inetAddress.hostAddress}")
                        scope.launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Error accepting client", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}", e)
                isRunning = false
            }
        }
    }
    
    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        lateinit var reader: BufferedReader
        lateinit var writer: PrintWriter
        var currentStudentId = ""
        
        try {
            client.keepAlive = true
            client.tcpNoDelay = true
            client.soTimeout = 0
            client.receiveBufferSize = 128 * 1024
            client.sendBufferSize = 128 * 1024
            
            reader = BufferedReader(InputStreamReader(client.getInputStream(), "UTF-8"), 8192)
            writer = PrintWriter(client.getOutputStream(), true)
            
            connectedClients.add(writer)
            clientReaders[writer] = reader
            clientSockets[writer] = client
            
            Log.d(TAG, "Client connected: ${client.inetAddress.hostAddress}, total clients: ${connectedClients.size}")
            
            while (isRunning) {
                try {
                    val line = reader.readLine() ?: break
                    handleMessage(line, writer, currentStudentId) { id -> currentStudentId = id }
                } catch (e: java.io.IOException) {
                    Log.e(TAG, "Read error: ${e.message}", e)
                    break
                }
            }
            
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Client handler IO error: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error: ${e.message}", e)
        } finally {
            if (currentStudentId.isNotEmpty()) {
                registeredStudents.remove(currentStudentId)
            }
            connectedClients.remove(writer)
            clientReaders.remove(writer)
            clientSockets.remove(writer)
            try { 
                client.close() 
            } catch (e: Exception) { }
            
            val prefs = context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
            val tId = prefs.getString("teacher_id", "") ?: ""
            onStudentDisconnected?.invoke(tId)
        }
    }
    
    @Suppress("UNUSED_PARAMETER")
    private fun handleMessage(
        message: String,
        writer: PrintWriter,
        @Suppress("UNUSED_PARAMETER") currentStudentId: String,
        setStudentId: (String) -> Unit
    ) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type", "")
            
            when (type) {
                "REGISTER_STUDENT" -> {
                    val newStudentId = json.optString("studentId", "")
                    val studentName = json.optString("studentName", "")
                    val grade = json.optString("grade", "")
                    val section = json.optString("section", "")
                    setStudentId(newStudentId)
                    
                    registeredStudents[newStudentId] = StudentInfo(
                        id = newStudentId,
                        name = studentName,
                        grade = grade,
                        section = section,
                        points = 0
                    )
                    
                    onStudentConnected?.invoke(newStudentId, studentName, grade, section)
                    sendAck(writer, "REGISTERED", newStudentId)
                }
                
                "REQUEST_LESSONS" -> {
                    val studentGrade = json.optString("grade", "")
                    val studentSection = json.optString("section", "")
                    val studentTeacherId = json.optString("teacherId", "")
                    onLessonsRequested?.invoke(studentGrade, studentSection, studentTeacherId)
                    
                    val lessons = getLessonsForClass(studentGrade, studentSection)
                    sendLessons(writer, lessons)
                }
                
                "HOMEWORK_SUBMISSION" -> {
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "=== HOMEWORK RECEIVED FROM STUDENT ===")
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "Raw message length: ${message.length}")
                    Log.d(TAG, "Student ID: ${json.optString("studentId", "unknown")}")
                    Log.d(TAG, "Student Name: ${json.optString("studentName", "unknown")}")
                    Log.d(TAG, "Lesson ID: ${json.optString("lessonId", "unknown")}")
                    Log.d(TAG, "Lesson Title: ${json.optString("lessonTitle", "unknown")}")
                    Log.d(TAG, "Answers array: ${json.optJSONArray("answers")?.length() ?: 0} items")
                    Log.d(TAG, "========================================")
                    
                    try {
                        val submissionId = json.optString("submissionId", UUID.randomUUID().toString())
                        val studentId = json.optString("studentId", "")
                        val studentName = json.optString("studentName", "")
                        val studentGrade = json.optString("studentGrade", "")
                        val studentSection = json.optString("studentSection", "")
                        val avatar = json.optString("avatar", "")
                        val lessonId = json.optString("lessonId", "")
                        val lessonTitle = json.optString("lessonTitle", "")
                        val subjectId = json.optString("subjectId", "")
                        val subjectTitle = json.optString("subjectTitle", "")
                        val classId = json.optString("classId", "")
                        val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                        
                        val answersArray = json.optJSONArray("answers") ?: JSONArray()
                        val summaryJson = json.optJSONObject("summary") ?: JSONObject()
                        
                        val submission = JSONObject().apply {
                            put("submissionId", submissionId)
                            put("studentId", studentId)
                            put("studentName", studentName)
                            put("studentGrade", studentGrade)
                            put("studentSection", studentSection)
                            put("avatar", avatar)
                            put("lessonId", lessonId)
                            put("lessonTitle", lessonTitle)
                            put("subjectId", subjectId)
                            put("subjectTitle", subjectTitle)
                            put("classId", classId)
                            put("timestamp", timestamp)
                            put("answers", answersArray)
                            put("summary", summaryJson)
                            put("status", "pending")
                            put("teacherFeedback", "")
                            put("finalScore", 0)
                        }
                        
                        val teacherIdValue = teacherId.ifEmpty {
                            context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE).getString("teacher_id", "") ?: ""
                        }
                        
                        if (teacherIdValue.isNotEmpty()) {
                            DataManager.addSubmission(context, teacherIdValue, submission)
                            
                            if (classId.isNotEmpty() && lessonId.isNotEmpty()) {
                                DataManager.addSubmissionToLesson(context, teacherIdValue, classId, lessonId, submission)
                                Log.d(TAG, "Homework saved for lesson: $lessonId in class: $classId")
                            }
                        }
                        
                        registeredStudents[studentId]?.let { student ->
                            val earnedPoints = summaryJson.optInt("score", 0)
                            student.points += earnedPoints
                        }
                        
                        @Suppress("UNCHECKED_CAST")
                        val data = json.toMap()
                        onHomeworkSubmitted?.invoke(data)
                        
                        sendAck(writer, "HOMEWORK_RECEIVED", submissionId)
                        
                        Log.d(TAG, "Homework saved with ID: $submissionId")
                        Log.d(TAG, "=== HOMEWORK PROCESSING COMPLETE ===")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing homework submission: ${e.message}", e)
                        sendAck(writer, "ERROR", null)
                    }
                }
                
                "PING" -> {
                    sendAck(writer, "PONG", null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}", e)
        }
    }
    
    private fun getLessonsForClass(grade: String, section: String): List<JSONObject> {
        val lessons = mutableListOf<JSONObject>()
        val teacherIdValue = teacherId.ifEmpty {
            context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE).getString("teacher_id", "") ?: ""
        }
        if (teacherIdValue.isEmpty()) return lessons
        
        val subjects = DataManager.getSubjects(context, teacherIdValue)
        
        for (subject in subjects) {
            val subjectId = subject.optString("id")
            val subjectTitle = subject.optString("name", "")
            
            val classes = DataManager.getClasses(context, teacherIdValue, subjectId)
            
            for (cls in classes) {
                val classId = cls.optString("id")
                val classGrade = cls.optString("grade", "")
                val classSection = cls.optString("section", "")
                
                val gradeMatch = grade.isEmpty() || classGrade == grade
                val sectionMatch = section.isEmpty() || classSection == section
                
                if (gradeMatch && sectionMatch) {
                    val classLessons = DataManager.getLessons(context, teacherIdValue, classId)
                    
                    for (lesson in classLessons) {
                        lesson.put("subjectId", subjectId)
                        lesson.put("subjectTitle", subjectTitle)
                        lesson.put("grade", classGrade)
                        lesson.put("section", classSection)
                        lesson.put("classId", classId)
                        
                        lessons.add(lesson)
                    }
                }
            }
        }
        
        Log.d(TAG, "Found ${lessons.size} lessons for grade=$grade, section=$section")
        return lessons
    }
    
    private fun sendAck(writer: PrintWriter, status: String, id: String?) {
        try {
            val response = JSONObject().apply {
                put("status", status)
                id?.let { put("id", it) }
                put("teacherIP", teacherIP)
            }
            val jsonString = response.toString()
            if (isValidJson(jsonString)) {
                safeSendToClient(writer, jsonString)
            } else {
                Log.w(TAG, "Invalid JSON generated for ack")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending ack", e)
        }
    }
    
    private fun safeSendToClient(writer: PrintWriter, data: String): Boolean {
        return try {
            writer.println(data)
            true
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Client disconnected during send")
            removeDisconnectedClient(writer)
            false
        }
    }
    
    private fun removeDisconnectedClient(writer: PrintWriter) {
        synchronized(connectedClients) {
            connectedClients.remove(writer)
        }
        synchronized(clientReaders) {
            val reader = clientReaders.remove(writer)
            try {
                reader?.close()
            } catch (e: Exception) { }
        }
        synchronized(clientSockets) {
            val socket = clientSockets.remove(writer)
            try {
                socket?.close()
            } catch (e: Exception) { }
        }
    }

    private fun isValidJson(str: String): Boolean {
        return try {
            JSONObject(str)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun sendLessons(writer: PrintWriter, lessons: List<JSONObject>) {
        try {
            val teacherIdValue = teacherId.ifEmpty {
                context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE).getString("teacher_id", "") ?: ""
            }
            
            val lessonsArray = JSONArray()
            lessons.forEach { lesson ->
                try {
                    val cleanLesson = sanitizeJsonObject(lesson)
                    val classId = cleanLesson.optString("classId", "")
                    
                    val classInfo = DataManager.getClassById(context, teacherIdValue, classId)
                    cleanLesson.put("grade", classInfo?.optString("grade", "") ?: "")
                    cleanLesson.put("section", classInfo?.optString("section", "") ?: "")
                    
                    lessonsArray.put(cleanLesson)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing lesson", e)
                }
            }
            
            val response = JSONObject().apply {
                put("action", "LESSONS_DATA")
                put("lessons", lessonsArray)
            }
            
            val jsonString = response.toString()
            if (isValidJson(jsonString)) {
                safeSendToClient(writer, jsonString)
                Log.d(TAG, "Sent ${lessons.size} lessons")
            } else {
                Log.w(TAG, "Invalid JSON generated for lessons")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending lessons", e)
        }
    }

    private fun sanitizeJsonObject(obj: JSONObject): JSONObject {
        val clean = JSONObject()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            try {
                val value = obj.get(key)
                clean.put(key, value)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping invalid key: $key")
            }
        }
        return clean
    }
    
    fun broadcastLesson(lesson: JSONObject) {
        scope.launch(Dispatchers.IO) {
            try {
                val teacherIdValue = teacherId.ifEmpty {
                    context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
                        .getString("teacher_id", "") ?: ""
                }
                
                val lessonWithSubject = sanitizeJsonObject(lesson)
                val classId = lessonWithSubject.optString("classId", "")
                
                // جلب معلومات الفصل
                val classInfo = DataManager.getClassById(context, teacherIdValue, classId)
                val classGrade = classInfo?.optString("grade", "") ?: ""
                val classSection = classInfo?.optString("section", "") ?: ""
                
                Log.d(TAG, "broadcastLesson: classId=$classId, grade=$classGrade, section=$classSection")
                Log.d(TAG, "broadcastLesson: connectedClients size=${connectedClients.size}")
                
                // إذا لم يتم العثور على الفصل، استخدم القيم من الدرس نفسه
                val finalGrade = if (classGrade.isNotEmpty()) classGrade 
                    else lessonWithSubject.optString("grade", "")
                val finalSection = if (classSection.isNotEmpty()) classSection 
                    else lessonWithSubject.optString("section", "")
                
                // إضافة معلومات المادة
                val subject = DataManager.getSubjectForClass(context, teacherIdValue, classId)
                val subjectId = subject?.optString("id", "") ?: lessonWithSubject.optString("subjectId", "")
                val subjectTitle = subject?.optString("name", "") ?: lessonWithSubject.optString("subjectTitle", "")
                
                lessonWithSubject.put("subjectId", subjectId)
                lessonWithSubject.put("subjectTitle", subjectTitle)
                lessonWithSubject.put("grade", finalGrade)
                lessonWithSubject.put("section", finalSection)
                
                val message = JSONObject().apply {
                    put("action", "LESSON_BROADCAST")
                    put("lesson", lessonWithSubject)
                    put("targetGrade", finalGrade)
                    put("targetSection", finalSection)
                    put("classId", classId)
                    put("subjectId", subjectId)
                }
                
                val messageStr = message.toString()
                Log.d(TAG, "broadcastLesson: message=${messageStr.take(200)}")
                
                if (!isValidJson(messageStr)) {
                    Log.w(TAG, "Invalid JSON generated for lesson broadcast")
                    return@launch
                }
                
                val clientsToRemove = mutableListOf<PrintWriter>()
                
                connectedClients.forEach { writer ->
                    try {
                        writer.println(messageStr)
                        Log.d(TAG, "Sent lesson to client")
                    } catch (e: java.io.IOException) {
                        Log.e(TAG, "Write error broadcasting lesson: ${e.message}")
                        clientsToRemove.add(writer)
                    }
                }
                
                clientsToRemove.forEach { writer ->
                    connectedClients.remove(writer)
                    clientReaders.remove(writer)
                }
                
                Log.d(TAG, "Broadcasted lesson to ${connectedClients.size} clients")
                Log.d(TAG, "Lesson sent successfully to all connected students")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in broadcastLesson", e)
            }
        }
    }
    
    fun broadcastLessonsUpdate(lessons: List<JSONObject>) {
        scope.launch(Dispatchers.IO) {
            try {
                val teacherIdValue = teacherId.ifEmpty {
                    context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE).getString("teacher_id", "") ?: ""
                }
                
                val lessonsArray = JSONArray()
                lessons.forEach { lesson ->
                    try {
                        val cleanLesson = sanitizeJsonObject(lesson)
                        val classId = cleanLesson.optString("classId", "")
                        
                        val classInfo = DataManager.getClassById(context, teacherIdValue, classId)
                        cleanLesson.put("grade", classInfo?.optString("grade", "") ?: "")
                        cleanLesson.put("section", classInfo?.optString("section", "") ?: "")
                        
                        lessonsArray.put(cleanLesson)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing lesson for broadcast", e)
                    }
                }
                
                val message = JSONObject().apply {
                    put("action", "LESSONS_DATA")
                    put("lessons", lessonsArray)
                }
                
                val messageStr = message.toString()
                if (!isValidJson(messageStr)) {
                    Log.w(TAG, "Invalid JSON generated for lessons broadcast")
                    return@launch
                }
                
                val disconnectedWriters = mutableListOf<PrintWriter>()
                synchronized(connectedClients) {
                    connectedClients.forEach { writer ->
                        if (!safeSendToClient(writer, messageStr)) {
                            disconnectedWriters.add(writer)
                        }
                    }
                }
                disconnectedWriters.forEach { removeDisconnectedClient(it) }
                
                Log.d(TAG, "Broadcasted ${lessons.size} lessons to ${connectedClients.size} clients")
            } catch (e: Exception) {
                Log.e(TAG, "Error in broadcastLessonsUpdate", e)
            }
        }
    }
    
    fun getConnectedCount(): Int = connectedClients.size
    
    fun stop() {
        isRunning = false
        
        beacon.stopBeacon()
        
        scope.cancel()
        
        synchronized(connectedClients) {
            connectedClients.forEach { writer ->
                try {
                    writer.close()
                } catch (e: Exception) { }
            }
            connectedClients.clear()
        }
        
        synchronized(clientReaders) {
            clientReaders.forEach { (_, reader) ->
                try {
                    reader.close()
                } catch (e: Exception) { }
            }
            clientReaders.clear()
        }
        
        synchronized(clientSockets) {
            clientSockets.forEach { (_, socket) ->
                try {
                    socket.close()
                } catch (e: Exception) { }
            }
            clientSockets.clear()
        }
        
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server", e)
        }
        
        serverSocket = null
        
        onStudentConnected = null
        onStudentDisconnected = null
        onLessonsRequested = null
        onHomeworkSubmitted = null
        
        registeredStudents.clear()
        
        Log.d(TAG, "Server stopped")
        
        bleManager.stopAdvertising()
        bleManager.destroy()
    }
    
    private fun startBleAdvertising(teacherIp: String) {
        if (!bleManager.hasBluetoothPermissions()) {
            Log.w(TAG, "BLE permissions not granted, skipping BLE advertising")
            return
        }
        
        if (!bleManager.isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not enabled, skipping BLE advertising")
            return
        }
        
        val teacherName = getTeacherName()
        bleManager.startAdvertising(teacherIp, teacherName, SERVER_PORT)
        Log.d(TAG, "Started BLE advertising: $teacherIp:$teacherName")
    }
    
    private fun getTeacherName(): String {
        val prefs = context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
        return prefs.getString("teacher_name", "Teacher") ?: "Teacher"
    }
    
    private fun getHotspotIP(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager.connectionInfo
            @Suppress("DEPRECATION")
            val ipAddress = wifiInfo.ipAddress
            
            if (ipAddress != 0) {
                val ip = String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
                Log.d(TAG, "WiFi IP: $ip")
                
                if (ip.startsWith("192.168.43.")) {
                    return ip
                } else if (ip.startsWith("192.168.")) {
                    val parts = ip.split(".")
                    return "${parts[0]}.${parts[1]}.${parts[2]}.1"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting hotspot IP: ${e.message}")
        }
        return "192.168.43.1"
    }
    
    fun isServerAlive(): Boolean {
        return try {
            isRunning && serverSocket != null && !serverSocket!!.isClosed
        } catch (e: Exception) {
            false
        }
    }
    
    fun restart() {
        stop()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            start()
        }, 1000)
    }
    
    fun cleanupDisconnectedClients() {
        val toRemove = mutableListOf<PrintWriter>()
        
        synchronized(connectedClients) {
            connectedClients.forEach { writer ->
                try {
                    writer.checkError()
                } catch (e: Exception) {
                    toRemove.add(writer)
                }
            }
            
            toRemove.forEach { writer ->
                removeDisconnectedClient(writer)
            }
        }
        
        if (toRemove.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${toRemove.size} disconnected clients")
        }
    }
}

private fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = get(key)
    }
    return map
}

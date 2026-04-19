package com.edu.teacher

import android.content.Context
import android.util.Base64
import android.util.Log
import com.edu.teacher.network.TeacherBeacon
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class TeacherServer(private val context: Context) {
    
    companion object {
        private const val TAG = "TeacherServer"
        const val SERVER_PORT = 9999
    }

    data class LessonAttachments(
        val images: List<String> = emptyList(),
        val pdfData: String? = null,
        val pdfFileName: String? = null,
        val videoData: String? = null,
        val videoFileName: String? = null
    )
    
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
    
    var teacherIP: String = "192.168.0.101"
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
    
    fun start() {
        if (isRunning) {
            Log.w(TAG, "Server already running")
            return
        }
        
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
                
                val localIp = getLocalIp()
                teacherIP = localIp
                prefs.edit().putString("teacher_ip", localIp).apply()
                
                Log.d(TAG, "======================================")
                Log.d(TAG, "TeacherServer STARTED")
                Log.d(TAG, "Port: $SERVER_PORT")
                Log.d(TAG, "Teacher IP: $localIp")
                Log.d(TAG, "Teacher ID: $teacherId")
                Log.d(TAG, "======================================")
                
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
    
    private fun getLocalIp(): String {
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
                            Log.d(TAG, "Local IP found: $ip")
                            return ip
                        }
                    }
                }
            }
            "192.168.0.101"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP: ${e.message}")
            "192.168.0.101"
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
            
            Log.d(TAG, "Client connected: ${client.inetAddress.hostAddress}, total: ${connectedClients.size}")
            
            while (isRunning) {
                try {
                    val line = reader.readLine() ?: break
                    handleMessage(line, writer, currentStudentId) { id -> currentStudentId = id }
                } catch (e: java.io.IOException) {
                    Log.e(TAG, "Read error: ${e.message}", e)
                    break
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error: ${e.message}", e)
        } finally {
            if (currentStudentId.isNotEmpty()) {
                registeredStudents.remove(currentStudentId)
            }
            connectedClients.remove(writer)
            clientReaders.remove(writer)
            clientSockets.remove(writer)
            try { client.close() } catch (e: Exception) {}
            onStudentDisconnected?.invoke(teacherId)
        }
    }
    
    private fun handleMessage(
        message: String,
        writer: PrintWriter,
        currentStudentId: String,
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
                        section = section
                    )
                    
                    onStudentConnected?.invoke(newStudentId, studentName, grade, section)
                    sendAck(writer, "REGISTERED", newStudentId)
                }
                
                "REQUEST_LESSONS" -> {
                    val studentGrade = json.optString("grade", "")
                    val studentSection = json.optString("section", "")
                    onLessonsRequested?.invoke(studentGrade, studentSection, teacherId)
                    
                    val lessons = getLessonsForClass(studentGrade, studentSection)
                    sendLessons(writer, lessons)
                }
                
                "HOMEWORK_SUBMISSION" -> {
                    Log.d(TAG, "=== HOMEWORK RECEIVED ===")
                    Log.d(TAG, "Student: ${json.optString("studentName")}")
                    Log.d(TAG, "Lesson: ${json.optString("lessonTitle")}")
                    
                    try {
                        val submissionId = json.optString("submissionId", UUID.randomUUID().toString())
                        val studentId = json.optString("studentId", "")
                        val lessonId = json.optString("lessonId", "")
                        val classId = json.optString("classId", "")
                        
                        val submission = JSONObject().apply {
                            put("submissionId", submissionId)
                            put("studentId", studentId)
                            put("studentName", json.optString("studentName", ""))
                            put("studentGrade", json.optString("studentGrade", ""))
                            put("studentSection", json.optString("studentSection", ""))
                            put("lessonId", lessonId)
                            put("lessonTitle", json.optString("lessonTitle", ""))
                            put("classId", classId)
                            put("answers", json.optJSONArray("answers") ?: JSONArray())
                            put("summary", json.optJSONObject("summary") ?: JSONObject())
                            put("status", "pending")
                        }
                        
                        val teacherIdValue = teacherId.ifEmpty {
                            context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
                                .getString("teacher_id", "") ?: ""
                        }
                        
                        if (teacherIdValue.isNotEmpty()) {
                            DataManager.addSubmission(context, teacherIdValue, submission)
                            if (classId.isNotEmpty() && lessonId.isNotEmpty()) {
                                DataManager.addSubmissionToLesson(context, teacherIdValue, classId, lessonId, submission)
                            }
                            Log.d(TAG, "Homework saved: $submissionId")
                        }
                        
                        onHomeworkSubmitted?.invoke(json.toMap())
                        sendAck(writer, "HOMEWORK_RECEIVED", submissionId)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing homework: ${e.message}", e)
                        sendAck(writer, "ERROR", null)
                    }
                }
                
                "PING" -> {
                    val pong = JSONObject().apply {
                        put("type", "PONG")
                        put("timestamp", System.currentTimeMillis())
                    }
                    safeSendToClient(writer, pong.toString())
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
                val classGrade = cls.optString("grade", "")
                val classSection = cls.optString("section", "")
                
                if ((grade.isEmpty() || classGrade == grade) && (section.isEmpty() || classSection == section)) {
                    val classId = cls.optString("id")
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
            safeSendToClient(writer, response.toString())
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
        synchronized(connectedClients) { connectedClients.remove(writer) }
        synchronized(clientReaders) { clientReaders.remove(writer)?.close() }
        synchronized(clientSockets) { clientSockets.remove(writer)?.close() }
    }
    
    private fun sendLessons(writer: PrintWriter, lessons: List<JSONObject>) {
        try {
            val lessonsArray = JSONArray()
            lessons.forEach { lesson ->
                val lessonWithMedia = attachMediaToLesson(lesson)
                lessonsArray.put(lessonWithMedia)
            }
            
            val response = JSONObject().apply {
                put("action", "LESSONS_DATA")
                put("lessons", lessonsArray)
            }
            
            safeSendToClient(writer, response.toString())
            Log.d(TAG, "Sent ${lessons.size} lessons with media")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending lessons", e)
        }
    }
    
    fun broadcastLesson(lesson: JSONObject, attachments: LessonAttachments? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "========================================")
                Log.d(TAG, "=== BROADCASTING LESSON WITH MEDIA ===")
                Log.d(TAG, "========================================")
                Log.d(TAG, "Lesson content: ${lesson.optString("content").take(100)}")

                val lessonWithMedia = attachMediaToLesson(lesson)
                Log.d(TAG, "LessonWithMedia content: ${lessonWithMedia.optString("content").take(100)}")

                val message = JSONObject().apply {
                    put("action", "LESSON_BROADCAST")
                    put("lesson", lessonWithMedia)
                    
                    attachments?.let { att ->
                        val attachmentsArray = JSONArray()
                        
                        att.images.forEach { imageBase64 ->
                            attachmentsArray.put(JSONObject().apply {
                                put("type", "image")
                                put("data", imageBase64)
                            })
                        }
                        
                        att.pdfData?.let { pdfBase64 ->
                            attachmentsArray.put(JSONObject().apply {
                                put("type", "pdf")
                                put("fileName", att.pdfFileName ?: "document.pdf")
                                put("data", pdfBase64)
                            })
                        }
                        
                        att.videoData?.let { videoBase64 ->
                            attachmentsArray.put(JSONObject().apply {
                                put("type", "video")
                                put("fileName", att.videoFileName ?: "video.mp4")
                                put("data", videoBase64)
                            })
                        }
                        
                        put("attachments", attachmentsArray)
                    }
                }
                
                val messageStr = message.toString()
                Log.d(TAG, "Message size: ${messageStr.length / 1024} KB")
                
                val disconnectedWriters = mutableListOf<PrintWriter>()
                synchronized(connectedClients) {
                    connectedClients.forEach { writer ->
                        if (!safeSendToClient(writer, messageStr)) {
                            disconnectedWriters.add(writer)
                        } else {
                            Log.d(TAG, "Lesson sent successfully to client")
                        }
                    }
                }
                disconnectedWriters.forEach { removeDisconnectedClient(it) }
                
                Log.d(TAG, "Broadcast complete to ${connectedClients.size} clients")
            } catch (e: Exception) {
                Log.e(TAG, "Error in broadcastLesson: ${e.message}", e)
            }
        }
    }
    
    private fun attachMediaToLesson(lesson: JSONObject): JSONObject {
        val result = JSONObject(lesson.toString())
        
        val imagePath = lesson.optString("imagePath", "")
            .ifEmpty { lesson.optString("selectedImagePath", "") }
            .ifEmpty { lesson.optString("imageUri", "") }
            .ifEmpty { lesson.optString("image", "") }
        
        if (imagePath.isNotEmpty()) {
            Log.d(TAG, "Processing image: $imagePath")
            val base64 = fileToBase64(imagePath)
            if (base64 != null) {
                result.put("imageData", base64)
                result.put("imageType", getMimeType(imagePath))
                Log.d(TAG, "Image attached: ${base64.length / 1024} KB")
            } else {
                Log.w(TAG, "Failed to read image: $imagePath")
            }
        }
        
        val videoPath = lesson.optString("videoPath", "")
            .ifEmpty { lesson.optString("selectedVideoPath", "") }
            .ifEmpty { lesson.optString("videoUri", "") }
            .ifEmpty { lesson.optString("video", "") }
        
        if (videoPath.isNotEmpty()) {
            Log.d(TAG, "Processing video: $videoPath")
            val base64 = fileToBase64(videoPath)
            if (base64 != null) {
                result.put("videoData", base64)
                result.put("videoType", getMimeType(videoPath))
                Log.d(TAG, "Video attached: ${base64.length / 1024} KB")
            } else {
                Log.w(TAG, "Failed to read video: $videoPath")
            }
        }
        
        val pdfPath = lesson.optString("pdfPath", "")
            .ifEmpty { lesson.optString("selectedPdfPath", "") }
            .ifEmpty { lesson.optString("pdfUri", "") }
            .ifEmpty { lesson.optString("pdf", "") }
        
        if (pdfPath.isNotEmpty()) {
            Log.d(TAG, "Processing PDF: $pdfPath")
            val base64 = fileToBase64(pdfPath)
            if (base64 != null) {
                result.put("pdfData", base64)
                Log.d(TAG, "PDF attached: ${base64.length / 1024} KB")
            } else {
                Log.w(TAG, "Failed to read PDF: $pdfPath")
            }
        }
        
        return result
    }
    
    private fun fileToBase64(path: String): String? {
        return try {
            val file = File(path)
            
            val actualFile = when {
                file.exists() -> file
                !path.startsWith("/") -> File(context.filesDir, path)
                else -> null
            }
            
            if (actualFile == null || !actualFile.exists()) {
                Log.w(TAG, "File not found: $path")
                return null
            }
            
            if (actualFile.length() > 50 * 1024 * 1024) {
                Log.w(TAG, "File too large: ${actualFile.length() / 1024 / 1024} MB")
                return null
            }
            
            val bytes = actualFile.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting file to Base64: ${e.message}")
            null
        }
    }
    
    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
            path.endsWith(".png", true) -> "image/png"
            path.endsWith(".webp", true) -> "image/webp"
            path.endsWith(".mp4", true) -> "video/mp4"
            path.endsWith(".mkv", true) -> "video/mkv"
            path.endsWith(".pdf", true) -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
    
    fun broadcastLessonsUpdate(lessons: List<JSONObject>) {
        scope.launch(Dispatchers.IO) {
            try {
                val lessonsArray = JSONArray()
                lessons.forEach { lessonsArray.put(it) }
                
                val message = JSONObject().apply {
                    put("action", "LESSONS_DATA")
                    put("lessons", lessonsArray)
                }
                
                val messageStr = message.toString()
                
                connectedClients.forEach { writer ->
                    try {
                        writer.println(messageStr)
                    } catch (e: Exception) {
                        connectedClients.remove(writer)
                    }
                }
                
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
        
        connectedClients.forEach { try { it.close() } catch (e: Exception) {} }
        clientReaders.forEach { try { it.value.close() } catch (e: Exception) {} }
        clientSockets.forEach { try { it.value.close() } catch (e: Exception) {} }
        
        connectedClients.clear()
        clientReaders.clear()
        clientSockets.clear()
        
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
        
        registeredStudents.clear()
        Log.d(TAG, "Server stopped")
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
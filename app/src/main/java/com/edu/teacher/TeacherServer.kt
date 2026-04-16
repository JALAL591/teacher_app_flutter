package com.edu.teacher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class TeacherServer(private val context: Context) {
    
    companion object {
        private const val TAG = "TeacherServer"
        const val SERVER_PORT = 9999
    }
    
    private var serverSocket: ServerSocket? = null
    var isRunning = false
        private set
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val connectedClients = mutableSetOf<PrintWriter>()
    private val clientReaders = mutableMapOf<PrintWriter, BufferedReader>()
    
    var teacherId: String = ""
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
                serverSocket = ServerSocket(SERVER_PORT)
                Log.d(TAG, "Server started on port $SERVER_PORT - IP: 192.168.43.1")
                
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
            reader = BufferedReader(InputStreamReader(client.getInputStream()))
            writer = PrintWriter(client.getOutputStream(), true)
            
            connectedClients.add(writer)
            clientReaders[writer] = reader
            
            Log.d(TAG, "Client connected: ${client.inetAddress.hostAddress}")
            
            while (isRunning) {
                val line = reader.readLine() ?: break
                handleMessage(line, writer, currentStudentId) { id -> currentStudentId = id }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error: ${e.message}", e)
        } finally {
            if (currentStudentId.isNotEmpty()) {
                registeredStudents.remove(currentStudentId)
            }
            connectedClients.remove(writer)
            clientReaders.remove(writer)
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
                    @Suppress("UNCHECKED_CAST")
                    val data = json.toMap()
                    onHomeworkSubmitted?.invoke(data)
                    sendAck(writer, "HOMEWORK_RECEIVED", null)
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
        
        val prefs = context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
        
        val subjects = DataManager.getSubjects(context, teacherIdValue)
        for (subject in subjects) {
            val subjectId = subject.optString("id")
            val classes = DataManager.getClasses(context, teacherIdValue, subjectId)
            for (cls in classes) {
                val classId = cls.optString("id")
                val classGrade = cls.optString("grade", "")
                val classSection = cls.optString("section", "")
                
                val key = "lessons_${teacherIdValue}_$classId"
                val lessonsJson = prefs.getString(key, "[]") ?: "[]"
                
                try {
                    val jsonArray = JSONArray(lessonsJson)
                    for (i in 0 until jsonArray.length()) {
                        val lesson = jsonArray.getJSONObject(i)
                        
                        val lessonGrade = lesson.optString("grade", classGrade)
                        val lessonSection = lesson.optString("section", classSection)
                        
                        val gradeMatch = grade.isEmpty() || lessonGrade == grade || classGrade == grade
                        val sectionMatch = section.isEmpty() || lessonSection == section || classSection == section
                        
                        if (gradeMatch && sectionMatch) {
                            lesson.put("subjectId", subjectId)
                            lesson.put("subjectTitle", subject.optString("name", ""))
                            lessons.add(lesson)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading lessons for class $classId", e)
                }
            }
        }
        
        return lessons
    }
    
    private fun sendAck(writer: PrintWriter, status: String, id: String?) {
        val response = JSONObject().apply {
            put("status", status)
            id?.let { put("id", it) }
        }
        writer.println(response.toString())
    }
    
    private fun sendLessons(writer: PrintWriter, lessons: List<JSONObject>) {
        val lessonsArray = JSONArray()
        lessons.forEach { lessonsArray.put(it) }
        
        val response = JSONObject().apply {
            put("action", "LESSONS_DATA")
            put("lessons", lessonsArray)
        }
        writer.println(response.toString())
    }
    
    fun broadcastLesson(lesson: JSONObject) {
        scope.launch(Dispatchers.IO) {
            val teacherIdValue = teacherId.ifEmpty {
                context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE).getString("teacher_id", "") ?: ""
            }
            
            val lessonWithSubject = JSONObject(lesson.toString())
            val classId = lesson.optString("classId", "")
            
            val subjects = DataManager.getSubjects(context, teacherIdValue)
            for (subject in subjects) {
                val subjectId = subject.optString("id")
                val classes = DataManager.getClasses(context, teacherIdValue, subjectId)
                if (classes.any { it.optString("id") == classId }) {
                    lessonWithSubject.put("subjectId", subjectId)
                    lessonWithSubject.put("subjectTitle", subject.optString("name", ""))
                    break
                }
            }
            
            val message = JSONObject().apply {
                put("action", "LESSON_BROADCAST")
                put("lesson", lessonWithSubject)
            }
            
            val messageStr = message.toString()
            connectedClients.forEach { writer ->
                try {
                    writer.println(messageStr)
                    Log.d(TAG, "Broadcasted lesson to client: ${lesson.optString("title")}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting lesson", e)
                }
            }
            
            if (connectedClients.isEmpty()) {
                Log.d(TAG, "No students connected to broadcast to")
            }
        }
    }
    
    fun broadcastLessonsUpdate(lessons: List<JSONObject>) {
        scope.launch(Dispatchers.IO) {
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
                    Log.d(TAG, "Broadcasted ${lessons.size} lessons to clients")
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting lessons", e)
                }
            }
            
            if (connectedClients.isEmpty()) {
                Log.d(TAG, "No students connected to broadcast to")
            }
        }
    }
    
    fun getConnectedCount(): Int = connectedClients.size
    
    fun stop() {
        isRunning = false
        scope.cancel()
        
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server", e)
        }
        
        connectedClients.clear()
        clientReaders.clear()
        
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

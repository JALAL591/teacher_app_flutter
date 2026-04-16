package com.edu.teacher

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
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
        private const val SERVER_PORT = 9999
        private const val BROADCAST_PORT = 9998
    }
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    
    private val connectedClients = mutableSetOf<PrintWriter>()
    private val clientReaders = mutableMapOf<PrintWriter, BufferedReader>()
    
    var teacherId: String = ""
        private set
    
    var onStudentConnected: ((String, String) -> Unit)? = null
    var onStudentDisconnected: ((String) -> Unit)? = null
    var onLessonsRequested: ((String, String, String) -> Unit)? = null
    var onHomeworkSubmitted: ((Map<String, Any?>) -> Unit)? = null
    
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
                Log.d(TAG, "Server started on port $SERVER_PORT")
                
                startBroadcast()
                
                while (isRunning) {
                    try {
                        val client = serverSocket!!.accept()
                        scope.launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Error accepting client", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                isRunning = false
            }
        }
        
        scope.launch {
            startUDPListener()
        }
    }
    
    private fun startBroadcast() {
        scope.launch(Dispatchers.IO) {
            try {
                val socket = java.net.DatagramSocket()
                socket.broadcast = true
                
                while (isRunning) {
                    val broadcastData = JSONObject().apply {
                        put("type", "TEACHER_ANNOUNCE")
                        put("teacherId", teacherId)
                        put("port", SERVER_PORT)
                        put("version", "1.0")
                    }.toString()
                    
                    val packet = java.net.DatagramPacket(
                        broadcastData.toByteArray(),
                        broadcastData.length,
                        java.net.InetAddress.getByName("255.255.255.255"),
                        BROADCAST_PORT
                    )
                    
                    socket.send(packet)
                    delay(3000)
                }
                
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Broadcast error", e)
            }
        }
    }
    
    private suspend fun startUDPListener() {
        withContext(Dispatchers.IO) {
            try {
                val socket = java.net.DatagramSocket(BROADCAST_PORT)
                socket.broadcast = true
                val buffer = ByteArray(1024)
                
                while (isRunning) {
                    val packet = java.net.DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    
                    val data = String(packet.data, 0, packet.length)
                    Log.d(TAG, "UDP received: $data")
                }
                
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "UDP listener error", e)
            }
        }
    }
    
    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), true)
            
            connectedClients.add(writer)
            clientReaders[writer] = reader
            
            Log.d(TAG, "Client connected: ${client.inetAddress.hostAddress}")
            
            var clientId = ""
            
            while (isRunning) {
                val line = reader.readLine() ?: break
                handleMessage(line, writer, clientId) { id -> clientId = id }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error", e)
        } finally {
            connectedClients.removeAll { it == writer }
            clientReaders.remove(writer)
            try { client.close() } catch (e: Exception) { }
            
            val prefs = context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
            val teacherId = prefs.getString("teacher_id", "") ?: ""
            onStudentDisconnected?.invoke(teacherId)
        }
    }
    
    private fun handleMessage(
        message: String,
        writer: PrintWriter,
        clientId: String,
        setClientId: (String) -> Unit
    ) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type", "")
            
            when (type) {
                "REGISTER_STUDENT" -> {
                    val studentId = json.optString("studentId", "")
                    val studentName = json.optString("studentName", "")
                    setClientId(studentId)
                    onStudentConnected?.invoke(studentId, studentName)
                    sendAck(writer, "REGISTERED", studentId)
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
            Log.e(TAG, "Error parsing message", e)
        }
    }
    
    private fun getLessonsForClass(grade: String, section: String): List<JSONObject> {
        val lessons = mutableListOf<JSONObject>()
        val prefs = context.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
        val teacherId = prefs.getString("teacher_id", "") ?: return lessons
        
        val lessonsJson = prefs.getString("lessons_$teacherId", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(lessonsJson)
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
            Log.e(TAG, "Error loading lessons", e)
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
        val response = JSONObject().apply {
            put("action", "LESSONS_DATA")
            put("lessons", JSONArray(lessons.map { it.toString() }))
        }
        writer.println(response.toString())
    }
    
    fun broadcastLesson(lesson: JSONObject) {
        scope.launch(Dispatchers.IO) {
            val message = JSONObject().apply {
                put("action", "LESSON_BROADCAST")
                put("lesson", lesson)
            }
            
            connectedClients.forEach { writer ->
                try {
                    writer.println(message.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting lesson", e)
                }
            }
        }
    }
    
    fun broadcastLessonsUpdate(lessons: List<JSONObject>) {
        scope.launch(Dispatchers.IO) {
            val message = JSONObject().apply {
                put("action", "LESSONS_DATA")
                put("lessons", JSONArray(lessons.map { it.toString() }))
            }
            
            connectedClients.forEach { writer ->
                try {
                    writer.println(message.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting lessons", e)
                }
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
    keys().forEach { key ->
        map[key] = get(key)
    }
    return map
}

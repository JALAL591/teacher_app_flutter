package com.edu.student.services

import android.content.Context
import android.util.Log
import com.edu.student.data.preferences.StudentPreferences
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class TeacherClient(private val context: Context) {
    
    companion object {
        private const val TAG = "TeacherClient"
        private const val SERVER_PORT = 9999
        private const val BROADCAST_PORT = 9998
    }
    
    private val prefs = StudentPreferences(context)
    private val gson = Gson()
    
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var teacherIP: String = "192.168.43.1"
    private var isRunning = false
    private var listenerJob: Job? = null
    
    private val HOTSPOT_IPS = listOf(
        "192.168.43.1", "192.168.42.129", "172.20.10.1",
        "192.168.1.1", "192.168.0.1", "10.0.0.1", "127.0.0.1"
    )
    
    var isConnected: Boolean = false
        private set
    
    private var hasAutoRequested = false
    private var currentGrade: String? = null
    private var currentSection: String? = null
    
    private val listeners = mutableMapOf<String, MutableList<(Any?) -> Unit>>()
    private var retryJob: Job? = null
    private var isManualDisconnect = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    interface ClientCallback {
        fun onConnected(ip: String)
        fun onDisconnected()
        fun onLessonsReceived(lessons: List<com.edu.student.domain.model.Lesson>)
        fun onError(error: String)
    }
    
    private var callback: ClientCallback? = null
    
    fun setCallback(cb: ClientCallback) {
        callback = cb
    }
    
    fun setStudentInfo(grade: String, section: String) {
        currentGrade = grade
        currentSection = section
    }
    
    fun init() {
        isManualDisconnect = false
        isRunning = true
        
        scope.launch {
            startUDPDiscovery()
            teacherIP = discoverTeacherIP()
            if (teacherIP != "0.0.0.0") {
                connect()
            } else {
                scheduleRetry()
            }
        }
    }
    
    private suspend fun startUDPDiscovery() = withContext(Dispatchers.IO) {
        try {
            val socket = java.net.DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 5000
            
            val requestData = JSONObject().apply {
                put("type", "DISCOVER_TEACHER")
                put("version", "1.0")
            }.toString()
            
            val requestPacket = java.net.DatagramPacket(
                requestData.toByteArray(),
                requestData.length,
                java.net.InetAddress.getByName("255.255.255.255"),
                BROADCAST_PORT
            )
            
            socket.send(requestPacket)
            Log.d(TAG, "Discovery broadcast sent")
            
            val buffer = ByteArray(1024)
            val responsePacket = java.net.DatagramPacket(buffer, buffer.size)
            
            try {
                socket.receive(responsePacket)
                val response = String(responsePacket.data, 0, responsePacket.length)
                val json = JSONObject(response)
                
                if (json.optString("type") == "TEACHER_ANNOUNCE") {
                    val discoveredIP = responsePacket.address.hostAddress
                    teacherIP = discoveredIP ?: teacherIP
                    prefs.setTeacherIP(teacherIP)
                    Log.d(TAG, "Teacher discovered at: $teacherIP")
                }
            } catch (e: Exception) {
                Log.d(TAG, "No broadcast response, will try IPs")
            }
            
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "UDP discovery error", e)
        }
    }
    
    private suspend fun discoverTeacherIP(): String = withContext(Dispatchers.IO) {
        val savedIP = prefs.getTeacherIP()
        if (savedIP != null && pingServer(savedIP)) {
            return@withContext savedIP
        }
        
        for (ip in HOTSPOT_IPS) {
            if (pingServer(ip)) {
                prefs.setTeacherIP(ip)
                return@withContext ip
            }
        }
        
        return@withContext "192.168.43.1"
    }
    
    private suspend fun pingServer(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val testSocket = Socket()
            testSocket.connect(java.net.InetSocketAddress(ip, SERVER_PORT), 1000)
            testSocket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun connect() {
        scope.launch(Dispatchers.IO) {
            try {
                socket = Socket(teacherIP, SERVER_PORT)
                socket?.soTimeout = 0
                
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                writer = PrintWriter(socket!!.getOutputStream(), true)
                
                isConnected = true
                Log.d(TAG, "Connected to teacher at $teacherIP:$SERVER_PORT")
                
                if (!hasAutoRequested) {
                    hasAutoRequested = true
                    val grade = currentGrade ?: prefs.getStudent()?.grade ?: ""
                    val section = currentSection ?: prefs.getStudent()?.section ?: ""
                    
                    if (grade.isNotEmpty() && section.isNotEmpty()) {
                        Log.d(TAG, "Auto-requesting lessons for $grade-$section")
                        requestLessons(grade, section)
                    }
                }
                
                callback?.onConnected(teacherIP)
                emit("connected", teacherIP)
                
                listenForMessages()
                
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed to $teacherIP", e)
                isConnected = false
                callback?.onError(e.message ?: "Connection failed")
                emit("disconnected", null)
                scheduleRetry()
            }
        }
    }
    
    private suspend fun listenForMessages() = withContext(Dispatchers.IO) {
        try {
            while (isRunning && isConnected) {
                try {
                    val line = reader?.readLine() ?: break
                    if (line.isNotEmpty()) {
                        handleIncoming(line)
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Read error", e)
                        break
                    }
                }
            }
        } finally {
            if (isRunning && !isManualDisconnect) {
                isConnected = false
                callback?.onDisconnected()
                emit("disconnected", null)
                scheduleRetry()
            }
        }
    }
    
    private fun handleIncoming(data: String) {
        try {
            val json = JSONObject(data)
            val action = json.optString("action", "")
            val status = json.optString("status", "")
            
            when {
                action == "LESSONS_DATA" || action == "lessons_updated" -> {
                    val lessonsJson = json.optJSONArray("lessons")
                    if (lessonsJson != null) {
                        val teacherId = prefs.getAssignedTeacherId() ?: ""
                        prefs.saveCachedLessons(teacherId, lessonsJson.toString())
                        
                        val lessons = mutableListOf<com.edu.student.domain.model.Lesson>()
                        for (i in 0 until lessonsJson.length()) {
                            try {
                                val lessonJson = lessonsJson.getJSONObject(i)
                                val lesson = gson.fromJson(lessonJson.toString(), com.edu.student.domain.model.Lesson::class.java)
                                lessons.add(lesson)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing lesson", e)
                            }
                        }
                        
                        Log.d(TAG, "Received ${lessons.size} lessons from broadcast")
                        callback?.onLessonsReceived(lessons)
                        emit("lessons_updated", lessons)
                    }
                }
                action == "LESSON_BROADCAST" -> {
                    val lessonJson = json.optJSONObject("lesson")
                    if (lessonJson != null) {
                        val lesson = gson.fromJson(lessonJson.toString(), com.edu.student.domain.model.Lesson::class.java)
                        Log.d(TAG, "Received single lesson broadcast: ${lesson.title}")
                        emit("lesson_broadcast", lesson)
                    }
                }
                status == "REGISTERED" -> {
                    Log.d(TAG, "Registered with teacher")
                    emit("registered", json.optString("id"))
                }
                status == "PONG" -> {
                    Log.d(TAG, "Pong received")
                }
            }
            
            emit(action.ifEmpty { status }.ifEmpty { "data" }, json.toMap())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message", e)
        }
    }
    
    fun sendData(payload: Map<String, Any?>) {
        if (!isConnected) {
            Log.w(TAG, "Cannot send data: not connected")
            return
        }
        
        val data = payload.toMutableMap()
        data["timestamp"] = System.currentTimeMillis().toString()
        
        val json = JSONObject(data).toString()
        writer?.println(json)
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
        if (!listeners.containsKey(event)) {
            listeners[event] = mutableListOf()
        }
        listeners[event]?.add(callback)
        return { listeners[event]?.remove(callback) }
    }
    
    private fun emit(event: String, data: Any?) {
        listeners[event]?.forEach { it.invoke(data) }
    }
    
    private fun scheduleRetry() {
        if (isManualDisconnect) return
        
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(5000)
            if (!isManualDisconnect && isRunning) {
                hasAutoRequested = false
                startUDPDiscovery()
                teacherIP = discoverTeacherIP()
                if (teacherIP != "0.0.0.0") {
                    connect()
                } else {
                    scheduleRetry()
                }
            }
        }
    }
    
    fun destroy() {
        isManualDisconnect = true
        isRunning = false
        isConnected = false
        retryJob?.cancel()
        listenerJob?.cancel()
        
        try {
            reader?.close()
            writer?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing connection", e)
        }
        
        reader = null
        writer = null
        socket = null
    }
    
    fun manualReconnect() {
        hasAutoRequested = false
        isManualDisconnect = false
        destroy()
        scope.launch {
            delay(1000)
            init()
        }
    }
}

private fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    keys().forEach { key ->
        map[key] = get(key)
    }
    return map
}

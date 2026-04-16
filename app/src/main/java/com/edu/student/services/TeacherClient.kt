package com.edu.student.services

import android.content.Context
import android.util.Log
import com.edu.student.data.preferences.StudentPreferences
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class TeacherClient(private val context: Context) {
    
    private val prefs = StudentPreferences(context)
    private val gson = Gson()
    
    private var webSocket: WebSocket? = null
    private var teacherIP: String = "192.168.43.1"
    
    private val HOTSPOT_IPS = listOf(
        "192.168.43.1", "192.168.42.129", "172.20.10.1",
        "192.168.1.1", "192.168.0.1", "10.0.0.1", "127.0.0.1"
    )
    
    private val WS_PORT = 8080
    
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
        scope.launch {
            teacherIP = discoverTeacherIP()
            connect()
        }
    }
    
    private suspend fun discoverTeacherIP(): String = withContext(Dispatchers.IO) {
        val savedIP = prefs.getTeacherIP()
        if (savedIP != null && pingWS(savedIP)) {
            return@withContext savedIP
        }
        
        for (ip in HOTSPOT_IPS) {
            if (pingWS(ip)) {
                prefs.setTeacherIP(ip)
                return@withContext ip
            }
        }
        
        return@withContext "192.168.43.1"
    }
    
    private fun pingWS(ip: String): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url("ws://$ip:$WS_PORT")
                .build()
            
            var result = false
            client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.close(1000, "Ping successful")
                    result = true
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    result = false
                }
            })
            
            Thread.sleep(1500)
            result
        } catch (e: Exception) {
            false
        }
    }
    
    private fun connect() {
        val client = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("ws://$teacherIP:$WS_PORT")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d("TeacherClient", "Connected to $teacherIP")
                
                if (!hasAutoRequested) {
                    hasAutoRequested = true
                    val grade = currentGrade ?: prefs.getStudent()?.grade ?: ""
                    val section = currentSection ?: prefs.getStudent()?.section ?: ""
                    
                    if (grade.isNotEmpty() && section.isNotEmpty()) {
                        Log.d("TeacherClient", "Auto-requesting lessons for $grade-$section")
                        requestLessons(grade, section)
                    }
                }
                
                callback?.onConnected(teacherIP)
                emit("connected", teacherIP)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                isConnected = false
                callback?.onDisconnected()
                emit("disconnected", null)
                scheduleRetry()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("TeacherClient", "Connection failed", t)
                isConnected = false
                callback?.onError(t.message ?: "Connection failed")
                emit("disconnected", null)
                scheduleRetry()
            }
        })
    }
    
    private fun handleIncoming(data: String) {
        try {
            val json = gson.fromJson(data, Map::class.java)
            val action = json["action"] as? String
            
            when (action) {
                "LESSONS_DATA", "lessons_updated" -> {
                    @Suppress("UNCHECKED_CAST")
                    val lessonsJson = gson.toJson(json["lessons"])
                    val teacherId = prefs.getAssignedTeacherId() ?: ""
                    prefs.saveCachedLessons(teacherId, lessonsJson)
                    
                    val lessons = gson.fromJson(lessonsJson, Array<com.edu.student.domain.model.Lesson>::class.java)
                    Log.d("TeacherClient", "Received ${lessons.size} lessons from broadcast")
                    callback?.onLessonsReceived(lessons.toList())
                    emit("lessons_updated", lessons.toList())
                }
                "LESSON_BROADCAST" -> {
                    @Suppress("UNCHECKED_CAST")
                    val lessonJson = gson.toJson(json["lesson"])
                    val lesson = gson.fromJson(lessonJson, com.edu.student.domain.model.Lesson::class.java)
                    Log.d("TeacherClient", "Received single lesson broadcast: ${lesson.title}")
                    emit("lesson_broadcast", lesson)
                }
            }
            
            emit(action ?: "data", json)
        } catch (e: Exception) {
            Log.e("TeacherClient", "Error parsing message", e)
        }
    }
    
    fun sendData(payload: Map<String, Any?>) {
        val data = payload.toMutableMap()
        data["timestamp"] = System.currentTimeMillis().toString()
        
        val json = gson.toJson(data)
        webSocket?.send(json)
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
            delay(10000)
            if (!isManualDisconnect) {
                hasAutoRequested = false
                init()
            }
        }
    }
    
    fun destroy() {
        isManualDisconnect = true
        retryJob?.cancel()
        webSocket?.close(1000, "Closing")
        webSocket = null
        isConnected = false
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

package com.edu.student.services

import android.content.Context
import android.util.Log
import com.edu.student.data.preferences.StudentPreferences
import com.edu.student.utils.StudentWifiDirectManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket

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
    private var isRunning = false
    private var currentServerIP: String = HOTSPOT_IP
    private var listenerJob: Job? = null
    
    private var wifiDirectManager: StudentWifiDirectManager? = null
    
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
        
        initWifiDirect()
        connect()
    }
    
    private fun initWifiDirect() {
        wifiDirectManager = StudentWifiDirectManager(context)
        wifiDirectManager?.initialize()
        wifiDirectManager?.register()
        
        wifiDirectManager?.onWifiP2pEnabled = { enabled ->
            Log.d(TAG, "WiFi Direct enabled: $enabled")
        }
        
        wifiDirectManager?.onPeersAvailable = { peers ->
            Log.d(TAG, "Found ${peers.size} teachers nearby")
        }
        
        wifiDirectManager?.onConnectedToTeacher = { groupOwnerAddress ->
            Log.d(TAG, "Connected via WiFi Direct to: ${groupOwnerAddress.hostAddress}")
            currentServerIP = groupOwnerAddress.hostAddress ?: HOTSPOT_IP
            
            if (!isConnected) {
                connectToServer(groupOwnerAddress)
            }
        }
        
        wifiDirectManager?.onDisconnected = {
            Log.d(TAG, "Disconnected from WiFi Direct")
        }
    }
    
    private fun connect() {
        scope.launch(Dispatchers.IO) {
            connectToServer(HOTSPOT_IP)
        }
    }
    
    private fun connectToServer(serverAddress: String) {
        connectToServer(InetAddress.getByName(serverAddress))
    }
    
    private fun connectToServer(serverAddress: InetAddress) {
        scope.launch(Dispatchers.IO) {
            try {
                val ip = serverAddress.hostAddress ?: HOTSPOT_IP
                Log.d(TAG, "Connecting to teacher at $ip:$SERVER_PORT")
                
                socket = Socket(serverAddress, SERVER_PORT)
                socket?.soTimeout = 0
                
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                writer = PrintWriter(socket!!.getOutputStream(), true)
                
                isConnected = true
                currentServerIP = ip
                Log.d(TAG, "Connected to teacher at $ip:$SERVER_PORT")
                
                val student = prefs.getStudent()
                val grade = currentGrade ?: student?.grade ?: ""
                val section = currentSection ?: student?.section ?: ""
                
                if (student != null) {
                    registerStudent(student.id, student.name, grade, section)
                }
                
                if (!hasAutoRequested) {
                    hasAutoRequested = true
                    if (grade.isNotEmpty() && section.isNotEmpty()) {
                        Log.d(TAG, "Requesting lessons for $grade-$section")
                        requestLessons(grade, section)
                    }
                }
                
                runOnUiThread {
                    callback?.onConnected(ip)
                }
                emit("connected", ip)
                
                listenForMessages()
                
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}", e)
                isConnected = false
                
                wifiDirectManager?.discoverTeachers()
                
                runOnUiThread {
                    callback?.onError("لا يمكن الاتصال بالمعلم - جاري البحث...")
                }
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
                        Log.e(TAG, "Read error: ${e.message}", e)
                        break
                    }
                }
            }
        } finally {
            if (isRunning && !isManualDisconnect) {
                isConnected = false
                withContext(Dispatchers.Main) {
                    callback?.onDisconnected()
                }
                emit("disconnected", null)
                
                wifiDirectManager?.discoverTeachers()
                scheduleRetry()
            }
        }
    }
    
    private suspend fun handleIncoming(data: String) {
        try {
            val trimmedData = data.trim()
            if (trimmedData.isEmpty()) return
            
            val json: JSONObject
            try {
                json = JSONObject(trimmedData)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid JSON format: $trimmedData")
                return
            }
            
            val action = json.optString("action", "")
            val status = json.optString("status", "")
            
            when {
                action == "LESSONS_DATA" || action == "lessons_updated" -> {
                    val lessonsJson = json.optJSONArray("lessons")
                    if (lessonsJson != null && lessonsJson.length() > 0) {
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
                        
                        if (lessons.isNotEmpty()) {
                            Log.d(TAG, "Received ${lessons.size} lessons")
                            withContext(Dispatchers.Main) {
                                callback?.onLessonsReceived(lessons)
                            }
                            emit("lessons_updated", lessons)
                        }
                    }
                }
                action == "LESSON_BROADCAST" -> {
                    val lessonJson = json.optJSONObject("lesson")
                    if (lessonJson != null) {
                        val lesson = gson.fromJson(lessonJson.toString(), com.edu.student.domain.model.Lesson::class.java)
                        Log.d(TAG, "Received lesson broadcast: ${lesson.title}")
                        
                        val teacherId = prefs.getAssignedTeacherId() ?: ""
                        val cached = prefs.getCachedLessons(teacherId)
                        val lessons = if (cached != null) {
                            try {
                                val type = object : TypeToken<MutableList<com.edu.student.domain.model.Lesson>>() {}.type
                                gson.fromJson<MutableList<com.edu.student.domain.model.Lesson>>(cached, type)
                            } catch (e: Exception) { mutableListOf() }
                        } else { mutableListOf() }
                        
                        lessons.add(0, lesson)
                        prefs.saveCachedLessons(teacherId, gson.toJson(lessons))
                        
                        emit("lesson_broadcast", lesson)
                    } else {
                        val lessonStr = json.optString("lesson", "")
                        if (lessonStr.isNotEmpty()) {
                            try {
                                val lesson = gson.fromJson(lessonStr, com.edu.student.domain.model.Lesson::class.java)
                                Log.d(TAG, "Received lesson broadcast (string): ${lesson.title}")
                                
                                val teacherId = prefs.getAssignedTeacherId() ?: ""
                                val cached = prefs.getCachedLessons(teacherId)
                                val lessons = if (cached != null) {
                                    try {
                                        val type = object : TypeToken<MutableList<com.edu.student.domain.model.Lesson>>() {}.type
                                        gson.fromJson<MutableList<com.edu.student.domain.model.Lesson>>(cached, type)
                                    } catch (e: Exception) { mutableListOf() }
                                } else { mutableListOf() }
                                
                                lessons.add(0, lesson)
                                prefs.saveCachedLessons(teacherId, gson.toJson(lessons))
                                
                                emit("lesson_broadcast", lesson)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing lesson from string", e)
                            }
                        }
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
            Log.e(TAG, "Error parsing message: ${e.message}", e)
        }
    }
    
    private fun sendData(payload: Map<String, Any?>) {
        if (!isConnected) {
            Log.w(TAG, "Cannot send data: not connected")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            val data = payload.toMutableMap()
            data["timestamp"] = System.currentTimeMillis().toString()
            
            val json = JSONObject(data).toString()
            writer?.println(json)
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
        if (!listeners.containsKey(event)) {
            listeners[event] = mutableListOf()
        }
        listeners[event]?.add(callback)
        return { listeners[event]?.remove(callback) }
    }
    
    private fun emit(event: String, data: Any?) {
        scope.launch(Dispatchers.Main) {
            listeners[event]?.forEach { it.invoke(data) }
        }
    }
    
    private fun scheduleRetry() {
        if (isManualDisconnect) return
        
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(5000)
            if (!isManualDisconnect && isRunning && !isConnected) {
                Log.d(TAG, "Retrying connection...")
                hasAutoRequested = false
                connect()
            }
        }
    }
    
    private fun runOnUiThread(action: () -> Unit) {
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post(action)
        } catch (e: Exception) {
            action()
        }
    }
    
    fun destroy() {
        isManualDisconnect = true
        isRunning = false
        isConnected = false
        retryJob?.cancel()
        listenerJob?.cancel()
        
        wifiDirectManager?.unregister()
        
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
    
    fun startWifiDirectSearch() {
        wifiDirectManager?.discoverTeachers()
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

package com.edu.student.services

import android.content.Context
import android.util.Log
import com.edu.student.data.preferences.StudentPreferences
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncService(context: Context) {
    
    private val prefs = StudentPreferences(context)
    private val gson = Gson()
    private var baseIP = "192.168.43.1"
    private var basePort = 3000
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "SyncService"
    }
    
    interface SyncCallback {
        fun onSuccess(lessons: List<com.edu.student.domain.model.Lesson>)
        fun onError(error: String)
    }
    
    fun syncLessons(callback: SyncCallback) {
        val teacherId = prefs.getAssignedTeacherId() ?: "jalal"
        val student = prefs.getStudent()
        val grade = student?.grade ?: ""
        val section = student?.section ?: ""
        
        val url = HttpUrl.Builder()
            .scheme("http")
            .host(baseIP)
            .port(basePort)
            .addPathSegment("api")
            .addPathSegment("lessons")
            .addQueryParameter("tid", teacherId)
            .addQueryParameter("grade", grade)
            .addQueryParameter("section", section)
            .build()
        
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Sync failed: ${e.message}")
                callback.onError(e.message ?: "فشل الاتصال")
            }
            
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback.onError("HTTP ${response.code}")
                    return
                }
                
                try {
                    val body = response.body?.string() ?: "[]"
                    val lessons = parseLessons(body)
                    prefs.saveCachedLessons(teacherId, body)
                    callback.onSuccess(lessons)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error", e)
                    callback.onError("خطأ في تحليل البيانات")
                }
            }
        })
    }
    
    fun submitHomework(
        lessonId: String,
        lessonTitle: String,
        subjectName: String,
        homeworkImage: String?,
        homeworkNote: String,
        isCompleted: Boolean,
        callback: (Boolean) -> Unit
    ) {
        val student = prefs.getStudent() ?: return callback(false)
        val teacherId = prefs.getAssignedTeacherId() ?: return callback(false)
        
        val payload = mapOf(
            "studentId" to student.id,
            "studentName" to student.name,
            "name" to student.name,
            "avatar" to (student.avatar ?: ""),
            "grade" to student.grade,
            "section" to student.section,
            "points" to student.points,
            "teacherId" to teacherId,
            "lessonId" to lessonId,
            "lessonTitle" to lessonTitle,
            "subjectName" to subjectName,
            "homeworkImage" to homeworkImage,
            "homeworkNote" to homeworkNote,
            "isCompleted" to isCompleted,
            "timestamp" to System.currentTimeMillis().toString()
        )
        
        val url = HttpUrl.Builder()
            .scheme("http")
            .host(baseIP)
            .port(basePort)
            .addPathSegment("api")
            .addPathSegment("student")
            .addPathSegment("submit")
            .build()
        
        val body = gson.toJson(payload)
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Submit homework failed", e)
                callback(false)
            }
            
            override fun onResponse(call: Call, response: Response) {
                callback(response.isSuccessful)
            }
        })
    }
    
    private fun parseLessons(json: String): List<com.edu.student.domain.model.Lesson> {
        return try {
            val rawList = gson.fromJson(json, List::class.java)
            rawList.mapNotNull { item ->
                try {
                    gson.fromJson(gson.toJson(item), com.edu.student.domain.model.Lesson::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun setServerIP(ip: String, port: Int = 3000) {
        baseIP = ip
        basePort = port
    }
}

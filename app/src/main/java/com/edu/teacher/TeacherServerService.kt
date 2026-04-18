package com.edu.teacher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.edu.teacher.R

class TeacherServerService : Service() {
    
    companion object {
        private const val TAG = "TeacherServerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "teacher_server_channel"
        
        var isRunning = false
            private set
    }
    
    private lateinit var teacherServer: TeacherServer
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        teacherServer = TeacherServer(this)
        teacherServer.start()
        
        isRunning = true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        teacherServer.stop()
        isRunning = false
        super.onDestroy()
    }
    
    private fun createNotification(): Notification {
        createNotificationChannel()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("المعلم متصل")
            .setContentText("في انتظار اتصال الطلاب...")
            .setSmallIcon(R.drawable.ic_teacher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة المعلم",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "إبقاء الاتصال مع الطلاب نشطاً"
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
package com.edu.teacher.utils

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    
    const val CHANNEL_ID = "teacher_app_channel"
    const val CHANNEL_NAME = "إشعارات المعلم"
    const val NOTIFICATION_ID = 1001
    
    const val REQUEST_PERMISSIONS = 100
    const val REQUEST_NOTIFICATION = 101
    const val REQUEST_SETTINGS = 102
    const val REQUEST_BATTERY = 103
    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات المعلم للطلاب المتصلين"
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        return permissions.toTypedArray()
    }
    
    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun requestPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            getRequiredPermissions(),
            REQUEST_PERMISSIONS
        )
    }
    
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION
            )
        }
    }
    
    fun canWriteSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }
    
    fun requestWriteSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(activity)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_SETTINGS)
            }
        }
    }
    
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    
    fun requestIgnoreBatteryOptimization(activity: Activity) {
        if (!isIgnoringBatteryOptimizations(activity)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivityForResult(intent, REQUEST_BATTERY)
        }
    }
    
    fun checkAndRequestAll(activity: Activity) {
        val missingPermissions = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }
        
        if (!hasNotificationPermission(activity)) {
            requestNotificationPermission(activity)
        }
        
        if (!canWriteSettings(activity)) {
            requestWriteSettings(activity)
        }
        
        if (!isIgnoringBatteryOptimizations(activity)) {
            requestIgnoreBatteryOptimization(activity)
        }
    }
    
    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        onGranted: () -> Unit = {},
        onDenied: (List<String>) -> Unit = {}
    ) {
        if (requestCode == REQUEST_PERMISSIONS) {
            val deniedPermissions = mutableListOf<String>()
            val permissions = getRequiredPermissions()
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED && i < permissions.size) {
                    deniedPermissions.add(permissions[i])
                }
            }
            
            if (deniedPermissions.isEmpty()) {
                onGranted()
            } else {
                onDenied(deniedPermissions)
            }
        }
    }
}

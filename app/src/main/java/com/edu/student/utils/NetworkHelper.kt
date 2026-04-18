package com.edu.student.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

object NetworkHelper {
    
    private const val TAG = "NetworkHelper"
    
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            return try {
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.type == ConnectivityManager.TYPE_WIFI && networkInfo.isConnected
            } catch (e: Exception) {
                false
            }
        }
    }
    
    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return wifiManager.isWifiEnabled
    }
    
    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        val ip = address.hostAddress
                        if (ip != null && ip.startsWith("192.168.")) {
                            Log.d(TAG, "Found local IP: $ip")
                            return ip
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP: ${e.message}")
            null
        }
    }
    
    fun checkNetworkReadiness(context: Context): NetworkStatus {
        return when {
            !isWifiEnabled(context) -> NetworkStatus.WIFI_DISABLED
            !isWifiConnected(context) -> NetworkStatus.WIFI_NOT_CONNECTED
            !PermissionHelper.hasAllPermissions(context) -> NetworkStatus.PERMISSIONS_MISSING
            !PermissionHelper.isIgnoringBatteryOptimizations(context) -> NetworkStatus.BATTERY_OPTIMIZATION_ENABLED
            else -> NetworkStatus.READY
        }
    }
    
    enum class NetworkStatus {
        READY,
        WIFI_DISABLED,
        WIFI_NOT_CONNECTED,
        PERMISSIONS_MISSING,
        BATTERY_OPTIMIZATION_ENABLED
    }
}
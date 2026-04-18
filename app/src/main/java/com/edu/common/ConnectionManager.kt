package com.edu.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class ConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "ConnectionManager"
        const val SERVER_PORT = 9999
    }

    enum class ConnectionMode {
        BLE_DISCOVERY,
        WIFI_DIRECT,
        SAME_WIFI,
        HOTSPOT
    }

    interface ConnectionCallback {
        fun onModeDetected(mode: ConnectionMode)
        fun onTeacherFound(ip: String, mode: ConnectionMode)
        fun onConnectionFailed()
    }

    private val wifiDirectManager = WifiDirectManager(context)
    private val bleDiscoveryManager = BleDiscoveryManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val bleDiscoveryDone = AtomicBoolean(false)

    suspend fun discoverTeacher(callback: ConnectionCallback): String? = withContext(Dispatchers.IO) {
        if (bleDiscoveryManager.hasBluetoothPermissions() && bleDiscoveryManager.isBluetoothEnabled()) {
            callback.onModeDetected(ConnectionMode.BLE_DISCOVERY)
            val bleIp = discoverViaBle()
            if (bleIp != null) {
                callback.onTeacherFound(bleIp, ConnectionMode.BLE_DISCOVERY)
                return@withContext bleIp
            }
        } else {
            Log.d(TAG, "BLE not available, skipping to WiFi methods")
        }

        callback.onModeDetected(ConnectionMode.WIFI_DIRECT)
        val wifiDirectIp = discoverViaWifiDirect()
        if (wifiDirectIp != null) {
            callback.onTeacherFound(wifiDirectIp, ConnectionMode.WIFI_DIRECT)
            return@withContext wifiDirectIp
        }

        callback.onModeDetected(ConnectionMode.SAME_WIFI)
        val sameWifiIp = discoverViaSameWifi()
        if (sameWifiIp != null) {
            callback.onTeacherFound(sameWifiIp, ConnectionMode.SAME_WIFI)
            return@withContext sameWifiIp
        }

        callback.onModeDetected(ConnectionMode.HOTSPOT)
        val hotspotIp = discoverViaHotspot()
        if (hotspotIp != null) {
            callback.onTeacherFound(hotspotIp, ConnectionMode.HOTSPOT)
            return@withContext hotspotIp
        }

        return@withContext null
    }
    
    private suspend fun discoverViaBle(): String? = withContext(Dispatchers.IO) {
        if (!bleDiscoveryManager.hasBluetoothPermissions() || !bleDiscoveryManager.isBluetoothEnabled()) {
            Log.d(TAG, "BLE not available or permissions missing")
            return@withContext null
        }
        
        var resultIp: String? = null
        val foundCallback = object : BleDiscoveryManager.DiscoveryCallback {
            override fun onTeacherFound(ip: String, port: Int, deviceName: String) {
                if (bleDiscoveryDone.compareAndSet(false, true)) {
                    resultIp = ip
                }
            }
            
            override fun onScanStarted() {
                Log.d(TAG, "BLE scanning started")
            }
            
            override fun onScanStopped() {
                Log.d(TAG, "BLE scanning stopped")
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "BLE error: $error")
            }
        }
        
        try {
            bleDiscoveryManager.startScanningWithCallback(foundCallback)
            
            withTimeoutOrNull(15000) {
                while (resultIp == null && !bleDiscoveryDone.get()) {
                    delay(500)
                }
                resultIp
            }
        } catch (e: Exception) {
            Log.e(TAG, "BLE discovery error: ${e.message}")
            null
        } finally {
            bleDiscoveryManager.stopScanning()
        }
    }

    private suspend fun discoverViaWifiDirect(): String? = withContext(Dispatchers.IO) {
        if (!wifiDirectManager.initialize()) {
            Log.d(TAG, "WiFi Direct not supported")
            return@withContext null
        }

        if (!wifiDirectManager.hasPermissions()) {
            Log.d(TAG, "WiFi Direct permissions missing")
            return@withContext null
        }

        var resultIp: String? = null

        val connectionJob = launch {
            delay(10000)
        }

        wifiDirectManager.onConnected = { ip ->
            resultIp = ip
            connectionJob.cancel()
        }

        wifiDirectManager.register()
        wifiDirectManager.startDiscovery()

        try {
            withTimeoutOrNull(10000) {
                while (resultIp == null) {
                    delay(500)
                }
                resultIp
            }
        } finally {
            wifiDirectManager.stopDiscovery()
            wifiDirectManager.unregister()
        }
    }

    private suspend fun discoverViaSameWifi(): String? = withContext(Dispatchers.IO) {
        val localIp = getLocalIpAddress() ?: return@withContext null

        if (localIp.startsWith("192.168.1.") || localIp.startsWith("192.168.0.")) {
            return@withContext scanNetworkForTeacher(localIp)
        }

        if (localIp.startsWith("10.0.") || localIp.startsWith("172.16.")) {
            return@withContext scanNetworkForTeacher(localIp)
        }

        return@withContext null
    }

    private suspend fun discoverViaHotspot(): String? = withContext(Dispatchers.IO) {
        val localIp = getLocalIpAddress() ?: return@withContext null

        return@withContext when {
            localIp.startsWith("192.168.43.") -> scanNetworkForTeacher(localIp)
            localIp.startsWith("192.168.49.") -> "192.168.49.1"
            else -> null
        }
    }

    private suspend fun scanNetworkForTeacher(localIp: String): String? = withContext(Dispatchers.IO) {
        val parts = localIp.split(".")
        if (parts.size != 4) return@withContext null

        val networkPrefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        Log.d(TAG, "Scanning network: $networkPrefix.xxx")

        val jobs = mutableListOf<Deferred<String?>>()

        for (i in 1..254) {
            val testIp = "$networkPrefix.$i"
            if (testIp == localIp) continue

            jobs.add(async {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(testIp, SERVER_PORT), 200)
                    socket.close()
                    testIp
                } catch (e: Exception) {
                    null
                }
            })

            if (i % 50 == 0) {
                for (job in jobs) {
                    val result = job.await()
                    if (result != null) {
                        jobs.forEach { it.cancel() }
                        return@withContext result
                    }
                }
                jobs.clear()
            }
        }

        for (job in jobs) {
            val result = job.await()
            if (result != null) {
                jobs.forEach { it.cancel() }
                return@withContext result
            }
        }

        return@withContext null
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        val ip = address.hostAddress
                        if (ip != null) {
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

    fun getCurrentConnectionMode(): ConnectionMode {
        val localIp = getLocalIpAddress() ?: return ConnectionMode.HOTSPOT

        return when {
            localIp.startsWith("192.168.49.") -> ConnectionMode.WIFI_DIRECT
            localIp.startsWith("192.168.43.") -> ConnectionMode.HOTSPOT
            localIp.startsWith("192.168.1.") || localIp.startsWith("192.168.0.") -> ConnectionMode.SAME_WIFI
            else -> ConnectionMode.SAME_WIFI
        }
    }

    fun isOnSameNetwork(teacherIp: String): Boolean {
        val localIp = getLocalIpAddress() ?: return false

        val localParts = localIp.split(".")
        val teacherParts = teacherIp.split(".")

        if (localParts.size != 4 || teacherParts.size != 4) return false

        return localParts[0] == teacherParts[0] && localParts[1] == teacherParts[1] && localParts[2] == teacherParts[2]
    }

    fun cleanup() {
        bleDiscoveryManager.destroy()
        scope.cancel()
        wifiDirectManager.unregister()
    }
}
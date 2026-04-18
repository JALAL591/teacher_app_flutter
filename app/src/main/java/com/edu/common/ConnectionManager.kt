package com.edu.common

import android.content.Context
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
        // ١. محاولة BLE أولاً
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

        // ٢. محاولة WiFi Direct
        callback.onModeDetected(ConnectionMode.WIFI_DIRECT)
        val wifiDirectIp = discoverViaWifiDirect()
        if (wifiDirectIp != null) {
            callback.onTeacherFound(wifiDirectIp, ConnectionMode.WIFI_DIRECT)
            return@withContext wifiDirectIp
        }

        // ٣. محاولة Same WiFi
        callback.onModeDetected(ConnectionMode.SAME_WIFI)
        val sameWifiIp = discoverViaSameWifi()
        if (sameWifiIp != null) {
            callback.onTeacherFound(sameWifiIp, ConnectionMode.SAME_WIFI)
            return@withContext sameWifiIp
        }

        // ٤. محاولة Hotspot
        callback.onModeDetected(ConnectionMode.HOTSPOT)
        val hotspotIp = discoverViaHotspot()
        if (hotspotIp != null) {
            callback.onTeacherFound(hotspotIp, ConnectionMode.HOTSPOT)
            return@withContext hotspotIp
        }

        callback.onConnectionFailed()
        return@withContext null
    }
    
    private suspend fun discoverViaBle(): String? = withContext(Dispatchers.IO) {
        if (!bleDiscoveryManager.hasBluetoothPermissions() || !bleDiscoveryManager.isBluetoothEnabled()) {
            Log.d(TAG, "BLE not available or permissions missing")
            return@withContext null
        }
        
        var resultIp: String? = null
        var resultPort: Int = SERVER_PORT
        val foundCallback = object : BleDiscoveryManager.DiscoveryCallback {
            override fun onTeacherFound(ip: String, port: Int, deviceName: String) {
                if (bleDiscoveryDone.compareAndSet(false, true)) {
                    resultIp = ip
                    resultPort = port
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
            
            val discoveredIp: String? = withTimeoutOrNull(15000) {
                while (resultIp == null && !bleDiscoveryDone.get()) {
                    delay(500)
                }
                resultIp
            }
            
            if (discoveredIp != null) {
                Log.d(TAG, "BLE discovery successful: $discoveredIp:$resultPort")
                
                val connected = tryConnectToTeacher(discoveredIp, resultPort)
                if (connected) {
                    Log.d(TAG, "BLE: Socket connection established")
                    return@withContext discoveredIp
                } else {
                    Log.e(TAG, "BLE: Failed to establish Socket connection")
                    return@withContext null
                }
            } else {
                Log.w(TAG, "BLE discovery timeout or failed")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "BLE discovery error: ${e.message}")
            return@withContext null
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
        var connectionJob: Job? = null

        wifiDirectManager.onConnected = { ip ->
            Log.d(TAG, "WiFi Direct connected to: $ip")
            resultIp = ip
            connectionJob?.cancel()
        }

        wifiDirectManager.register()
        
        connectionJob = launch {
            delay(15000)
            if (resultIp == null) {
                Log.w(TAG, "WiFi Direct connection timeout")
            }
        }

        wifiDirectManager.startDiscovery()

        try {
            val discoveredIp: String? = withTimeoutOrNull(15000) {
                while (resultIp == null) {
                    delay(500)
                }
                resultIp
            }
            
            if (discoveredIp != null) {
                Log.d(TAG, "WiFi Direct discovery successful: $discoveredIp")
                
                val connected = tryConnectToTeacher(discoveredIp, SERVER_PORT)
                if (connected) {
                    Log.d(TAG, "WiFi Direct: Socket connection established")
                    return@withContext discoveredIp
                } else {
                    Log.e(TAG, "WiFi Direct: Failed to establish Socket connection")
                    return@withContext null
                }
            } else {
                Log.w(TAG, "WiFi Direct discovery timeout")
                return@withContext null
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
            localIp.startsWith("192.168.43.") -> {
                val ip = scanNetworkForTeacher(localIp)
                if (ip != null) ip else "192.168.43.1"
            }
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
                        
                        val connected = tryConnectToTeacher(result, SERVER_PORT)
                        if (connected) {
                            return@withContext result
                        }
                    }
                }
                jobs.clear()
            }
        }

        for (job in jobs) {
            val result = job.await()
            if (result != null) {
                jobs.forEach { it.cancel() }
                
                val connected = tryConnectToTeacher(result, SERVER_PORT)
                if (connected) {
                    return@withContext result
                }
            }
        }

        return@withContext null
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                            val ip = address.hostAddress
                            if (ip != null && !ip.startsWith("127.")) {
                                return ip
                            }
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

        return localParts[0] == teacherParts[0] && 
               localParts[1] == teacherParts[1] && 
               localParts[2] == teacherParts[2]
    }

    private fun tryConnectToTeacher(ip: String, port: Int, timeout: Int = 3000): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeout)
            socket.close()
            Log.d(TAG, "Successfully connected to teacher at $ip:$port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to teacher at $ip:$port: ${e.message}")
            false
        }
    }

    fun cleanup() {
        bleDiscoveryManager.destroy()
        scope.cancel()
        wifiDirectManager.unregister()
    }
}
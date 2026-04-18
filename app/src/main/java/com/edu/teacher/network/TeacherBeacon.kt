package com.edu.teacher.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface

class TeacherBeacon(private val context: Context) {

    companion object {
        private const val TAG = "TeacherBeacon"
        private const val DISCOVERY_PORT = 8888
        private const val BROADCAST_MESSAGE = "TEACHER_DISCOVERY_REQUEST"
        private const val RESPONSE_PREFIX = "TEACHER_HERE:"
    }

    private var isRunning = false
    private var socket: DatagramSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var beaconJob: Job? = null

    fun startBeacon(port: Int = 9999) {
        if (isRunning) {
            Log.d(TAG, "Beacon already running")
            return
        }
        
        isRunning = true
        beaconJob = scope.launch {
            try {
                socket = DatagramSocket(DISCOVERY_PORT)
                socket?.broadcast = true
                val buffer = ByteArray(1024)
                
                Log.d(TAG, "Beacon started on port $DISCOVERY_PORT")
                
                while (isRunning) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket?.receive(packet)
                        
                        val message = String(packet.data, 0, packet.length).trim()
                        
                        if (message == BROADCAST_MESSAGE) {
                            val myIp = getLocalIpAddress()
                            val response = "$RESPONSE_PREFIX$myIp:$port"
                            val responseBytes = response.toByteArray()
                            
                            val responsePacket = DatagramPacket(
                                responseBytes,
                                responseBytes.size,
                                packet.address,
                                packet.port
                            )
                            
                            socket?.send(responsePacket)
                            Log.d(TAG, "Responded to student at ${packet.address.hostAddress} with IP: $myIp:$port")
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error in beacon loop: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Beacon error: ${e.message}", e)
            }
        }
    }

    fun stopBeacon() {
        Log.d(TAG, "Stopping beacon...")
        isRunning = false
        beaconJob?.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
    }

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            val ip = address.hostAddress
                            if (ip != null && !ip.startsWith("127.")) {
                                return ip
                            }
                        }
                    }
                }
            }
            "192.168.43.1"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP: ${e.message}")
            "192.168.43.1"
        }
    }

    fun isBeaconRunning(): Boolean = isRunning
}
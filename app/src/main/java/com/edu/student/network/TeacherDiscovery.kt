package com.edu.student.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

class TeacherDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "TeacherDiscovery"
        private const val DISCOVERY_PORT = 8888
        private const val BROADCAST_MESSAGE = "TEACHER_DISCOVERY_REQUEST"
        private const val RESPONSE_PREFIX = "TEACHER_HERE:"
        private const val TIMEOUT_MS = 3000
    }

    interface DiscoveryListener {
        fun onTeacherFound(ip: String)
        fun onDiscoveryFailed()
    }

    suspend fun discoverTeacher(listener: DiscoveryListener) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting teacher discovery...")
            
            val foundIp = sendDiscoveryBroadcast()
            
            if (foundIp != null) {
                Log.d(TAG, "Teacher found at: $foundIp")
                withContext(Dispatchers.Main) {
                    listener.onTeacherFound(foundIp)
                }
            } else {
                Log.w(TAG, "No teacher found on network")
                withContext(Dispatchers.Main) {
                    listener.onDiscoveryFailed()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Discovery error: ${e.message}", e)
            withContext(Dispatchers.Main) {
                listener.onDiscoveryFailed()
            }
        }
    }

    private fun sendDiscoveryBroadcast(): String? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = TIMEOUT_MS

            val broadcastAddresses = getBroadcastAddresses()
            
            val message = BROADCAST_MESSAGE.toByteArray()
            
            for (broadcastAddr in broadcastAddresses) {
                try {
                    Log.d(TAG, "Sending broadcast to: $broadcastAddr")
                    val packet = DatagramPacket(
                        message, 
                        message.size,
                        InetAddress.getByName(broadcastAddr), 
                        DISCOVERY_PORT
                    )
                    socket.send(packet)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send to $broadcastAddr: ${e.message}")
                }
            }

            val buffer = ByteArray(1024)
            val responsePacket = DatagramPacket(buffer, buffer.size)

            try {
                socket.receive(responsePacket)
                val response = String(responsePacket.data, 0, responsePacket.length)
                Log.d(TAG, "Received response: $response from ${responsePacket.address.hostAddress}")
                
                return when {
                    response.startsWith(RESPONSE_PREFIX) -> {
                        response.removePrefix(RESPONSE_PREFIX).trim()
                    }
                    else -> {
                        responsePacket.address.hostAddress
                    }
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "No response received (timeout)")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast error: ${e.message}", e)
            null
        } finally {
            socket?.close()
        }
    }

    private fun getBroadcastAddresses(): List<String> {
        val addresses = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val interfaceAddresses = networkInterface.interfaceAddresses
                    for (interfaceAddress in interfaceAddresses) {
                        val address = interfaceAddress.address
                        if (address is Inet4Address) {
                            val broadcast = interfaceAddress.broadcast
                            if (broadcast != null) {
                                addresses.add(broadcast.hostAddress ?: continue)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting broadcast addresses: ${e.message}")
        }
        
        if (addresses.isEmpty()) {
            addresses.add("255.255.255.255")
        }
        
        Log.d(TAG, "Broadcast addresses: $addresses")
        return addresses.distinct()
    }

    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
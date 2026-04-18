package com.edu.common

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat

class WifiDirectManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WifiDirectManager"
    }
    
    private val wifiP2pManager: WifiP2pManager? = 
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    
    private var channel: WifiP2pManager.Channel? = null
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
    
    private var isWifiP2pEnabled = false
    private var isConnected = false
    private var groupOwnerAddress: String? = null
    
    var onWifiP2pEnabled: ((Boolean) -> Unit)? = null
    var onPeersAvailable: ((List<WifiP2pDevice>) -> Unit)? = null
    var onConnected: ((String) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    isWifiP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    onWifiP2pEnabled?.invoke(isWifiP2pEnabled)
                    Log.d(TAG, "WiFi Direct enabled: $isWifiP2pEnabled")
                }
                
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    wifiP2pManager?.requestPeers(channel) { peers: WifiP2pDeviceList? ->
                        val deviceList = peers?.deviceList?.toList() ?: emptyList()
                        Log.d(TAG, "Found ${deviceList.size} devices")
                        onPeersAvailable?.invoke(deviceList)
                    }
                }
                
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val connectionInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, WifiP2pInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                    }
                    
                    if (connectionInfo?.groupFormed == true) {
                        isConnected = true
                        connectionInfo.groupOwnerAddress?.hostAddress?.let { ip ->
                            groupOwnerAddress = ip
                            Log.d(TAG, "Connected! Group Owner IP: $ip")
                            onConnected?.invoke(ip)
                        }
                    } else {
                        isConnected = false
                        groupOwnerAddress = null
                        Log.d(TAG, "Disconnected")
                        onDisconnected?.invoke()
                    }
                }
            }
        }
    }
    
    fun initialize(): Boolean {
        if (wifiP2pManager == null) {
            Log.e(TAG, "WiFi Direct not supported")
            return false
        }
        channel = wifiP2pManager.initialize(context, context.mainLooper, null)
        return channel != null
    }
    
    fun register() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
    }
    
    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }
    
    fun startDiscovery() {
        if (!isWifiP2pEnabled) {
            Log.w(TAG, "WiFi Direct not enabled")
            return
        }
        
        if (!hasPermissions()) {
            Log.w(TAG, "Missing permissions for WiFi Direct")
            return
        }
        
        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery started")
                
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    requestPeersAndConnect()
                }, 3000)
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Discovery failed: $reason")
            }
        })
    }
    
    private fun requestPeersAndConnect() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        wifiP2pManager?.requestPeers(channel) { peers ->
            val teacherDevices = peers?.deviceList?.filter { 
                it.deviceName.contains("Teacher", ignoreCase = true) ||
                it.deviceName.contains("معلم", ignoreCase = true) ||
                it.isGroupOwner
            } ?: emptyList()
            
            if (teacherDevices.isNotEmpty()) {
                val teacher = teacherDevices.first()
                Log.d(TAG, "Found teacher: ${teacher.deviceName}, connecting...")
                connectToDevice(teacher)
            } else if (peers?.deviceList?.isNotEmpty() == true) {
                val firstDevice = peers.deviceList.first()
                Log.d(TAG, "Connecting to first available device: ${firstDevice.deviceName}")
                connectToDevice(firstDevice)
            } else {
                Log.d(TAG, "No devices found in peers list")
            }
        }
    }
    
    fun stopDiscovery() {
        wifiP2pManager?.stopPeerDiscovery(channel, null)
    }
    
    fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = 0
        }
        
        wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated to ${device.deviceName}")
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connection failed: $reason")
            }
        })
    }
    
    fun createGroup() {
        wifiP2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Group created - I am the teacher")
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Group creation failed: $reason")
            }
        })
    }
    
    fun removeGroup() {
        wifiP2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Group removed")
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Group removal failed: $reason")
            }
        })
    }
    
    fun getGroupOwnerAddress(): String? = groupOwnerAddress
    fun isConnected(): Boolean = isConnected
    fun isEnabled(): Boolean = isWifiP2pEnabled
    
    fun hasPermissions(): Boolean {
        val hasLocation = ActivityCompat.checkSelfPermission(context, 
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        val hasNearbyWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(context, 
                Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        return hasLocation && hasNearbyWifi
    }
    
    @Suppress("UNUSED_PARAMETER")
    fun setReceiverListener(listener: Any?) {
        // Not needed in new implementation - using internal receiver
    }
    
    fun connect(device: WifiP2pDevice) {
        connectToDevice(device)
    }
    
    fun discoverPeers() {
        startDiscovery()
    }
    
    fun requestConnectionInfo(callback: (WifiP2pInfo?) -> Unit) {
        wifiP2pManager?.requestConnectionInfo(channel) { info ->
            callback(info)
        }
    }
    
    @Suppress("UNUSED_PARAMETER")
    fun requestConnectionInfo() {
        wifiP2pManager?.requestConnectionInfo(channel) { }
    }
}
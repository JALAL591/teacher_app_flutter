package com.edu.student.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import java.net.InetAddress

class StudentWifiDirectManager(private val context: Context) {
    
    companion object {
        private const val TAG = "StudentWifiDirect"
        const val PORT = 9999
    }
    
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var isWifiP2pEnabled = false
    private var discovering = false
    private var connectedToTeacher = false
    private var groupOwnerAddress: InetAddress? = null
    
    var onWifiP2pEnabled: ((Boolean) -> Unit)? = null
    var onPeersAvailable: ((List<WifiP2pDevice>) -> Unit)? = null
    var onConnectedToTeacher: ((InetAddress) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onDiscoveryStarted: (() -> Unit)? = null
    var onDiscoveryStopped: (() -> Unit)? = null
    
    private val receiver = StudentWifiDirectBroadcastReceiver()
    
    fun initialize() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            Log.w(TAG, "WiFi Direct not supported")
            return
        }
        
        manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = manager?.initialize(context, context.mainLooper, null)
        Log.d(TAG, "Student WiFi Direct initialized")
    }
    
    fun register() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        try {
            context.registerReceiver(receiver, intentFilter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver", e)
        }
    }
    
    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver", e)
        }
    }
    
    fun setReceiverListener(listener: StudentWifiP2pBroadcastListener) {
        receiver.listener = listener
        receiver.studentWifiDirectManager = this
    }
    
    @SuppressLint("MissingPermission")
    fun discoverTeachers() {
        if (!hasPermissions()) {
            Log.w(TAG, "Missing permissions")
            return
        }
        
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery started")
                discovering = true
                onDiscoveryStarted?.invoke()
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Discovery failed: $reason")
                discovering = false
            }
        })
    }
    
    fun stopDiscovery() {
        manager?.stopPeerDiscovery(channel, null)
        discovering = false
        onDiscoveryStopped?.invoke()
    }
    
    @SuppressLint("MissingPermission")
    fun connectToTeacher(device: WifiP2pDevice) {
        if (!hasPermissions()) return
        
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connecting to teacher: ${device.deviceName}")
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connection failed: $reason")
            }
        })
    }
    
    fun disconnect() {
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Disconnected")
                connectedToTeacher = false
                groupOwnerAddress = null
                onDisconnected?.invoke()
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Disconnect failed: $reason")
            }
        })
    }
    
    @SuppressLint("MissingPermission")
    fun requestPeers() {
        if (!hasPermissions()) return
        
        manager?.requestPeers(channel) { peers ->
            val teacherDevices = peers.deviceList.filter { 
                it.isGroupOwner || it.deviceName.contains("Teacher", ignoreCase = true)
            }
            if (teacherDevices.isNotEmpty()) {
                onPeersAvailable?.invoke(teacherDevices)
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    fun requestConnectionInfo() {
        manager?.requestConnectionInfo(channel) { info ->
            if (info.groupFormed) {
                if (info.isGroupOwner) {
                    Log.d(TAG, "I am group owner (Teacher)")
                } else {
                    connectedToTeacher = true
                    groupOwnerAddress = info.groupOwnerAddress
                    Log.d(TAG, "Connected to teacher at: ${info.groupOwnerAddress}")
                    onConnectedToTeacher?.invoke(info.groupOwnerAddress)
                }
            } else {
                connectedToTeacher = false
                groupOwnerAddress = null
            }
        }
    }
    
    fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    fun isDiscovering() = discovering
    
    fun isConnected() = connectedToTeacher
    
    fun getTeacherAddress(): InetAddress? = groupOwnerAddress
}

class StudentWifiDirectBroadcastReceiver : android.content.BroadcastReceiver() {
    
    var studentWifiDirectManager: StudentWifiDirectManager? = null
    var listener: StudentWifiP2pBroadcastListener? = null
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                Log.d("StudentWifiDirect", "WiFi P2P enabled: $isEnabled")
                studentWifiDirectManager?.onWifiP2pEnabled?.invoke(isEnabled)
            }
            
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                studentWifiDirectManager?.requestPeers()
            }
            
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                studentWifiDirectManager?.requestConnectionInfo()
            }
        }
        
        listener?.onReceive(context, intent)
    }
}

interface StudentWifiP2pBroadcastListener {
    fun onReceive(context: Context, intent: Intent)
}

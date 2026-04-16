package com.edu.teacher.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import java.net.InetAddress

class WifiDirectManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WifiDirectManager"
        const val PORT = 9999
    }
    
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var isWifiP2pEnabled = false
    private var discovering = false
    
    var onWifiP2pEnabled: ((Boolean) -> Unit)? = null
    var onPeersAvailable: ((List<WifiP2pDevice>) -> Unit)? = null
    var onConnectionInfoAvailable: ((WifiP2pInfo) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onThisDeviceChanged: ((WifiP2pDevice) -> Unit)? = null
    
    private val receiver = TeacherWifiDirectBroadcastReceiver()
    
    fun initialize() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            Log.w(TAG, "WiFi Direct not supported on this device")
            return
        }
        
        manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = manager?.initialize(context, context.mainLooper, null)
        
        Log.d(TAG, "WiFi Direct initialized")
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
    
    fun setReceiverListener(listener: WifiP2pBroadcastListener) {
        receiver.listener = listener
        receiver.wifiDirectManager = this
    }
    
    fun discoverPeers() {
        if (!hasPermissions()) {
            Log.w(TAG, "Missing permissions for WiFi Direct")
            return
        }
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted")
            return
        }
        
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery started")
                discovering = true
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
    }
    
    fun connect(device: WifiP2pDevice) {
        if (!hasPermissions()) return
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }
        
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connecting to: ${device.deviceName}")
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
            }
            
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Disconnect failed: $reason")
            }
        })
    }
    
    fun requestPeers() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        manager?.requestPeers(channel) { peers ->
            onPeersAvailable?.invoke(peers.deviceList.toList())
        }
    }
    
    fun requestConnectionInfo() {
        manager?.requestConnectionInfo(channel) { info ->
            onConnectionInfoAvailable?.invoke(info)
        }
    }
    
    fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    fun isDiscovering() = discovering
    
    fun isWifiP2pEnabled() = isWifiP2pEnabled
    
    fun getGroupOwnerAddress(): InetAddress? {
        var address: InetAddress? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            manager?.requestConnectionInfo(channel) { info ->
                if (info.groupFormed && !info.isGroupOwner) {
                    address = info.groupOwnerAddress
                }
                latch.countDown()
            }
        } else {
            latch.countDown()
        }
        
        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting group owner", e)
        }
        
        return address
    }
}

class TeacherWifiDirectBroadcastReceiver : BroadcastReceiver() {
    
    var wifiDirectManager: WifiDirectManager? = null
    var listener: WifiP2pBroadcastListener? = null
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                Log.d("WifiDirect", "WiFi P2P enabled: $isEnabled")
                wifiDirectManager?.onWifiP2pEnabled?.invoke(isEnabled)
            }
            
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                wifiDirectManager?.requestPeers()
            }
            
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                wifiDirectManager?.requestConnectionInfo()
            }
            
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                }
                device?.let { wifiDirectManager?.onThisDeviceChanged?.invoke(it) }
            }
        }
        
        listener?.onReceive(context, intent)
    }
}

interface WifiP2pBroadcastListener {
    fun onReceive(context: Context, intent: Intent)
}

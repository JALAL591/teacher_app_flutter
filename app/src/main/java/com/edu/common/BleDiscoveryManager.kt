package com.edu.common

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.nio.charset.Charset
import java.util.UUID

class BleDiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "BleDiscoveryManager"
        
        val SERVICE_UUID: UUID = UUID.fromString("8a5c7f3e-b8a2-4d9e-9a1c-6e2f8d4c3b0a")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("9b6d8e4f-c9b3-5e0f-ac2d-7f3e9d5c1b0a")
        
        const val DEFAULT_PORT = 9999
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var isAdvertising = false
    private var isScanning = false
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    interface DiscoveryCallback {
        fun onTeacherFound(ip: String, port: Int, deviceName: String)
        fun onScanStarted()
        fun onScanStopped()
        fun onError(error: String)
    }
    
    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null
    
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
    
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun startAdvertising(teacherIp: String, teacherName: String, port: Int = DEFAULT_PORT) {
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return
        }
        
        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }
        
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        
        if (advertiser == null) {
            Log.e(TAG, "BLE Advertiser not available")
            return
        }
        
        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        
        val advertiseData = "${teacherIp}:${port}:${teacherName}"
        val advertiseBytes = advertiseData.toByteArray(Charset.forName("UTF-8"))
        
        val serviceData = android.os.ParcelUuid(SERVICE_UUID)
        
        val advertisePacket = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(serviceData, advertiseBytes)
            .build()
        
        try {
            advertiser?.startAdvertising(advertiseSettings, advertisePacket, advertiseCallback)
            isAdvertising = true
            Log.d(TAG, "Started advertising: $advertiseData")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising: ${e.message}")
        }
    }
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "Advertising started successfully")
        }
        
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed: $errorCode")
            isAdvertising = false
        }
    }
    
    fun startScanning(callback: DiscoveryCallback) {
        if (!hasBluetoothPermissions()) {
            callback.onError("Missing Bluetooth permissions")
            return
        }
        
        if (!isBluetoothEnabled()) {
            callback.onError("Bluetooth is not enabled")
            return
        }
        
        scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            callback.onError("BLE Scanner not available")
            return
        }
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()
        
        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
        )
        
        try {
            scanner?.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
            callback.onScanStarted()
            Log.d(TAG, "Started scanning for teachers")
        } catch (e: Exception) {
            callback.onError("Failed to start scanning: ${e.message}")
        }
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanRecord = result.scanRecord ?: return
            
            val serviceDataUuid = ParcelUuid(SERVICE_UUID)
            val serviceDataBytes = scanRecord.getServiceData(serviceDataUuid) ?: return
            
            val dataString = try {
                String(serviceDataBytes, Charset.forName("UTF-8"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode service data: ${e.message}")
                return
            }
            
            val parts = dataString.split(":")
            if (parts.size < 2) {
                Log.w(TAG, "Invalid data format: $dataString")
                return
            }
            
            val ip = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PORT
            val name = parts.getOrNull(2) ?: "Teacher"
            
            val deviceName = result.device.name ?: name
            
            Log.d(TAG, "Found teacher: $deviceName at $ip:$port")
            
            context.mainLooper?.let { looper ->
                android.os.Handler(looper).post {
                    currentCallback?.onTeacherFound(ip, port, deviceName)
                }
            } ?: run {
                currentCallback?.onTeacherFound(ip, port, deviceName)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            currentCallback?.onError("Scan failed: $errorCode")
        }
    }
    
    private var currentCallback: DiscoveryCallback? = null
    
    fun startScanningWithCallback(callback: DiscoveryCallback) {
        currentCallback = callback
        startScanning(callback)
    }
    
    fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising: ${e.message}")
        }
        advertiser = null
        isAdvertising = false
    }
    
    fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scanning: ${e.message}")
        }
        scanner = null
        isScanning = false
        currentCallback = null
    }
    
    fun stopAll() {
        stopAdvertising()
        stopScanning()
    }
    
    fun isCurrentlyAdvertising(): Boolean = isAdvertising
    fun isAdvertising(): Boolean = advertiser != null
    fun isCurrentlyScanning(): Boolean = isScanning
    
    fun destroy() {
        stopAll()
        scope.cancel()
    }
}
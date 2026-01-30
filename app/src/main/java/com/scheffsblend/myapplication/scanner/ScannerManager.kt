package com.scheffsblend.myapplication.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.scheffsblend.myapplication.data.Detection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class ScannerManager(private val context: Context) {

    companion object {
        private const val TAG = "ScannerManager"

        // WiFi SSID patterns (case-insensitive)
        private val WIFI_SSID_PATTERNS = listOf(
            "flock", "Flock", "FLOCK", "FS Ext Battery", "Penguin", "Pigvision"
        )

        // Known Flock Safety MAC address prefixes
        private val MAC_PREFIXES = listOf(
            "58:8e:81", "cc:cc:cc", "ec:1b:bd", "90:35:ea", "04:0d:84",
            "f0:82:c0", "1c:34:f1", "38:5b:44", "94:34:69", "b4:e3:f9",
            "70:c9:4e", "3c:91:80", "d8:f3:bc", "80:30:49", "14:5a:fc",
            "74:4c:a1", "08:3a:88", "9c:2f:9d", "94:08:53", "e4:aa:ea"
        )

        // Device name patterns for BLE advertisement detection
        private val DEVICE_NAME_PATTERNS = listOf(
            "FS Ext Battery", "Penguin", "Flock", "Pigvision"
        )

        // Raven surveillance device service UUIDs (shortened for contains() check)
        private val RAVEN_SERVICE_UUIDS = listOf(
            "0000180a", "00003100", "00003200", "00003300", 
            "00003400", "00003500", "00001809", "00001819"
        )
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation

    private val scanHandler = Handler(Looper.getMainLooper())

    fun updateLocation(lat: Double, lng: Double) {
        _userLocation.value = Pair(lat, lng)
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord
            val name = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    scanRecord?.deviceName
                } else {
                    scanRecord?.deviceName ?: device.name
                }
            } catch (e: SecurityException) {
                scanRecord?.deviceName
            }
            val mac = device.address
            val uuids = scanRecord?.serviceUuids?.map { it.toString().lowercase() } ?: emptyList()

            val detectionResult = calculateBleThreat(name, mac, uuids)
            if (detectionResult.threatLevel > 0) {
                Log.d(TAG, "BLE Match Found: Name=$name, MAC=$mac, RSSI=${result.rssi}, Reason=${detectionResult.reason}")
                val location = _userLocation.value ?: Pair(0.0, 0.0)
                val detection = Detection(
                    type = "Bluetooth",
                    macAddress = mac ?: "Unknown",
                    name = name ?: "Unknown BLE",
                    uuid = uuids.firstOrNull(),
                    rssi = result.rssi,
                    latitude = location.first,
                    longitude = location.second,
                    threatLevel = detectionResult.threatLevel,
                    reason = detectionResult.reason
                )
                addDetection(detection)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan Failed: $errorCode")
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                Log.d(TAG, "WiFi scan broadcast received. Results updated: $updated")
                processWifiResults()
            }
        }
    }

    private val wifiScanRunnable = object : Runnable {
        override fun run() {
            if (_isScanning.value) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    @SuppressLint("MissingPermission")
                    val success = wifiManager.startScan()
                    Log.d(TAG, "Aggressive WiFi startScan triggered: $success")
                }
                // Android 10 with "WiFi scan throttling" DISABLED can scan every 5 seconds.
                scanHandler.postDelayed(this, 5000)
            }
        }
    }

    private fun processWifiResults() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot process WiFi results: Location permission missing")
            return
        }

        @SuppressLint("MissingPermission")
        val results = wifiManager.scanResults
        Log.d(TAG, "Processing ${results.size} WiFi scan results")
        
        val foundDetections = mutableListOf<Detection>()
        val location = _userLocation.value ?: Pair(0.0, 0.0)
        results.forEach { result ->
            val detectionResult = calculateWifiThreat(result.SSID, result.BSSID)
            if (detectionResult.threatLevel > 0) {
                Log.d(TAG, "WiFi Match Found: SSID=${result.SSID}, BSSID=${result.BSSID}, RSSI=${result.level}, Reason=${detectionResult.reason}")
                foundDetections.add(Detection(
                    type = "WiFi",
                    macAddress = result.BSSID,
                    name = if (result.SSID.isNullOrEmpty()) "Hidden Network" else result.SSID,
                    uuid = null,
                    rssi = result.level,
                    latitude = location.first,
                    longitude = location.second,
                    threatLevel = detectionResult.threatLevel,
                    reason = detectionResult.reason
                ))
            }
        }
        
        if (foundDetections.isNotEmpty()) {
            addDetections(foundDetections)
        }
    }

    private data class ThreatResult(val threatLevel: Int, val reason: String?)

    private fun calculateWifiThreat(ssid: String?, bssid: String?): ThreatResult {
        val upperSsid = ssid?.uppercase(Locale.ROOT) ?: ""
        val upperBssid = bssid?.uppercase(Locale.ROOT) ?: ""

        val ssidPattern = WIFI_SSID_PATTERNS.find { pattern ->
            upperSsid.contains(pattern.uppercase(Locale.ROOT))
        }

        val macMatch = MAC_PREFIXES.find { prefix ->
            upperBssid.startsWith(prefix.uppercase(Locale.ROOT))
        }

        return when {
            ssidPattern != null && macMatch != null -> ThreatResult(3, "SSID + MAC prefix")
            ssidPattern != null -> ThreatResult(2, "SSID")
            macMatch != null -> ThreatResult(2, "MAC prefix")
            else -> ThreatResult(0, null)
        }
    }

    private fun calculateBleThreat(name: String?, mac: String?, uuids: List<String>): ThreatResult {
        val upperName = name?.uppercase(Locale.ROOT) ?: ""
        val upperMac = mac?.uppercase(Locale.ROOT) ?: ""

        val uuidMatch = RAVEN_SERVICE_UUIDS.find { ravenUuid ->
            uuids.any { it.contains(ravenUuid.lowercase()) }
        }
        if (uuidMatch != null) return ThreatResult(3, "Service UUID")

        val namePattern = DEVICE_NAME_PATTERNS.find { pattern ->
            upperName.contains(pattern.uppercase(Locale.ROOT))
        }

        val macMatch = MAC_PREFIXES.find { prefix ->
            upperMac.startsWith(prefix.uppercase(Locale.ROOT))
        }

        return when {
            namePattern != null && macMatch != null -> ThreatResult(3, "BLE Name + MAC prefix")
            namePattern != null -> ThreatResult(2, "BLE Name")
            macMatch != null -> ThreatResult(1, "BLE MAC prefix")
            else -> ThreatResult(0, null)
        }
    }

    private fun addDetection(detection: Detection) {
        val currentList = _detections.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.macAddress == detection.macAddress }
        if (existingIndex != -1) {
            currentList[existingIndex] = detection
        } else {
            currentList.add(0, detection)
        }
        _detections.value = currentList
    }

    private fun addDetections(newDetections: List<Detection>) {
        val currentList = _detections.value.toMutableList()
        newDetections.forEach { detection ->
            val existingIndex = currentList.indexOfFirst { it.macAddress == detection.macAddress }
            if (existingIndex != -1) {
                currentList[existingIndex] = detection
            } else {
                currentList.add(0, detection)
            }
        }
        _detections.value = currentList
    }

    fun clearDetections() {
        _detections.value = emptyList()
    }

    fun startScanning() {
        if (_isScanning.value) return

        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBleScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!hasLocation || !hasBleScan) {
            Log.e(TAG, "Cannot start scanning: Missing permissions (Location=$hasLocation, BLE Scan=$hasBleScan)")
            return
        }

        _isScanning.value = true

        // Push BLE scanning to the max for responsiveness
        val bleSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()
            
        @SuppressLint("MissingPermission")
        bluetoothAdapter?.bluetoothLeScanner?.startScan(null, bleSettings, bleScanCallback)
        
        context.registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        
        // Initial process of current results immediately
        processWifiResults()
        
        // Start aggressive WiFi scan loop (5s interval for Android 10+ without throttle)
        scanHandler.post(wifiScanRunnable)
    }

    fun stopScanning() {
        if (!_isScanning.value) return
        _isScanning.value = false

        val hasBleScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasBleScan) {
            @SuppressLint("MissingPermission")
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
        }

        try {
            context.unregisterReceiver(wifiReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering wifi receiver", e)
        }
        scanHandler.removeCallbacksAndMessages(null)
    }
}

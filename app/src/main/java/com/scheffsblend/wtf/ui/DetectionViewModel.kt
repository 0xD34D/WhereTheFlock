package com.scheffsblend.wtf.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scheffsblend.wtf.WTFApplication
import com.scheffsblend.wtf.data.AppDatabase
import com.scheffsblend.wtf.data.Detection
import com.scheffsblend.wtf.scanner.ScanningService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DetectionViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.detectionDao()
    private val scannerManager = (application as WTFApplication).scannerManager
    private val prefs = application.getSharedPreferences("detection_prefs", Context.MODE_PRIVATE)

    val isScanning = scannerManager.isScanning
    val currentDetections = scannerManager.detections
    val userLocation = scannerManager.userLocation

    private val _hasPermissions = MutableStateFlow(true)
    val hasPermissions = _hasPermissions.asStateFlow()
    
    private val _savedDetections = MutableStateFlow<List<Detection>>(emptyList())
    val savedDetections: StateFlow<List<Detection>> = _savedDetections.asStateFlow()

    private val _saveAutomatically = MutableStateFlow(prefs.getBoolean("auto_save", false))
    val saveAutomatically = _saveAutomatically.asStateFlow()

    init {
        viewModelScope.launch {
            dao.getAllDetections().collectLatest {
                _savedDetections.value = it
            }
        }
        
        viewModelScope.launch {
            currentDetections.collectLatest { detections ->
                if (_saveAutomatically.value) {
                    detections.forEach { saveIfStronger(it) }
                }
            }
        }
    }

    private suspend fun saveIfStronger(detection: Detection) {
        val existing = dao.getByMacAddress(detection.macAddress)
        if (existing == null || detection.rssi > existing.rssi) {
            val toSave = if (existing != null) detection.copy(id = existing.id) else detection
            dao.insert(toSave)
        }
    }

    fun setPermissionsGranted(granted: Boolean) {
        _hasPermissions.value = granted
    }

    fun toggleScanning() {
        if (!_hasPermissions.value) return

        val intent = Intent(getApplication(), ScanningService::class.java)
        if (isScanning.value) {
            getApplication<Application>().stopService(intent)
        } else {
            getApplication<Application>().startForegroundService(intent)
        }
    }

    fun toggleAutoSave(enabled: Boolean) {
        _saveAutomatically.value = enabled
        prefs.edit().putBoolean("auto_save", enabled).apply()
        
        if (enabled) {
            saveCurrentDetections()
        }
    }

    fun saveCurrentDetections() {
        viewModelScope.launch {
            currentDetections.value.forEach {
                saveIfStronger(it)
            }
        }
    }

    fun deleteSavedDetection(detection: Detection) {
        viewModelScope.launch {
            dao.delete(detection)
        }
    }

    fun removeCurrentDetection(detection: Detection) {
        scannerManager.removeDetection(detection)
    }

    fun updateLocation(lat: Double, lng: Double) {
        scannerManager.updateLocation(lat, lng)
    }

    fun clearCurrentDetections() {
        scannerManager.clearDetections()
    }

    fun clearSavedDetections() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }
}

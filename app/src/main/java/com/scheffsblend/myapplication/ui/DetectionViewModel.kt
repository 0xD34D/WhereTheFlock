package com.scheffsblend.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scheffsblend.myapplication.data.AppDatabase
import com.scheffsblend.myapplication.data.Detection
import com.scheffsblend.myapplication.scanner.ScannerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DetectionViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.detectionDao()
    private val scannerManager = ScannerManager(application)

    val isScanning = scannerManager.isScanning
    val currentDetections = scannerManager.detections
    val userLocation = scannerManager.userLocation
    
    private val _savedDetections = MutableStateFlow<List<Detection>>(emptyList())
    val savedDetections: StateFlow<List<Detection>> = _savedDetections.asStateFlow()

    private val _saveAutomatically = MutableStateFlow(false)
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
                    detections.forEach { dao.insert(it) }
                }
            }
        }
    }

    fun toggleScanning() {
        if (isScanning.value) {
            scannerManager.stopScanning()
        } else {
            scannerManager.startScanning()
        }
    }

    fun toggleAutoSave(enabled: Boolean) {
        _saveAutomatically.value = enabled
    }

    fun saveCurrentDetections() {
        viewModelScope.launch {
            currentDetections.value.forEach {
                dao.insert(it)
            }
        }
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

package com.scheffsblend.wtf

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.scheffsblend.wtf.ui.MainScreen
import com.scheffsblend.wtf.ui.DetectionViewModel
import com.scheffsblend.wtf.ui.WelcomeScreen
import com.scheffsblend.wtf.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DetectionViewModel by viewModels()
    private lateinit var locationManager: LocationManager

    private var showWelcomeScreen by mutableStateOf(false)

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            viewModel.updateLocation(location.latitude, location.longitude)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        updateViewModelPermissions()
        
        if (allGranted) {
            initializeLocation()
        } else {
            val deniedPermissions = permissions.filter { !it.value }.keys
            if (deniedPermissions.size == 1 && deniedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS)) {
                // If only notification permission is denied, we can still function.
                initializeLocation()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        val isFirstRun = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getBoolean("first_run", true)

        if (isFirstRun && !hasRequiredPermissions()) {
            showWelcomeScreen = true
        } else {
            checkAndRequestPermissions()
        }
        
        setContent {
            MyApplicationTheme {
                if (showWelcomeScreen) {
                    WelcomeScreen(onGetStarted = {
                        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("first_run", false).apply()
                        showWelcomeScreen = false
                        checkAndRequestPermissions()
                    })
                } else {
                    MainScreen(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateViewModelPermissions()
        if (hasLocationPermissions()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(locationListener)
    }

    private fun updateViewModelPermissions() {
        viewModel.setPermissionsGranted(hasRequiredPermissions())
    }

    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasRequiredPermissions(): Boolean {
        val locationGranted = hasLocationPermissions()
        
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        return locationGranted && bluetoothGranted
    }

    private fun initializeLocation() {
        if (hasLocationPermissions()) {
            getLastKnownLocation()
            startLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation() {
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val l = locationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation!!.accuracy) {
                bestLocation = l
            }
        }
        
        bestLocation?.let {
            viewModel.updateLocation(it.latitude, it.longitude)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermissions()) return
        
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                0f,
                locationListener
            )
        }
        
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                5000L,
                0f,
                locationListener
            )
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initializeLocation()
        }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}

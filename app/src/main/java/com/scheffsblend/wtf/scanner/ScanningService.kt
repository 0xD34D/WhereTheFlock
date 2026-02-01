package com.scheffsblend.wtf.scanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.scheffsblend.wtf.MainActivity
import com.scheffsblend.wtf.R
import com.scheffsblend.wtf.data.Detection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.jvm.java

class ScanningService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var scannerManager: ScannerManager
    private var lastDetectionCount = 0
    
    companion object {
        const val CHANNEL_ID = "scanning_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "STOP_SCANNING"
        
        private var _instance: ScanningService? = null
        val isRunning: Boolean get() = _instance != null
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
        createNotificationChannel()
        
        scannerManager = (application as? com.scheffsblend.wtf.WTFApplication)?.scannerManager
            ?: ScannerManager(applicationContext)
            
        startForeground(NOTIFICATION_ID, createNotification(0, null))
        
        serviceScope.launch {
            scannerManager.detections.collectLatest { detections ->
                val lastDetection = detections.firstOrNull()
                val currentCount = detections.size
                
                if (currentCount > lastDetectionCount) {
                    vibrate()
                }
                lastDetectionCount = currentCount
                
                updateNotification(currentCount, lastDetection)
            }
        }
        
        scannerManager.startScanning()
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopScanning()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun stopScanning() {
        scannerManager.stopScanning()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scannerManager.stopScanning()
        super.onDestroy()
        serviceScope.cancel()
        _instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(count: Int, lastTarget: Detection?): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScanningService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val lastTargetText = if (lastTarget != null) {
            getString(R.string.notif_last_target, lastTarget.name ?: lastTarget.macAddress)
        } else {
            getString(R.string.notif_no_targets)
        }
        
        val targetsFoundText = getString(R.string.targets_found, count)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(targetsFoundText)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(count: Int, lastTarget: Detection?) {
        val notification = createNotification(count, lastTarget)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}

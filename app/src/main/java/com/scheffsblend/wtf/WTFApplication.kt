package com.scheffsblend.wtf

import android.app.Application
import com.scheffsblend.wtf.scanner.ScannerManager

class WTFApplication : Application() {
    lateinit var scannerManager: ScannerManager
        private set

    override fun onCreate() {
        super.onCreate()
        scannerManager = ScannerManager(this)
    }
}

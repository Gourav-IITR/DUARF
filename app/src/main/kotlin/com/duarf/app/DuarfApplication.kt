package com.duarf.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DuarfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}

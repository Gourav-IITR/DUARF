// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app

import android.app.Application
import com.duarf.app.locale.AppLanguage
import com.duarf.app.service.MessageCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DuarfApplication : Application() {

    @Inject
    lateinit var messageCoordinator: MessageCoordinator

    override fun onCreate() {
        super.onCreate()
        AppLanguage.ensureDefault(this)
        messageCoordinator.initialize()
    }
}

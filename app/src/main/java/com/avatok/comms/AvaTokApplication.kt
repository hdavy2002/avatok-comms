/*
 * AvaTokApplication.kt — root Application class.
 *
 * Phase 3 commit 3a: @HiltAndroidApp added — Hilt DI graph is now
 * scaffolded. No services are actually wired yet (that's commit 3b),
 * but the annotation makes Hilt's generated component available to
 * future @AndroidEntryPoint Services / Activities.
 *
 * Copyright (C) AvaTok Comms contributors.
 * Distributed under GPL-3.0-or-later. See ../../../LICENSE.
 */
package com.avatok.comms

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AvaTokApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AvaTok Comms application starting (Phase 3 commit 3a — Hilt scaffold)")
        // Phase 3 commit 3b: kick off JamiDaemonService here after the
        // DI graph is wired with the libjamiclient *ServiceImpl classes
        // copied from Jami's app module.
    }

    companion object {
        private const val TAG = "AvaTok"
    }
}

/*
 * AvaTokApplication.kt — root Application class.
 *
 * Phase 2 commit 1 (this commit): stub — just initialises logging.
 * Phase 2 commit 2:                bootstraps libjamiclient services
 *                                  (DaemonService, AccountService),
 *                                  creates a Jami identity on first
 *                                  launch.
 *
 * Copyright (C) AvaTok Comms contributors.
 *
 * This file is part of AvaTok Comms, distributed under GPL-3.0-or-later.
 * See ../../../LICENSE for the full license text.
 */
package com.avatok.comms

import android.app.Application
import android.util.Log

class AvaTokApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AvaTok Comms application starting (Phase 2 scaffolding)")
        // Phase 2 commit 2: kick off DaemonService here.
    }

    companion object {
        private const val TAG = "AvaTok"
    }
}

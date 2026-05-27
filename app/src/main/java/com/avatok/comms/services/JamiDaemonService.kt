/*
 * JamiDaemonService.kt — foreground Service that owns the libjami
 * daemon's lifecycle.
 *
 * Phase 3 commit 3a: stub. Starts as a foreground service with a
 * persistent notification, but does NOT yet initialise the daemon
 * (Hilt-injected libjamiclient services are empty in commit 3a).
 *
 * Phase 3 commit 3b: @Inject the DaemonService from libjamiclient,
 * call mDaemonService.startDaemon() in onCreate(), stopDaemon() in
 * onDestroy(). Mirrors vendor/jami-client-android/jami-android/app/
 * src/main/java/cx/ring/services/JamiServiceWrapper.kt + their
 * JamiApplication.startDaemon() pattern.
 *
 * Why a foreground service: Jami's daemon owns long-lived UDP sockets
 * for DHT participation. If we run it only inside the Activity, Android
 * kills it as soon as the user backgrounds the app — incoming calls
 * and messages stop. Foreground service with an ongoing notification
 * is the documented Android pattern for keeping a comms daemon alive.
 *
 * Copyright (C) AvaTok Comms contributors.
 * Distributed under GPL-3.0-or-later. See ../../../../LICENSE.
 */
package com.avatok.comms.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class JamiDaemonService : Service() {

    // Phase 3 commit 3b:
    //   @Inject lateinit var daemonService: net.jami.services.DaemonService

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "JamiDaemonService onCreate (Phase 3 commit 3a — stub, daemon not yet started)")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        // Phase 3 commit 3b:
        //   daemonService.startDaemon()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "JamiDaemonService onStartCommand")
        // START_STICKY: if Android kills us under memory pressure, restart
        // as soon as resources free up. The user expects the comms client
        // to stay reachable.
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "JamiDaemonService onDestroy")
        // Phase 3 commit 3b:
        //   daemonService.stopDaemon()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AvaTok service",
                NotificationManager.IMPORTANCE_LOW   // silent persistent notification
            ).apply {
                description = "Keeps AvaTok available for incoming messages and calls"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AvaTok")
            .setContentText("Ready to send and receive")
            .setSmallIcon(android.R.drawable.ic_dialog_info)   // Phase 6: AvaTok icon
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val TAG = "AvaTok/Daemon"
        private const val CHANNEL_ID = "avatok.daemon"
        private const val NOTIFICATION_ID = 1001
    }
}

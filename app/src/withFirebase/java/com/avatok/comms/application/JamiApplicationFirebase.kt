/*
 *  Copyright (C) 2004-2025 Savoir-faire Linux Inc.
 *  Copyright (C) 2026 AvaTok contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.avatok.comms.application

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.HiltAndroidApp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * AvaTok-specific Firebase application class.
 *
 * Unlike stock Jami, we do NOT hand the FCM token directly to the daemon
 * with platform="android". The Jami public DHT proxy (dhtproxy.jami.net)
 * is configured with Jami's own FCM credentials, not ours, so a raw
 * platform="android" registration with our token would never receive
 * pushes.
 *
 * Instead we use the UnifiedPush mode of the DHT proxy:
 *   1. Get our FCM token from Firebase.
 *   2. POST it to https://avatok-comms-bridge.getmystuffme.workers.dev/register
 *      (a Cloudflare Worker we own).
 *   3. The bridge returns a unique pushUrl like
 *      https://avatok-comms-bridge.getmystuffme.workers.dev/push/<installId>.
 *   4. We hand THAT URL to the daemon as a UnifiedPush endpoint
 *      (platform="unifiedpush"). The daemon thinks it's UnifiedPush.
 *   5. dhtproxy.jami.net then POSTs to that URL whenever there's a value
 *      for us. The Worker translates the POST into an FCM data message
 *      in OUR Firebase project, which wakes the daemon.
 *
 * The daemon receives the same Map<String, String> via FCM data fields
 * as it would receive from a UnifiedPush distributor, so the on-device
 * JamiFirebaseMessagingService stays unchanged (it just hands
 * remoteMessage.data to mAccountService.pushNotificationReceived as
 * before).
 *
 * Why this routing: zero infra costs for AvaTok at any scale
 * (Worker free tier, FCM free forever, KV free up to 100k reads/day),
 * zero on-device friction (no helper app like ntfy needed), and
 * AvaTok stays a Jami client without forking the engine.
 *
 * See also:
 *   - avatok-comms-bridge (Cloudflare Worker source, separate repo)
 *   - avatok/openspec/changes/add-avatok-comms-push-wakeup/
 */
@HiltAndroidApp
class JamiApplicationFirebase : JamiApplication() {
    override val pushPlatform: String = PUSH_PLATFORM

    override var pushToken: Pair<String, String>? = null
        set(token) {
            field = token
            if (token != null && mPreferencesService.settings.enablePushNotifications) {
                mAccountService.setPushNotificationConfig(token.first, token.second, PUSH_PLATFORM)
            } else {
                mAccountService.setPushNotificationToken("")
            }
        }

    override fun onCreate() {
        super.onCreate()
        hardwareService.startTime = getCurrentTimestamp()
        hardwareService.highPriorityPushCount = 0
        hardwareService.normalPriorityPushCount = 0
        hardwareService.unknownPriorityPushCount = 0
        try {
            Log.w(TAG, "onCreate()")
            FirebaseApp.initializeApp(this)
            FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken: String? ->
                Log.w(TAG, "Found FCM token")
                if (fcmToken == null) {
                    pushToken = null
                    return@addOnSuccessListener
                }
                registerWithBridge(fcmToken)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Can't start service", e)
        }
    }

    /**
     * Called from JamiFirebaseMessagingService.onNewToken when FCM rotates
     * our token. Re-register with the bridge so dhtproxy still hits a
     * pushUrl that maps to the new FCM token.
     */
    fun onFcmTokenRefreshed(refreshedToken: String) {
        Log.w(TAG, "onFcmTokenRefreshed — re-registering with bridge")
        // Invalidate the bridge cache so registerWithBridge POSTs again.
        getSharedPreferences(PREFS_BRIDGE, MODE_PRIVATE).edit().clear().apply()
        registerWithBridge(refreshedToken)
    }

    private fun registerWithBridge(fcmToken: String) {
        // Fast path: if we've already registered THIS exact FCM token,
        // reuse the cached pushUrl. FCM rarely rotates the token, so this
        // path runs on every cold start.
        val cachedUrl = readCachedPushUrl(fcmToken)
        if (cachedUrl != null) {
            Log.i(TAG, "Reusing cached bridge pushUrl")
            Handler(Looper.getMainLooper()).post { pushToken = Pair(cachedUrl, "") }
            return
        }

        thread(name = "avatok-bridge-register", start = true) {
            try {
                val url = URL("$BRIDGE_URL/register")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val payload = JSONObject().apply {
                    put("fcmToken", fcmToken)
                    put("hint", "android-${Build.MODEL}-sdk${Build.VERSION.SDK_INT}")
                }.toString()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    Log.e(TAG, "Bridge /register HTTP $code")
                    return@thread
                }

                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(responseBody)
                val installId = obj.getString("installId")
                val pushUrl = obj.getString("pushUrl")
                cachePushUrl(fcmToken, installId, pushUrl)
                Log.i(TAG, "Bridge registration complete; installId=$installId")

                Handler(Looper.getMainLooper()).post { pushToken = Pair(pushUrl, "") }
            } catch (e: Exception) {
                Log.e(TAG, "Bridge registration failed", e)
            }
        }
    }

    private fun readCachedPushUrl(fcmToken: String): String? {
        val prefs = getSharedPreferences(PREFS_BRIDGE, MODE_PRIVATE)
        val cachedFcmToken = prefs.getString(KEY_FCM_TOKEN, null)
        if (cachedFcmToken != fcmToken) return null
        return prefs.getString(KEY_PUSH_URL, null)
    }

    private fun cachePushUrl(fcmToken: String, installId: String, pushUrl: String) {
        getSharedPreferences(PREFS_BRIDGE, MODE_PRIVATE).edit()
            .putString(KEY_FCM_TOKEN, fcmToken)
            .putString(KEY_INSTALL_ID, installId)
            .putString(KEY_PUSH_URL, pushUrl)
            .apply()
    }

    private fun getCurrentTimestamp(withMilliseconds: Boolean = false): String {
        val pattern = if (withMilliseconds) "yyyy-MM-dd HH:mm:ss.SSS" else "yyyy-MM-dd HH:mm:ss"
        val formatter = SimpleDateFormat(pattern, Locale.getDefault())
        return formatter.format(Date())
    }

    fun onMessageReceived(remoteMessage: RemoteMessage) {
        // The on-the-wire FCM data fields match what dhtproxy would have
        // sent to a UnifiedPush distributor (the Worker forwards each
        // top-level JSON key as an FCM data field), so this stays
        // unchanged from stock Jami's withFirebase flavor: hand
        // remoteMessage.data to the daemon, let the daemon decide what
        // to do with it.
        mAccountService.pushNotificationReceived(remoteMessage.from ?: "", remoteMessage.data)
        mNotificationService.processPush()
        when (remoteMessage.priority) {
            RemoteMessage.PRIORITY_HIGH -> hardwareService.highPriorityPushCount++
            RemoteMessage.PRIORITY_NORMAL -> hardwareService.normalPriorityPushCount++
            RemoteMessage.PRIORITY_UNKNOWN -> hardwareService.unknownPriorityPushCount++
        }
        val messageData = remoteMessage.data.toString()
        val currentTimestamp = getCurrentTimestamp(withMilliseconds = true)
        hardwareService.pushLogMessage("[$currentTimestamp] Received message from: ${remoteMessage.from}, data: $messageData")
    }

    companion object {
        // We register with the daemon as a UnifiedPush client, not as
        // "android", so that dhtproxy.jami.net does an HTTPS POST to our
        // pushUrl instead of trying to use Jami's FCM project (which
        // doesn't know our app).
        private const val PUSH_PLATFORM = "unifiedpush"

        // Cloudflare Worker that bridges dhtproxy → FCM. Source lives at
        // /Users/davy/Documents/websites/avatok-comms-bridge.
        // Deployed in account Hdavy2005@gmail.com on 2026-05-28.
        private const val BRIDGE_URL = "https://avatok-comms-bridge.getmystuffme.workers.dev"

        private const val PREFS_BRIDGE = "avatok_bridge"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_PUSH_URL = "push_url"

        private val TAG = JamiApplicationFirebase::class.simpleName
    }
}

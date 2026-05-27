/*
 * AvaTok Comms — local persistence of the user's avatok.ai session.
 *
 * Stores the Clerk session cookie + minimal profile info captured during
 * the AvaTokLoginActivity WebView flow. Backed by SharedPreferences
 * (encrypted-prefs is overkill for a cookie that's already short-lived
 * server-side; the OS app-sandbox is the trust boundary).
 *
 * Spec: ../../../../../docs/proposals/avatok-comms-jami-mvp-amendment-1.md
 */
package com.avatok.comms.account

import android.content.Context
import android.content.SharedPreferences

object AvaTokSession {
    private const val PREFS_NAME = "avatok_session"
    private const val KEY_SESSION_COOKIE = "session_cookie"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_DISPLAY_NAME = "user_display_name"
    private const val KEY_AVATOK_USER_ID = "avatok_user_id"
    private const val KEY_LOGIN_TIMESTAMP = "login_ts"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(
        ctx: Context,
        sessionCookie: String,
        userEmail: String?,
        displayName: String?,
        userId: String?,
    ) {
        prefs(ctx).edit()
            .putString(KEY_SESSION_COOKIE, sessionCookie)
            .putString(KEY_USER_EMAIL, userEmail)
            .putString(KEY_USER_DISPLAY_NAME, displayName)
            .putString(KEY_AVATOK_USER_ID, userId)
            .putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun sessionCookie(ctx: Context): String? =
        prefs(ctx).getString(KEY_SESSION_COOKIE, null)

    fun userEmail(ctx: Context): String? =
        prefs(ctx).getString(KEY_USER_EMAIL, null)

    fun displayName(ctx: Context): String? =
        prefs(ctx).getString(KEY_USER_DISPLAY_NAME, null)

    fun isLoggedIn(ctx: Context): Boolean = sessionCookie(ctx) != null

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}

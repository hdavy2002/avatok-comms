/*
 * AvaTok Comms — first-run login screen.
 *
 * Replaces Jami's original AccountWizardActivity entry point for users
 * who have no local account yet. Embeds a WebView pointed at
 * https://avatok.ai/login so the user gets the existing Clerk-hosted
 * sign-in / sign-up / forgot-password UI verbatim — no native auth
 * rebuild required.
 *
 * Flow:
 *   1. WebView loads avatok.ai/login (or /register / /forgot-password,
 *      both reachable via links on the login page).
 *   2. WebViewClient.onPageFinished fires for every navigation. When the
 *      URL settles on a post-login page (anything on avatok.ai that is
 *      NOT an auth route), we treat the session as established.
 *   3. We capture the Clerk session cookie via CookieManager and persist
 *      it via AvaTokSession.save().
 *   4. We hand off to Jami's AccountWizardActivity so the existing
 *      rebranded create-account wizard takes over. The wizard creates
 *      a fresh local Jami keypair (the device IS the source of truth
 *      until the backend-keypair-fetch follow-up lands per
 *      docs/proposals/avatok-comms-backend-endpoints.md).
 *
 * Once the wizard finishes, the user lands in HomeActivity with a
 * working AvaTok-branded chat/voice/video client.
 */
package com.avatok.comms.account

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.avatok.comms.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class AvaTokLoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AvaTokLogin"

        // The avatok.ai surface the WebView starts on. Once Clerk's
        // session is established, navigation lands on "/" or any
        // non-auth path; we detect that to end the flow.
        const val LOGIN_URL = "https://avatok.ai/login"
        const val ME_URL = "https://avatok.ai/api/jami/me"

        // Intent extras passed into AccountWizardActivity so it can
        // pre-fill the create-account wizard with what the user just
        // told us during avatok.ai login. AccountWizardActivity reads
        // these in onCreate and seeds AccountCreationViewModel.
        const val EXTRA_AVATOK_DISPLAY_NAME = "avatok.display_name"
        const val EXTRA_AVATOK_EMAIL = "avatok.email"
        const val EXTRA_AVATOK_USER_ID = "avatok.user_id"

        // Path prefixes that mean "still on an auth surface" — the user
        // hasn't finished logging in yet. Anything else on avatok.ai
        // counts as post-login.
        private val AUTH_PATH_PREFIXES = listOf(
            "/login",
            "/register",
            "/sign-in",
            "/sign-up",
            "/forgot-password",
            "/reset-password",
            "/sso-callback",
            "/api/", // server-side endpoints, never a "landing" page
        )

        private const val AVATOK_HOST = "avatok.ai"
    }

    private lateinit var webView: WebView
    private lateinit var progressOverlay: FrameLayout
    private lateinit var progressLabel: TextView

    // Latches so we only fire the hand-off once even if onPageFinished
    // re-runs (it can fire multiple times during Clerk redirects).
    @Volatile private var handedOff: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avatok_login)

        webView = findViewById(R.id.avatok_login_webview)
        progressOverlay = findViewById(R.id.avatok_login_progress_overlay)
        progressLabel = findViewById(R.id.avatok_login_progress_label)

        // Cookies are mandatory for Clerk auth to persist. Accept third-
        // party cookies on the WebView itself for SSO redirects.
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // Avatok.ai is a desktop-first site; force the mobile UA so
            // the responsive shell kicks in.
            userAgentString = userAgentString + " AvaTokComms/Android"
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.webViewClient = LoginWebViewClient()

        // Intercept system back: if the WebView can go back inside the
        // auth flow, do that instead of finishing the activity.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        // First-run flow — there's nowhere to back out to.
                        // Killing the activity returns to launcher.
                        finishAffinity()
                    }
                }
            },
        )

        if (savedInstanceState == null) {
            Log.i(TAG, "Loading $LOGIN_URL")
            webView.loadUrl(LOGIN_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    private fun showProgress(message: Int) {
        progressLabel.setText(message)
        progressOverlay.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        progressOverlay.visibility = View.GONE
    }

    /**
     * Returns true if the URL is on avatok.ai and is NOT an auth-flow
     * page — i.e. the user has logged in.
     */
    private fun isPostLoginUrl(url: String): Boolean {
        val uri = try {
            Uri.parse(url)
        } catch (t: Throwable) {
            return false
        }
        val host = uri.host ?: return false
        if (!host.equals(AVATOK_HOST, ignoreCase = true) &&
            !host.endsWith(".$AVATOK_HOST", ignoreCase = true)
        ) {
            return false
        }
        val path = uri.path ?: "/"
        return AUTH_PATH_PREFIXES.none { prefix -> path.startsWith(prefix) }
    }

    /**
     * Capture the avatok.ai cookies, fetch the user's profile from
     * `/api/jami/me`, persist both, then hand off to
     * AccountWizardActivity so Jami's existing create-account wizard
     * runs to completion with pre-filled display name. The wizard
     * creates the local Jami keypair — that's the part that gives the
     * user a working comms identity for Commit B. The backend-keypair-
     * fetch (cross-device same-identity) is the follow-up tracked in
     * docs/proposals/avatok-comms-backend-endpoints.md.
     */
    private fun onLoginSuccess(landingUrl: String) {
        if (handedOff) return
        handedOff = true

        showProgress(R.string.avatok_login_setting_up)

        val cookie = CookieManager.getInstance().getCookie("https://avatok.ai") ?: ""
        if (cookie.isBlank()) {
            Log.w(TAG, "No avatok.ai cookie present after login landing at $landingUrl")
        }

        // Fetch profile in a background thread (we're on UI thread now).
        // We deliberately don't block the hand-off on this — if /me
        // 401s or times out, we still ship the user through to the
        // wizard with a blank pre-fill.
        thread(start = true, name = "avatok-me-fetch") {
            val profile = fetchMe(cookie)
            runOnUiThread { proceedToWizard(cookie, profile) }
        }
    }

    private data class AvaTokProfile(
        val userId: String?,
        val email: String?,
        val displayName: String?,
    )

    /**
     * Plain HttpsURLConnection call to /api/jami/me. Returns null on
     * any failure — non-fatal, the wizard still works without pre-fill.
     */
    private fun fetchMe(cookie: String): AvaTokProfile? {
        if (cookie.isBlank()) return null
        return try {
            val conn = (URL(ME_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", cookie)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AvaTokComms/Android")
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            if (code != 200) {
                Log.w(TAG, "/api/jami/me returned HTTP $code")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            AvaTokProfile(
                userId = json.optString("user_id").ifBlank { null },
                email = json.optString("email").ifBlank { null },
                displayName = json.optString("display_name").ifBlank { null },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "/api/jami/me failed: ${t.message}")
            null
        }
    }

    private fun proceedToWizard(cookie: String, profile: AvaTokProfile?) {
        AvaTokSession.save(
            ctx = this,
            sessionCookie = cookie,
            userEmail = profile?.email,
            displayName = profile?.displayName,
            userId = profile?.userId,
        )
        Log.i(
            TAG,
            "AvaTok session persisted (email=${profile?.email != null}, " +
                "displayName=${profile?.displayName != null}); " +
                "handing off to AccountWizardActivity",
        )

        // Defer the intent until the next main-thread tick so the
        // progress overlay actually paints before the wizard fades in.
        webView.post {
            startActivity(
                Intent(this, AccountWizardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(EXTRA_AVATOK_DISPLAY_NAME, profile?.displayName)
                    putExtra(EXTRA_AVATOK_EMAIL, profile?.email)
                    putExtra(EXTRA_AVATOK_USER_ID, profile?.userId)
                },
            )
            finish()
        }
    }

    private inner class LoginWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            Log.d(TAG, "onPageStarted $url")
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d(TAG, "onPageFinished $url")
            url ?: return
            if (isPostLoginUrl(url)) {
                onLoginSuccess(url)
            } else {
                hideProgress()
            }
        }

        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?,
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            Log.w(TAG, "WebView error $errorCode at $failingUrl: $description")
            // Don't bury the user — surface a Toast and let them retry.
            Toast.makeText(
                this@AvaTokLoginActivity,
                R.string.avatok_login_network_error,
                Toast.LENGTH_LONG,
            ).show()
            hideProgress()
        }
    }
}

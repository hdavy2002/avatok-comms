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

class AvaTokLoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AvaTokLogin"

        // The avatok.ai surface the WebView starts on. Once Clerk's
        // session is established, navigation lands on "/" or any
        // non-auth path; we detect that to end the flow.
        const val LOGIN_URL = "https://avatok.ai/login"

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
     * Capture the avatok.ai cookies, persist them, then hand off to
     * AccountWizardActivity so Jami's existing create-account wizard
     * runs to completion. The wizard creates the local Jami keypair —
     * that's the part that gives the user a working comms identity
     * for Commit B. The backend-keypair-fetch is the follow-up.
     */
    private fun onLoginSuccess(landingUrl: String) {
        if (handedOff) return
        handedOff = true

        showProgress(R.string.avatok_login_setting_up)

        val cookie = CookieManager.getInstance().getCookie("https://avatok.ai") ?: ""
        if (cookie.isBlank()) {
            Log.w(TAG, "No avatok.ai cookie present after login landing at $landingUrl")
        }

        AvaTokSession.save(
            ctx = this,
            sessionCookie = cookie,
            // We don't have email/displayName/userId yet — the
            // /api/me-style endpoint comes with the backend keypair work.
            // Save what we have; the wizard will set the Jami display
            // name and the user can rename later.
            userEmail = null,
            displayName = null,
            userId = null,
        )
        Log.i(TAG, "AvaTok session persisted; handing off to AccountWizardActivity")

        // Defer the intent until the next main-thread tick so the
        // progress overlay actually paints before the wizard fades in.
        webView.post {
            startActivity(
                Intent(this, AccountWizardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
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

/*
 * AvaTok Comms — landing / welcome screen.
 *
 * Proof-of-concept simplification (Davy 2026-05-27): the previous
 * WebView-based login flow that loaded avatok.ai/login looked messy
 * inside the embed and was confusing for investor demos. Replaced
 * with a pure-native landing: AvaTok logo, welcome headline, single
 * "Get started" button that drops the user straight into
 * AccountWizardActivity. The wizard auto-skips its own welcome
 * fragment (HomeAccountCreationFragment) and lands on the
 * username-pick page, so the user sees:
 *
 *     Landing  →  Username  →  Profile  →  Home
 *
 * No login, no auth, no backend roundtrip. The real avatok.ai-bound
 * identity flow is the deferred work tracked in
 * docs/proposals/avatok-comms-jami-mvp-amendment-1.md.
 *
 * The activity name and manifest entry are unchanged so HomeActivity
 * keeps routing here on first run (no local Jami account) — only the
 * activity's behaviour has changed.
 */
package com.avatok.comms.account

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.avatok.comms.R
import com.google.android.material.button.MaterialButton

class AvaTokLoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AvaTokLanding"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avatok_login)

        findViewById<MaterialButton>(R.id.avatok_landing_proceed).setOnClickListener {
            Log.i(TAG, "Proceed tapped — handing off to AccountWizardActivity")
            // Mark a synthetic AvaTok 'session' so HomeActivity's
            // first-run gate doesn't try to bounce the user back here
            // after the Jami wizard runs. The values are placeholders
            // because the real Clerk auth isn't wired in this POC.
            AvaTokSession.save(
                ctx = this,
                sessionCookie = "poc-no-auth",
                userEmail = null,
                displayName = null,
                userId = null,
            )

            startActivity(
                Intent(this, AccountWizardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
            )
            finish()
        }

        // System back: this is the first-run gate — there's nowhere
        // sensible to go back to from here, so back closes the app.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishAffinity()
                }
            },
        )
    }
}

/*
 * AvaTok Comms — directory lookup client.
 *
 * Wraps GET https://avatok.ai/api/jami/lookup?username=<handle>.
 * Used by the contact-add / search-for-people UI to resolve an
 * AvaTok handle or email into a Jami ID, so users can add each other
 * by AvaTok handle instead of exchanging 40-char hex Jami IDs.
 *
 * Spec: avatok/docs/proposals/avatok-comms-backend-endpoints.md
 *       endpoint (3).
 *
 * Wiring status (2026-05-27): this class is ready. The seam to call
 * it from HomeFragment.querySubject is flagged with a TODO in that
 * file. Polish-4 follow-up wires the call once polish-1/2/3 are
 * verified on a real APK.
 */
package com.avatok.comms.account

import android.content.Context
import android.util.Log
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AvaTokDirectoryClient {
    private const val TAG = "AvaTokDirectory"
    private const val LOOKUP_URL = "https://avatok.ai/api/jami/lookup"

    /**
     * A single result returned by /api/jami/lookup. The lookup
     * endpoint may return either a single `match` object or a list
     * of `matches`; we normalize to a list here regardless.
     */
    data class Match(
        val userId: String?,
        val username: String?,
        val displayName: String?,
        val jamiId: String,
    )

    data class LookupResult(
        val matches: List<Match>,
        val truncated: Boolean,
    )

    /**
     * Returns true if the input string looks like a candidate for the
     * directory (not already a Jami ID, not blank, not just digits).
     *
     * Jami IDs are 40-char lowercase hex. If the user typed one of
     * those, the existing Jami search path handles it — no need to
     * round-trip our backend.
     */
    fun looksLikeAvaTokHandle(query: String): Boolean {
        val q = query.trim()
        if (q.length < 2 || q.length > 100) return false
        if (q.matches(Regex("^[0-9a-f]{40}$"))) return false // Jami ID
        if (q.matches(Regex("^[0-9]+$"))) return false       // phone-ish
        // Anything else (@handle, email, partial name) counts.
        return true
    }

    /**
     * Look up a handle/email/display-name against avatok.ai's directory.
     *
     * Sends the captured Clerk session cookie (from AvaTokSession) so
     * the endpoint can authorise the request. Returns Single.error on
     * network failure; emits an empty result on 404 (no match).
     */
    fun lookup(ctx: Context, query: String): Single<LookupResult> =
        Single.fromCallable {
            val cookie = AvaTokSession.sessionCookie(ctx) ?: ""
            if (cookie.isBlank()) {
                Log.w(TAG, "lookup called without an AvaTok session cookie")
                return@fromCallable LookupResult(emptyList(), false)
            }
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$LOOKUP_URL?username=$encoded"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", cookie)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AvaTokComms/Android")
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            if (code == 404) {
                return@fromCallable LookupResult(emptyList(), false)
            }
            if (code != 200) {
                Log.w(TAG, "/api/jami/lookup returned HTTP $code for query='$query'")
                return@fromCallable LookupResult(emptyList(), false)
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseResponse(body)
        }.subscribeOn(Schedulers.io())

    private fun parseResponse(body: String): LookupResult {
        val json = JSONObject(body)
        // Single-match shape: { match: { ... } }
        val single = json.optJSONObject("match")
        if (single != null) {
            val m = parseMatch(single) ?: return LookupResult(emptyList(), false)
            return LookupResult(listOf(m), truncated = false)
        }
        // Multi-match shape: { matches: [ {...}, ... ], truncated: bool }
        val arr: JSONArray? = json.optJSONArray("matches")
        if (arr != null) {
            val out = mutableListOf<Match>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                parseMatch(obj)?.let(out::add)
            }
            return LookupResult(out, truncated = json.optBoolean("truncated", false))
        }
        return LookupResult(emptyList(), false)
    }

    private fun parseMatch(obj: JSONObject): Match? {
        val jamiId = obj.optString("jami_id").trim()
        if (jamiId.isBlank()) return null
        return Match(
            userId = obj.optString("user_id").ifBlank { null },
            username = obj.optString("username").ifBlank { null },
            displayName = obj.optString("display_name").ifBlank { null },
            jamiId = jamiId,
        )
    }
}

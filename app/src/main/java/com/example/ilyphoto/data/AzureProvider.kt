package com.example.ilyphoto.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Single access point for Azure Cloud Backend and secure session management.
 *
 * Connected to Microsoft Azure App Service + Azure Table Storage and Azure Blob Storage.
 * Enforces bearer token validation, session expiration, and SHA-256 session integrity checks.
 */
object AzureProvider {
    private const val PREFS_NAME = "ilyphoto_azure_prefs"
    private const val KEY_AUTH_TOKEN = "azure_auth_token"
    private const val KEY_USER_PROFILE = "azure_user_profile"
    private const val KEY_SESSION_TIMESTAMP = "azure_session_timestamp"
    private const val KEY_SESSION_SIGNATURE = "azure_session_signature"
    private const val SESSION_SALT = "IlyPhoto-Azure-Session-Integrity-v2"

    /** Maximum age of a cached session (24 hours) before re-authentication is required. */
    const val SESSION_MAX_AGE_MS = 24L * 60L * 60L * 1000L

    private lateinit var prefs: SharedPreferences

    // In-memory backing store used during runtime and when SharedPreferences is not initialized (e.g. JVM tests)
    private var memoryAuthToken: String? = null
    private var memoryProfile: UserProfile? = null
    private var memoryTimestamp: Long = 0L
    private var memorySignature: String? = null

    var isConfigured: Boolean = true
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isConfigured = AzureConfig.API_BASE_URL.isNotBlank()
        // Purge any legacy or invalid unauthenticated sessions on startup
        if (!hasValidSession()) {
            clearSession()
        }
    }

    fun isTokenValid(token: String?): Boolean {
        val clean = token?.trim().orEmpty()
        return clean.length >= 8 &&
            !clean.equals("local-offline-token", ignoreCase = true) &&
            !clean.equals("null", ignoreCase = true)
    }

    private fun computeSignature(token: String, email: String, uid: String, role: String): String {
        val payload = "$SESSION_SALT|$token|${email.lowercase()}|$uid|$role"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    var authToken: String?
        get() {
            val raw = if (::prefs.isInitialized) {
                prefs.getString(KEY_AUTH_TOKEN, null)
            } else {
                memoryAuthToken
            }
            return raw?.takeIf { isTokenValid(it) }
        }
        set(value) {
            val validValue = value?.takeIf { isTokenValid(it) }
            memoryAuthToken = validValue
            if (::prefs.isInitialized) {
                prefs.edit().apply {
                    if (validValue == null) remove(KEY_AUTH_TOKEN) else putString(KEY_AUTH_TOKEN, validValue)
                }.apply()
            }
        }

    /**
     * Establishes an authenticated session after server-side credential verification.
     */
    fun establishSession(
        token: String,
        profile: UserProfile,
        persistToDisk: Boolean = true,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        require(isTokenValid(token)) { "Cannot establish session with an invalid authentication token." }
        require(profile.email.isNotBlank()) { "Authenticated profile must include a valid email." }

        val sig = computeSignature(token.trim(), profile.email, profile.uid, profile.role.name)
        memoryAuthToken = token.trim()
        memoryProfile = profile
        memoryTimestamp = nowMillis
        memorySignature = sig

        if (::prefs.isInitialized) {
            if (persistToDisk) {
                prefs.edit()
                    .putString(KEY_AUTH_TOKEN, token.trim())
                    .putString(KEY_USER_PROFILE, profile.toJson().toString())
                    .putLong(KEY_SESSION_TIMESTAMP, nowMillis)
                    .putString(KEY_SESSION_SIGNATURE, sig)
                    .apply()
            } else {
                // Keep in-memory for current app process only; remove persisted disk credentials
                prefs.edit()
                    .remove(KEY_AUTH_TOKEN)
                    .remove(KEY_USER_PROFILE)
                    .remove(KEY_SESSION_TIMESTAMP)
                    .remove(KEY_SESSION_SIGNATURE)
                    .apply()
            }
        }
    }

    fun saveCachedProfile(profile: UserProfile?) {
        if (profile == null) {
            clearSession()
            return
        }
        val currentToken = authToken ?: memoryAuthToken
        if (!isTokenValid(currentToken)) {
            // Reject saving a cached profile without a valid authenticated token
            return
        }
        establishSession(
            token = currentToken!!,
            profile = profile,
            persistToDisk = true,
            nowMillis = if (memoryTimestamp > 0L) memoryTimestamp else System.currentTimeMillis()
        )
    }

    fun hasValidSession(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val token = authToken ?: memoryAuthToken
        if (!isTokenValid(token)) return false

        val profile = readRawProfile() ?: return false
        if (profile.email.isBlank()) return false

        val timestamp = if (::prefs.isInitialized && prefs.contains(KEY_SESSION_TIMESTAMP)) {
            prefs.getLong(KEY_SESSION_TIMESTAMP, 0L)
        } else {
            memoryTimestamp
        }
        if (timestamp <= 0L || (nowMillis - timestamp) > SESSION_MAX_AGE_MS) {
            return false
        }

        val savedSig = if (::prefs.isInitialized && prefs.contains(KEY_SESSION_SIGNATURE)) {
            prefs.getString(KEY_SESSION_SIGNATURE, null)
        } else {
            memorySignature
        }
        val expectedSig = computeSignature(token!!.trim(), profile.email, profile.uid, profile.role.name)
        return savedSig != null && savedSig == expectedSig
    }

    private fun readRawProfile(): UserProfile? {
        memoryProfile?.let { return it }
        if (!::prefs.isInitialized) return null
        val jsonStr = prefs.getString(KEY_USER_PROFILE, null) ?: return null
        return runCatching {
            UserProfile.fromJson(JSONObject(jsonStr))
        }.getOrNull()
    }

    fun getCachedProfile(): UserProfile? {
        if (!hasValidSession()) return null
        return readRawProfile()
    }

    fun clearSession() {
        memoryAuthToken = null
        memoryProfile = null
        memoryTimestamp = 0L
        memorySignature = null
        if (::prefs.isInitialized) {
            prefs.edit()
                .remove(KEY_AUTH_TOKEN)
                .remove(KEY_USER_PROFILE)
                .remove(KEY_SESSION_TIMESTAMP)
                .remove(KEY_SESSION_SIGNATURE)
                .apply()
        }
    }
}

/** Runs [block] and wraps the outcome in a [Result], handling Azure network/API exceptions cleanly. */
internal suspend fun <T> azureCall(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

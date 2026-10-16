package com.example.ilyphoto.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Single access point for Azure Cloud Backend and session management.
 *
 * Replaces FirebaseProvider with Microsoft Azure App Service + Cosmos DB.
 */
object AzureProvider {
    private const val PREFS_NAME = "ilyphoto_azure_prefs"
    private const val KEY_AUTH_TOKEN = "azure_auth_token"
    private const val KEY_USER_PROFILE = "azure_user_profile"

    private lateinit var prefs: SharedPreferences

    var isConfigured: Boolean = true
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isConfigured = AzureConfig.API_BASE_URL.isNotBlank()
    }

    var authToken: String?
        get() = if (::prefs.isInitialized) prefs.getString(KEY_AUTH_TOKEN, null) else null
        set(value) {
            if (::prefs.isInitialized) {
                prefs.edit().apply {
                    if (value == null) remove(KEY_AUTH_TOKEN) else putString(KEY_AUTH_TOKEN, value)
                }.apply()
            }
        }

    fun saveCachedProfile(profile: UserProfile?) {
        if (::prefs.isInitialized) {
            prefs.edit().apply {
                if (profile == null) {
                    remove(KEY_USER_PROFILE)
                } else {
                    putString(KEY_USER_PROFILE, profile.toJson().toString())
                }
            }.apply()
        }
    }

    fun getCachedProfile(): UserProfile? {
        if (!::prefs.isInitialized) return null
        val jsonStr = prefs.getString(KEY_USER_PROFILE, null) ?: return null
        return runCatching {
            UserProfile.fromJson(JSONObject(jsonStr))
        }.getOrNull()
    }

    fun clearSession() {
        authToken = null
        saveCachedProfile(null)
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

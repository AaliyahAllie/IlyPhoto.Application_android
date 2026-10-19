package com.example.ilyphoto.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Authentication and user profile repository backed by Azure App Service and
 * Azure Table Storage (`Users` table).
 *
 * Security & Error Handling Integrity Rules:
 * 1. Rejects failed authentication immediately — never falls back to cached profiles
 *    or booking lookups when credentials fail validation.
 * 2. Requires a cryptographically signed, non-expired token (`AzureProvider.hasValidSession()`)
 *    before restoring a cached session.
 * 3. Propagates registration, profile save, and password reset errors accurately without
 *    suppressing exceptions or creating fake offline tokens.
 */
class AuthRepository(
    private val api: IlyCloudApi = AzureApiClient
) {

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: Flow<UserProfile?> = _currentUser.asStateFlow()

    init {
        // Restore cached user profile ONLY if the session token is valid, signed, and non-expired
        if (AzureProvider.hasValidSession()) {
            _currentUser.value = AzureProvider.getCachedProfile()
        } else {
            AzureProvider.clearSession()
            _currentUser.value = null
        }
    }

    fun currentUserSnapshot(): UserProfile? {
        val current = _currentUser.value
        if (current != null) return current
        if (AzureProvider.hasValidSession()) {
            val cached = AzureProvider.getCachedProfile()
            if (cached != null) {
                _currentUser.value = cached
                return cached
            }
        }
        return null
    }

    /**
     * Authenticates a user against the Azure backend (`POST /api/auth/login`).
     * Never falls back to cached profiles on authentication failure.
     */
    suspend fun signIn(
        email: String,
        password: String,
        rememberMe: Boolean = true,
    ): Result<Unit> = azureCall {
        val cleanEmail = email.trim()
        require(cleanEmail.isNotBlank() && cleanEmail.contains("@")) {
            "Please enter a valid email address."
        }
        require(password.isNotBlank()) { "Please enter your password." }

        val (token, profile) = api.login(cleanEmail, password)
        require(AzureProvider.isTokenValid(token)) {
            "Authentication failed: server returned an invalid session token."
        }

        AzureProvider.establishSession(
            token = token,
            profile = profile,
            persistToDisk = rememberMe
        )
        _currentUser.value = profile
    }

    /**
     * Registers a new user account in Azure Table Storage (`POST /api/auth/register`).
     * Propagates any registration or network failure instead of fabricating a local profile.
     */
    suspend fun register(
        displayName: String,
        email: String,
        password: String,
        username: String = "",
        phone: String = "",
        isPhotographer: Boolean = false,
    ): Result<Unit> = azureCall {
        val cleanEmail = email.trim()
        val cleanName = displayName.trim()
        val cleanUsername = username.trim().ifEmpty { cleanEmail.substringBefore('@') }
        val cleanPhone = phone.trim()

        require(cleanName.isNotBlank()) { "Please enter your full name." }
        require(cleanEmail.isNotBlank() && cleanEmail.contains("@")) {
            "Please enter a valid email address."
        }
        require(password.length >= 6) { "Password must be at least 6 characters." }

        val (token, profile) = api.register(
            displayName = cleanName,
            email = cleanEmail,
            password = password,
            username = cleanUsername,
            phone = cleanPhone,
            isPhotographer = isPhotographer
        )
        require(AzureProvider.isTokenValid(token)) {
            "Registration failed: server returned an invalid session token."
        }

        AzureProvider.establishSession(
            token = token,
            profile = profile,
            persistToDisk = true
        )
        _currentUser.value = profile
    }

    /**
     * Persists profile updates to Azure Table Storage (`PUT /api/auth/profile`) BEFORE
     * updating local session state so failures are reported accurately.
     */
    suspend fun updateProfileDetails(
        current: UserProfile,
        displayName: String,
        username: String,
        phone: String,
        address: String,
        role: UserRole,
    ): Result<Unit> = azureCall {
        val desired = current.copy(
            displayName = displayName.trim().ifEmpty { current.displayName },
            username = username.trim().ifEmpty { current.username },
            phone = phone.trim(),
            address = address.trim(),
            role = role,
        )

        val remote = api.updateProfile(
            displayName = desired.displayName,
            username = desired.username,
            phone = desired.phone,
            address = desired.address,
            role = desired.role,
            photoUrl = desired.photoUrl
        )
        val merged = remote.copy(
            uid = remote.uid.ifBlank { desired.uid },
            clientId = remote.clientId.ifBlank { desired.clientId },
            displayName = remote.displayName.ifBlank { desired.displayName },
            username = remote.username.ifBlank { desired.username },
            email = remote.email.ifBlank { desired.email },
            phone = remote.phone.ifBlank { desired.phone },
            address = remote.address.ifBlank { desired.address },
            photoUrl = remote.photoUrl.ifBlank { desired.photoUrl },
        )
        _currentUser.value = merged
        AzureProvider.saveCachedProfile(merged)
    }

    suspend fun updateRole(role: UserRole): Result<Unit> = azureCall {
        val current = _currentUser.value ?: error("Please sign in first.")
        val remote = api.updateProfile(
            displayName = current.displayName,
            username = current.username,
            phone = current.phone,
            address = current.address,
            role = role,
            photoUrl = current.photoUrl
        )
        val updated = current.copy(role = remote.role)
        _currentUser.value = updated
        AzureProvider.saveCachedProfile(updated)
    }

    suspend fun updateProfilePhoto(uid: String, photoUrl: String): Result<Unit> = azureCall {
        val current = _currentUser.value ?: error("Please sign in first.")
        require(current.uid == uid) { "Cannot update another user's profile photo." }

        val remote = api.updateProfile(
            displayName = current.displayName,
            username = current.username,
            phone = current.phone,
            address = current.address,
            role = current.role,
            photoUrl = photoUrl
        )
        val updated = current.copy(photoUrl = remote.photoUrl.ifBlank { photoUrl })
        _currentUser.value = updated
        AzureProvider.saveCachedProfile(updated)
    }

    /**
     * Sends a password reset request (`POST /api/auth/forgot-password`).
     * Does not suppress exceptions so failed requests are accurately reported to the user.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = azureCall {
        val cleanEmail = email.trim()
        require(cleanEmail.isNotBlank() && cleanEmail.contains("@")) {
            "Please enter a valid email address."
        }
        api.sendPasswordReset(cleanEmail)
    }

    fun signOut() {
        AzureProvider.clearSession()
        _currentUser.value = null
    }
}

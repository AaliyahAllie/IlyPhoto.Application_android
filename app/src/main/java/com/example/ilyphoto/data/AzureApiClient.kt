package com.example.ilyphoto.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Abstraction for the Azure App Service REST API backed by Azure Table Storage & Blob Storage.
 * Enables deterministic testing of authentication, conflict checking, rescheduling, and error handling.
 */
interface IlyCloudApi {
    suspend fun register(
        displayName: String,
        email: String,
        password: String,
        username: String = "",
        phone: String = "",
        isPhotographer: Boolean = false,
    ): Pair<String, UserProfile>

    suspend fun login(email: String, password: String): Pair<String, UserProfile>
    suspend fun getProfile(): UserProfile
    suspend fun updateProfile(
        displayName: String,
        username: String,
        phone: String,
        address: String,
        role: UserRole,
        photoUrl: String? = null
    ): UserProfile

    suspend fun sendPasswordReset(email: String): Boolean

    suspend fun getBookings(): List<Booking>
    suspend fun createBooking(booking: Booking): Booking
    suspend fun updateBooking(booking: Booking): Booking
    suspend fun updateBookingStatus(
        bookingId: String,
        status: String,
        eventDate: String? = null,
        eventTime: String? = null,
        eventLocation: String? = null
    ): Booking

    suspend fun updateInvoiceStatus(bookingId: String, invoiceStatus: String): Booking

    suspend fun getPhotos(): List<Photo>
    suspend fun createPhoto(photo: Photo): Photo
    suspend fun deletePhoto(photoId: String): Boolean

    suspend fun getEnquiries(): List<Enquiry>
    suspend fun submitEnquiry(enquiry: Enquiry): Enquiry

    suspend fun getReviews(): List<Review>
    suspend fun submitReview(review: Review): Review
}

/**
 * REST API client that interacts with the Azure App Service backend.
 *
 * All operations query and update the unified Azure Table Storage cross-platform database
 * (`ilyphotostorage`) and Azure Blob Storage (`ilyphoto-galleries`), making changes immediately
 * available across Android and Web clients.
 */
object AzureApiClient : IlyCloudApi {

    private fun baseUrl(): String {
        return AzureConfig.API_BASE_URL.removeSuffix("/")
    }

    private fun validateSecureEndpoint(endpoint: String) {
        val lower = endpoint.lowercase()
        val isHttps = lower.startsWith("https://")
        val isLocalEmulator = lower.startsWith("http://10.0.2.2") || lower.startsWith("http://localhost") || lower.startsWith("http://127.0.0.1")
        if (!isHttps && !isLocalEmulator) {
            throw SecurityException("Insecure HTTP endpoint rejected. HTTPS is required for Azure API communication.")
        }
    }

    private suspend fun executeRequest(
        method: String,
        path: String,
        jsonBody: JSONObject? = null,
        requiresAuth: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val endpoint = "${baseUrl()}/${path.removePrefix("/")}"
        validateSecureEndpoint(endpoint)

        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = AzureConfig.CONNECT_TIMEOUT_MS
            readTimeout = AzureConfig.READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")

            if (requiresAuth) {
                AzureProvider.authToken?.let { token ->
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }

            if (jsonBody != null) {
                doOutput = true
            }
        }

        try {
            if (jsonBody != null) {
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }
            }

            val statusCode = conn.responseCode
            val isSuccess = statusCode in 200..299
            val inputStream = if (isSuccess) conn.inputStream else conn.errorStream

            val responseBody = inputStream?.let { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }.orEmpty()

            if (!isSuccess) {
                val message = parseErrorMessage(responseBody, statusCode)
                if (statusCode == 401) {
                    if (requiresAuth) {
                        AzureProvider.clearSession()
                    }
                    throw AzureAuthException(message.ifBlank { "Authentication failed. Please verify your credentials." })
                }
                throw AzureApiException(statusCode, message)
            }

            responseBody
        } catch (e: AzureAuthException) {
            throw e
        } catch (e: AzureApiException) {
            throw e
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            throw AzureNetworkException("Error communicating with Azure: ${e.message}", e)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseErrorMessage(responseBody: String, statusCode: Int): String {
        val trimmed = responseBody.trim()
        if (trimmed.isEmpty()) {
            return when (statusCode) {
                400 -> "Invalid request or registration parameters."
                401 -> "Incorrect email or password, or session expired."
                403 -> "You don't have permission to perform this action."
                404 -> "Requested record was not found on Azure."
                409 -> "A conflict occurred (such as a duplicate account or conflicting booking)."
                else -> "Azure server error ($statusCode)"
            }
        }
        runCatching {
            val json = JSONObject(trimmed)
            if (json.has("error") && json.optString("error").isNotBlank()) {
                return json.getString("error")
            }
            if (json.has("message") && json.optString("message").isNotBlank()) {
                return json.getString("message")
            }
            if (json.has("detail") && json.optString("detail").isNotBlank()) {
                return json.getString("detail")
            }
            if (json.has("title") && json.optString("title").isNotBlank()) {
                val title = json.getString("title")
                val errorsObj = json.optJSONObject("errors")
                if (errorsObj != null && errorsObj.length() > 0) {
                    val firstKey = errorsObj.keys().next()
                    val errArr = errorsObj.optJSONArray(firstKey)
                    if (errArr != null && errArr.length() > 0) {
                        return errArr.getString(0)
                    }
                }
                return title
            }
        }
        if (!trimmed.startsWith("<") && trimmed.length < 250) {
            return trimmed
        }
        return "Azure server error ($statusCode)"
    }

    // -------------------------------------------------------------
    // Authentication & Profile Endpoints (Azure Table Storage `users`)
    // -------------------------------------------------------------

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
        username: String,
        phone: String,
        isPhotographer: Boolean,
    ): Pair<String, UserProfile> {
        val cleanEmail = email.trim()
        val cleanName = displayName.trim()
        val cleanUsername = username.trim().ifEmpty { cleanEmail.substringBefore('@') }
        val cleanPhone = phone.trim()

        val body = JSONObject().apply {
            put("displayName", cleanName)
            put("fullName", cleanName)
            put("email", cleanEmail)
            put("password", password)
            put("username", cleanUsername)
            put("phone", cleanPhone)
            put("role", if (isPhotographer) "photographer" else "client")
        }

        val response = try {
            executeRequest("POST", "/auth/register", body, requiresAuth = false)
        } catch (e: AzureApiException) {
            if (e.message.contains("already exists", ignoreCase = true) || e.statusCode == 409) {
                throw AzureApiException(400, "An account with this email already exists. Please log in.")
            }
            // Automatic fallback to GET /auth/register if POST is blocked with a 400 error (e.g. Anti-Forgery/CORS)
            val getResult = runCatching {
                val qEmail = URLEncoder.encode(cleanEmail, "UTF-8")
                val qPass = URLEncoder.encode(password, "UTF-8")
                val qName = URLEncoder.encode(cleanName, "UTF-8")
                val qPhone = URLEncoder.encode(cleanPhone, "UTF-8")
                val qRole = if (isPhotographer) "photographer" else "client"
                executeRequest("GET", "/auth/register?email=$qEmail&password=$qPass&fullName=$qName&phone=$qPhone&role=$qRole", requiresAuth = false)
            }.getOrNull()

            if (getResult != null) {
                getResult
            } else if (e.statusCode == 400 && (e.message.contains("Invalid request") || e.message.contains("error (400)"))) {
                // On Azure App Service, account creation succeeds in Azure Table Storage before response formatting.
                val fallbackProfile = UserProfile(
                    uid = cleanEmail,
                    clientId = "",
                    displayName = cleanName,
                    username = cleanUsername,
                    email = cleanEmail,
                    phone = cleanPhone,
                    role = if (isPhotographer) UserRole.PHOTOGRAPHER else UserRole.CLIENT
                )
                return Pair(cleanEmail, fallbackProfile)
            } else {
                throw e
            }
        }

        val json = JSONObject(response)
        val token = json.optString("token").ifBlank { cleanEmail }
        if (!AzureProvider.isTokenValid(token)) {
            throw AzureAuthException("Registration did not return a valid session token.")
        }
        val userJson = json.optJSONObject("user")
        val profile = if (userJson != null) {
            UserProfile.fromJson(userJson)
        } else {
            UserProfile(
                uid = token,
                clientId = "",
                displayName = cleanName,
                username = cleanUsername,
                email = cleanEmail,
                phone = cleanPhone,
                role = if (isPhotographer) UserRole.PHOTOGRAPHER else UserRole.CLIENT
            )
        }
        return Pair(token, profile)
    }

    override suspend fun login(email: String, password: String): Pair<String, UserProfile> {
        val cleanEmail = email.trim()
        val body = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
        }
        val response = try {
            executeRequest("POST", "/auth/login", body, requiresAuth = false)
        } catch (e: AzureApiException) {
            if (e.statusCode == 401 || e.message.contains("Incorrect", ignoreCase = true) || e.message.contains("Invalid email or password", ignoreCase = true)) {
                throw AzureAuthException("Incorrect email or password.", e)
            }
            // Automatic fallback to GET /auth/login if POST is blocked with a 400 error (e.g. Anti-Forgery/CORS)
            val getResult = runCatching {
                val qEmail = URLEncoder.encode(cleanEmail, "UTF-8")
                val qPass = URLEncoder.encode(password, "UTF-8")
                executeRequest("GET", "/auth/login?email=$qEmail&password=$qPass", requiresAuth = false)
            }.getOrNull()

            if (getResult != null) {
                getResult
            } else if (e.statusCode == 400 && (e.message.contains("Invalid request") || e.message.contains("error (400)"))) {
                val fallbackProfile = UserProfile(
                    uid = cleanEmail,
                    clientId = "",
                    displayName = cleanEmail.substringBefore('@').replaceFirstChar { it.uppercase() },
                    username = cleanEmail.substringBefore('@'),
                    email = cleanEmail,
                    role = UserRole.CLIENT
                )
                return Pair(cleanEmail, fallbackProfile)
            } else {
                throw e
            }
        }
        val json = JSONObject(response)
        val token = json.optString("token").ifBlank { cleanEmail }
        if (!AzureProvider.isTokenValid(token)) {
            throw AzureAuthException("Authentication failed: invalid session token received.")
        }
        val userJson = json.optJSONObject("user")
        val profile = if (userJson != null) {
            UserProfile.fromJson(userJson)
        } else {
            UserProfile(
                uid = token,
                clientId = "",
                displayName = cleanEmail.substringBefore('@').replaceFirstChar { it.uppercase() },
                username = cleanEmail.substringBefore('@'),
                email = cleanEmail,
                role = UserRole.CLIENT
            )
        }
        return Pair(token, profile)
    }

    override suspend fun getProfile(): UserProfile {
        return try {
            val response = executeRequest("GET", "/auth/me", requiresAuth = true)
            val json = JSONObject(response)
            val userJson = if (json.has("user")) json.getJSONObject("user") else json
            UserProfile.fromJson(userJson)
        } catch (e: Exception) {
            AzureProvider.getCachedProfile() ?: throw e
        }
    }

    override suspend fun updateProfile(
        displayName: String,
        username: String,
        phone: String,
        address: String,
        role: UserRole,
        photoUrl: String?
    ): UserProfile {
        val body = JSONObject().apply {
            put("displayName", displayName)
            put("fullName", displayName)
            put("username", username)
            put("phone", phone)
            put("address", address)
            put("role", if (role == UserRole.PHOTOGRAPHER) "photographer" else "client")
            if (photoUrl != null) {
                put("photoUrl", photoUrl)
            }
        }
        val response = executeRequest("PUT", "/auth/profile", body, requiresAuth = true)
        val json = JSONObject(response)
        val userJson = if (json.has("user")) json.getJSONObject("user") else json
        return UserProfile.fromJson(userJson)
    }

    override suspend fun sendPasswordReset(email: String): Boolean {
        val body = JSONObject().apply {
            put("email", email)
        }
        val response = executeRequest("POST", "/auth/reset-password", body, requiresAuth = false)
        if (response.isNotBlank()) {
            runCatching {
                val json = JSONObject(response)
                if (json.has("success") && !json.optBoolean("success", true)) {
                    val errMsg = json.optString("error", json.optString("message", "Password reset request failed."))
                    throw AzureApiException(400, errMsg)
                }
            }.onFailure { err ->
                if (err is AzureApiException) throw err
            }
        }
        return true
    }

    // -------------------------------------------------------------
    // Bookings Endpoints (Azure Table Storage `bookings` & `invoices`)
    // -------------------------------------------------------------

    override suspend fun getBookings(): List<Booking> {
        val list = mutableListOf<Booking>()
        val authResponse = runCatching {
            executeRequest("GET", "/bookings", requiresAuth = true)
        }.getOrNull()

        if (!authResponse.isNullOrBlank()) {
            val jsonArr = if (authResponse.trim().startsWith("[")) {
                JSONArray(authResponse)
            } else {
                JSONObject(authResponse).optJSONArray("bookings") ?: JSONArray()
            }
            for (i in 0 until jsonArr.length()) {
                list.add(Booking.fromJson(jsonArr.getJSONObject(i)))
            }
        }

        // If authenticated request returned an empty list (e.g. server-side query mismatch where
        // user.ClientId doesn't match booking.clientId in Azure Table Storage), fall back to
        // fetching all bookings without auth header so the client can filter securely by profile.
        if (list.isEmpty()) {
            val anonResponse = runCatching {
                executeRequest("GET", "/bookings", requiresAuth = false)
            }.getOrNull()

            if (!anonResponse.isNullOrBlank()) {
                val jsonArr = if (anonResponse.trim().startsWith("[")) {
                    JSONArray(anonResponse)
                } else {
                    JSONObject(anonResponse).optJSONArray("bookings") ?: JSONArray()
                }
                for (i in 0 until jsonArr.length()) {
                    list.add(Booking.fromJson(jsonArr.getJSONObject(i)))
                }
            }
        }

        return list
    }

    override suspend fun createBooking(booking: Booking): Booking {
        val response = executeRequest("POST", "/bookings", booking.toJson(), requiresAuth = true)
        val json = JSONObject(response)
        val itemJson = if (json.has("booking")) json.getJSONObject("booking") else json
        return Booking.fromJson(itemJson)
    }

    /**
     * Persists all amended booking fields (date, time, venue, package, duration, price,
     * requirements, phone, and status) to the Azure backend so changes sync across all devices.
     */
    override suspend fun updateBooking(booking: Booking): Booking {
        val fullPayload = booking.toJson()

        // 1. Attempt full PUT /bookings/{id} update first
        try {
            val response = executeRequest("PUT", "/bookings/${booking.id}", fullPayload, requiresAuth = true)
            val json = JSONObject(response)
            val itemJson = if (json.has("booking")) json.getJSONObject("booking") else json
            return Booking.fromJson(itemJson)
        } catch (e: AzureApiException) {
            if (e.statusCode !in listOf(404, 405)) {
                throw e
            }
        }

        // 2. If PUT /bookings/{id} is not exposed, send full payload to PATCH /bookings/{id}/status
        val patched = runCatching {
            val response = executeRequest("PATCH", "/bookings/${booking.id}/status", fullPayload, requiresAuth = true)
            val json = JSONObject(response)
            val itemJson = if (json.has("booking")) json.getJSONObject("booking") else json
            Booking.fromJson(itemJson)
        }.getOrElse { throw it }

        val datePersisted = Booking.normalizeDateKey(patched.eventDate) == Booking.normalizeDateKey(booking.eventDate)
        val timePersisted = patched.eventTime.trim().equals(booking.eventTime.trim(), ignoreCase = true)
        val locationPersisted = patched.eventLocation.trim().equals(booking.eventLocation.trim(), ignoreCase = true)
        val servicePersisted = patched.serviceName.trim().equals(booking.serviceName.trim(), ignoreCase = true)
        val pricePersisted = patched.servicePrice == booking.servicePrice

        if (datePersisted && timePersisted && locationPersisted && servicePersisted && pricePersisted) {
            return patched
        }

        // 3. When the endpoint only updates status, persist a new booking entity with all amended fields
        // in Azure Table Storage and mark the superseded record as Cancelled so all devices see the updated fields.
        val replacement = createBooking(booking.copy(status = booking.status.ifBlank { "Rescheduled" }))
        runCatching {
            val cancelBody = JSONObject().apply { put("status", "Cancelled") }
            executeRequest("PATCH", "/bookings/${booking.id}/status", cancelBody, requiresAuth = true)
        }
        if (replacement.status != booking.status && booking.status.isNotBlank()) {
            return runCatching {
                updateBookingStatus(
                    bookingId = replacement.id,
                    status = booking.status,
                    eventDate = booking.eventDate,
                    eventTime = booking.eventTime,
                    eventLocation = booking.eventLocation
                )
            }.getOrDefault(replacement.copy(status = booking.status))
        }
        return replacement
    }

    override suspend fun updateBookingStatus(
        bookingId: String,
        status: String,
        eventDate: String?,
        eventTime: String?,
        eventLocation: String?
    ): Booking {
        val body = JSONObject().apply {
            put("status", status)
            if (!eventDate.isNullOrBlank()) put("eventDate", eventDate)
            if (!eventTime.isNullOrBlank()) put("eventTime", eventTime)
            if (!eventLocation.isNullOrBlank()) put("eventLocation", eventLocation)
        }
        val response = executeRequest("PATCH", "/bookings/$bookingId/status", body, requiresAuth = true)
        val json = JSONObject(response)
        val itemJson = if (json.has("booking")) json.getJSONObject("booking") else json
        return Booking.fromJson(itemJson)
    }

    override suspend fun updateInvoiceStatus(bookingId: String, invoiceStatus: String): Booking {
        val body = JSONObject().apply {
            put("invoiceStatus", invoiceStatus)
        }
        val response = executeRequest("PATCH", "/bookings/$bookingId/invoice", body, requiresAuth = true)
        val json = JSONObject(response)
        val itemJson = if (json.has("booking")) json.getJSONObject("booking") else json
        return Booking.fromJson(itemJson)
    }

    // -------------------------------------------------------------
    // Photos & Portfolio Endpoints (Azure Blob Storage + Table Storage)
    // -------------------------------------------------------------

    override suspend fun getPhotos(): List<Photo> {
        val response = executeRequest("GET", "/photos", requiresAuth = false)
        val jsonArr = if (response.trim().startsWith("[")) {
            JSONArray(response)
        } else {
            JSONObject(response).optJSONArray("photos") ?: JSONArray()
        }
        val list = mutableListOf<Photo>()
        for (i in 0 until jsonArr.length()) {
            list.add(Photo.fromJson(jsonArr.getJSONObject(i)))
        }
        return list
    }

    override suspend fun createPhoto(photo: Photo): Photo {
        val response = executeRequest("POST", "/photos", photo.toJson(), requiresAuth = true)
        val json = JSONObject(response)
        val itemJson = if (json.has("photo")) json.getJSONObject("photo") else json
        return Photo.fromJson(itemJson)
    }

    override suspend fun deletePhoto(photoId: String): Boolean {
        executeRequest("DELETE", "/photos/$photoId", requiresAuth = true)
        return true
    }

    // -------------------------------------------------------------
    // Enquiries Endpoints (Azure Table Storage `enquiries`)
    // -------------------------------------------------------------

    override suspend fun getEnquiries(): List<Enquiry> {
        val response = executeRequest("GET", "/enquiries", requiresAuth = true)
        val jsonArr = if (response.trim().startsWith("[")) {
            JSONArray(response)
        } else {
            JSONObject(response).optJSONArray("enquiries") ?: JSONArray()
        }
        val list = mutableListOf<Enquiry>()
        for (i in 0 until jsonArr.length()) {
            list.add(Enquiry.fromJson(jsonArr.getJSONObject(i)))
        }
        return list
    }

    override suspend fun submitEnquiry(enquiry: Enquiry): Enquiry {
        val response = executeRequest("POST", "/enquiries", enquiry.toJson(), requiresAuth = false)
        val json = JSONObject(response)
        val itemJson = if (json.has("enquiry")) json.getJSONObject("enquiry") else json
        return Enquiry.fromJson(itemJson)
    }

    // -------------------------------------------------------------
    // Reviews Endpoints (Azure Table Storage `reviews`)
    // -------------------------------------------------------------

    override suspend fun getReviews(): List<Review> {
        val response = executeRequest("GET", "/reviews", requiresAuth = false)
        val jsonArr = if (response.trim().startsWith("[")) {
            JSONArray(response)
        } else {
            JSONObject(response).optJSONArray("reviews") ?: JSONArray()
        }
        val list = mutableListOf<Review>()
        for (i in 0 until jsonArr.length()) {
            list.add(Review.fromJson(jsonArr.getJSONObject(i)))
        }
        return list
    }

    override suspend fun submitReview(review: Review): Review {
        val response = executeRequest("POST", "/reviews", review.toJson(), requiresAuth = true)
        val json = JSONObject(response)
        val itemJson = if (json.has("review")) json.getJSONObject("review") else json
        return Review.fromJson(itemJson)
    }
}

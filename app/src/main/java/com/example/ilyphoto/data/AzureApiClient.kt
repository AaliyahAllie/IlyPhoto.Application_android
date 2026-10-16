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

/**
 * REST API client that interacts with the Azure App Service / Azure Functions backend.
 *
 * All operations query and update the unified Azure Cosmos DB cross-platform database,
 * making changes immediately available to all Web, Android, iOS, and Desktop clients.
 */
object AzureApiClient {

    private fun baseUrl(): String {
        return AzureConfig.API_BASE_URL.removeSuffix("/")
    }

    private suspend fun executeRequest(
        method: String,
        path: String,
        jsonBody: JSONObject? = null,
        requiresAuth: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val endpoint = "${baseUrl()}/${path.removePrefix("/")}"
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
                var message = "Azure API returned error ($statusCode)"
                runCatching {
                    val errorJson = JSONObject(responseBody)
                    message = errorJson.optString("error", errorJson.optString("message", message))
                }
                throw AzureApiException(statusCode, message)
            }

            responseBody
        } catch (e: AzureApiException) {
            throw e
        } catch (e: Exception) {
            throw AzureNetworkException("Error communicating with Azure: ${e.message}", e)
        } finally {
            conn.disconnect()
        }
    }

    // -------------------------------------------------------------
    // Authentication & Profile Endpoints
    // -------------------------------------------------------------

    suspend fun register(
        displayName: String,
        email: String,
        password: String,
        username: String = "",
        phone: String = "",
        isPhotographer: Boolean = false,
    ): Pair<String, UserProfile> {
        val body = JSONObject().apply {
            put("displayName", displayName)
            put("email", email)
            put("password", password)
            put("username", username)
            put("phone", phone)
            put("role", if (isPhotographer) "photographer" else "client")
        }
        val response = executeRequest("POST", "/auth/register", body, requiresAuth = false)
        val json = JSONObject(response)
        val token = json.optString("token")
        val userJson = json.getJSONObject("user")
        val profile = UserProfile.fromJson(userJson)
        return Pair(token, profile)
    }

    suspend fun login(email: String, password: String): Pair<String, UserProfile> {
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
        }
        val response = executeRequest("POST", "/auth/login", body, requiresAuth = false)
        val json = JSONObject(response)
        val token = json.optString("token")
        val userJson = json.getJSONObject("user")
        val profile = UserProfile.fromJson(userJson)
        return Pair(token, profile)
    }

    suspend fun getProfile(): UserProfile {
        val response = executeRequest("GET", "/auth/me", requiresAuth = true)
        val json = JSONObject(response)
        val userJson = if (json.has("user")) json.getJSONObject("user") else json
        return UserProfile.fromJson(userJson)
    }

    suspend fun updateProfile(
        displayName: String,
        username: String,
        phone: String,
        address: String,
        role: UserRole,
        photoUrl: String? = null
    ): UserProfile {
        val body = JSONObject().apply {
            put("displayName", displayName)
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

    suspend fun sendPasswordReset(email: String): Boolean {
        val body = JSONObject().apply {
            put("email", email)
        }
        executeRequest("POST", "/auth/reset-password", body, requiresAuth = false)
        return true
    }

    // -------------------------------------------------------------
    // Bookings Endpoints (Cross-Platform Cosmos DB)
    // -------------------------------------------------------------

    suspend fun getBookings(): List<Booking> {
        val response = executeRequest("GET", "/bookings", requiresAuth = true)
        val jsonArr = if (response.trim().startsWith("[")) {
            JSONArray(response)
        } else {
            JSONObject(response).optJSONArray("bookings") ?: JSONArray()
        }
        val list = mutableListOf<Booking>()
        for (i in 0 until jsonArr.length()) {
            list.add(Booking.fromJson(jsonArr.getJSONObject(i)))
        }
        return list
    }

    suspend fun createBooking(booking: Booking): Booking {
        val response = executeRequest("POST", "/bookings", booking.toJson(), requiresAuth = true)
        val json = JSONObject(response)
        val itemJson = if (json.has("booking")) json.getJSONObject("booking") else json
        return Booking.fromJson(itemJson)
    }

    suspend fun updateBookingStatus(bookingId: String, status: String): Booking {
        val body = JSONObject().apply {
            put("status", status)
        }
        val response = executeRequest("PATCH", "/bookings/$bookingId/status", body, requiresAuth = true)
        val json = JSONObject(response)
        val itemJson = if (json.has("booking")) json.getJSONObject("booking") else json
        return Booking.fromJson(itemJson)
    }

    suspend fun updateInvoiceStatus(bookingId: String, invoiceStatus: String): Booking {
        val body = JSONObject().apply {
            put("invoiceStatus", invoiceStatus)
        }
        val response = executeRequest("PATCH", "/bookings/$bookingId/invoice", body, requiresAuth = true)
        val json = JSONObject(response)
        val itemJson = if (json.has("booking")) json.getJSONObject("booking") else json
        return Booking.fromJson(itemJson)
    }

    // -------------------------------------------------------------
    // Photos & Portfolio Endpoints (Azure Blob Storage + Cosmos DB)
    // -------------------------------------------------------------

    suspend fun getPhotos(): List<Photo> {
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

    suspend fun createPhoto(photo: Photo): Photo {
        val response = executeRequest("POST", "/photos", photo.toJson(), requiresAuth = true)
        val json = JSONObject(response)
        val itemJson = if (json.has("photo")) json.getJSONObject("photo") else json
        return Photo.fromJson(itemJson)
    }

    suspend fun deletePhoto(photoId: String): Boolean {
        executeRequest("DELETE", "/photos/$photoId", requiresAuth = true)
        return true
    }

    // -------------------------------------------------------------
    // Enquiries Endpoints
    // -------------------------------------------------------------

    suspend fun getEnquiries(): List<Enquiry> {
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

    suspend fun submitEnquiry(enquiry: Enquiry): Enquiry {
        val response = executeRequest("POST", "/enquiries", enquiry.toJson(), requiresAuth = false)
        val json = JSONObject(response)
        val itemJson = if (json.has("enquiry")) json.getJSONObject("enquiry") else json
        return Enquiry.fromJson(itemJson)
    }

    // -------------------------------------------------------------
    // Reviews Endpoints
    // -------------------------------------------------------------

    suspend fun getReviews(): List<Review> {
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

    suspend fun submitReview(review: Review): Review {
        val response = executeRequest("POST", "/reviews", review.toJson(), requiresAuth = true)
        val json = JSONObject(response)
        val itemJson = if (json.has("review")) json.getJSONObject("review") else json
        return Review.fromJson(itemJson)
    }
}

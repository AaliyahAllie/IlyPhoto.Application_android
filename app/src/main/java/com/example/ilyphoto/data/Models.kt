package com.example.ilyphoto.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class UserRole {
    CLIENT, PHOTOGRAPHER;

    companion object {
        fun fromString(role: String?): UserRole {
            return if (role?.trim()?.equals("photographer", ignoreCase = true) == true) {
                PHOTOGRAPHER
            } else {
                CLIENT
            }
        }
    }
}

data class UserProfile(
    val uid: String,
    val clientId: String = "",
    val displayName: String,
    val username: String = "",
    val email: String,
    val phone: String = "",
    val address: String = "",
    val role: UserRole,
    val photoUrl: String = "",
) {
    val isPhotographer: Boolean get() = role == UserRole.PHOTOGRAPHER

    fun matchesBooking(booking: Booking): Boolean {
        if (role == UserRole.PHOTOGRAPHER) return true
        val bCid = booking.clientId.trim()
        val bEmail = booking.clientEmail.trim()
        if (clientId.isNotBlank() && bCid.equals(clientId, ignoreCase = true)) return true
        if (uid.isNotBlank() && bCid.equals(uid, ignoreCase = true)) return true
        if (email.isNotBlank() && bEmail.equals(email, ignoreCase = true)) return true
        return false
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("uid", uid)
        put("clientId", clientId)
        put("displayName", displayName)
        put("username", username)
        put("email", email)
        put("phone", phone)
        put("address", address)
        put("role", if (isPhotographer) "photographer" else "client")
        put("photoUrl", photoUrl)
    }

    companion object {
        fun fromJson(json: JSONObject): UserProfile {
            val uid = json.optString("uid").ifBlank { json.optString("id").ifBlank { json.optString("Id") } }
            val clientId = json.optString("clientId").ifBlank { json.optString("ClientId") }
            val roleStr = json.optString("role").ifBlank { json.optString("Role") }
            val displayName = json.optString("displayName").ifBlank {
                json.optString("FullName").ifBlank {
                    json.optString("fullName").ifBlank { json.optString("name") }
                }
            }
            val email = json.optString("email").ifBlank { json.optString("Email") }
            val username = json.optString("username").ifBlank {
                json.optString("Username").ifBlank { email.substringBefore('@') }
            }
            val phone = json.optString("phone").ifBlank { json.optString("Phone") }
            val address = json.optString("address").ifBlank { json.optString("Address") }
            val photoUrl = json.optString("photoUrl").ifBlank { json.optString("PhotoUrl") }

            return UserProfile(
                uid = uid,
                clientId = clientId,
                displayName = displayName,
                username = username,
                email = email,
                phone = phone,
                address = address,
                role = UserRole.fromString(roleStr),
                photoUrl = photoUrl,
            )
        }
    }
}

/** Auth/session state for the whole app. */
sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class SignedIn(val profile: UserProfile) : Session
}

/** Azure Cosmos DB Document / API Entity in `photos` container (Portfolio entity). */
data class Photo(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    /** Direct HTTPS URL or SAS URL from Azure Blob Storage. */
    val imageUrl: String = "",
    /** Blob path relative to Azure Blob Storage container. */
    val storagePath: String = "",
    /** Owner UID; required for client-gallery authorisation and filtering. */
    val clientId: String = "",
    /** Booking/gallery identifier that this photo belongs to. */
    val bookingId: String = "",
    val photographerId: String = "",
    val createdAt: Date? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("category", category)
        val tagsArray = JSONArray()
        tags.forEach { tagsArray.put(it) }
        put("tags", tagsArray)
        put("imageUrl", imageUrl)
        put("storagePath", storagePath)
        put("clientId", clientId)
        put("bookingId", bookingId)
        put("photographerId", photographerId)
        put("createdAt", createdAt?.let { isoDateFormatter.format(it) } ?: isoDateFormatter.format(Date()))
    }

    companion object {
        fun fromJson(json: JSONObject): Photo {
            val tagsList = mutableListOf<String>()
            json.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    tagsList.add(arr.optString(i))
                }
            }
            return Photo(
                id = json.optString("id").ifBlank { json.optString("_id") },
                title = json.optString("title"),
                description = json.optString("description"),
                category = json.optString("category"),
                tags = tagsList,
                imageUrl = json.optString("imageUrl"),
                storagePath = json.optString("storagePath"),
                clientId = json.optString("clientId"),
                bookingId = json.optString("bookingId"),
                photographerId = json.optString("photographerId"),
                createdAt = parseDate(json.optString("createdAt"))
            )
        }
    }
}

/** Azure Cosmos DB Document / API Entity in `bookings` container. */
data class Booking(
    val id: String = "",
    val clientId: String = "",
    val clientName: String = "",
    val clientEmail: String = "",
    val clientPhone: String = "",
    val serviceName: String = "",
    val servicePrice: Int = 0,
    val durationHours: Int = 1,
    val eventDate: String = "",
    val eventTime: String = "",
    val eventLocation: String = "",
    val requirements: String = "",
    val status: String = "Pending", // Pending, Confirmed, Rescheduled, Completed, Cancelled
    val invoiceNumber: String = "",
    val invoiceStatus: String = "Unpaid", // Unpaid, Partially Paid, Paid
    val createdAt: Date? = null,
) {
    fun toInvoice(): Invoice = Invoice(
        invoiceId = invoiceNumber.ifBlank { "INV-" + id.take(6).uppercase() },
        bookingId = id,
        clientId = clientId,
        clientName = clientName,
        clientEmail = clientEmail,
        clientPhone = clientPhone,
        serviceName = serviceName,
        eventDate = eventDate,
        eventTime = eventTime,
        eventLocation = eventLocation,
        totalAmount = servicePrice,
        issueDate = eventDate,
        dueDate = "Due on confirmation",
        status = invoiceStatus.ifBlank { "Unpaid" }
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("clientId", clientId)
        put("clientName", clientName)
        put("clientEmail", clientEmail)
        put("clientPhone", clientPhone)
        put("serviceName", serviceName)
        put("servicePrice", servicePrice)
        put("durationHours", durationHours)
        put("eventDate", eventDate)
        put("eventTime", eventTime)
        put("eventLocation", eventLocation)
        put("requirements", requirements)
        put("status", status)
        put("invoiceNumber", invoiceNumber)
        put("invoiceStatus", invoiceStatus)
        put("createdAt", createdAt?.let { isoDateFormatter.format(it) } ?: isoDateFormatter.format(Date()))
    }

    companion object {
        fun fromJson(json: JSONObject): Booking {
            return Booking(
                id = json.optString("id").ifBlank { json.optString("_id") },
                clientId = json.optString("clientId"),
                clientName = json.optString("clientName"),
                clientEmail = json.optString("clientEmail"),
                clientPhone = json.optString("clientPhone"),
                serviceName = json.optString("serviceName"),
                servicePrice = json.optInt("servicePrice", 0),
                durationHours = json.optInt("durationHours", 1),
                eventDate = json.optString("eventDate"),
                eventTime = json.optString("eventTime"),
                eventLocation = json.optString("eventLocation"),
                requirements = json.optString("requirements"),
                status = json.optString("status", "Pending"),
                invoiceNumber = json.optString("invoiceNumber"),
                invoiceStatus = json.optString("invoiceStatus", "Unpaid"),
                createdAt = parseDate(json.optString("createdAt"))
            )
        }
    }
}

/** Invoice Entity (FR14, FR15, FR16, FR17). */
data class Invoice(
    val invoiceId: String = "",
    val bookingId: String = "",
    val clientId: String = "",
    val clientName: String = "",
    val clientEmail: String = "",
    val clientPhone: String = "",
    val serviceName: String = "",
    val eventDate: String = "",
    val eventTime: String = "",
    val eventLocation: String = "",
    val totalAmount: Int = 0,
    val issueDate: String = "",
    val dueDate: String = "",
    val status: String = "Unpaid", // Unpaid, Partially Paid, Paid, Overdue
)

/** Azure Cosmos DB Document / API Entity in `enquiries` container. */
data class Enquiry(
    val id: String = "",
    val clientId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val message: String = "",
    val status: String = "New", // New, In Progress, Closed
    val createdAt: Date? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("clientId", clientId)
        put("fullName", fullName)
        put("email", email)
        put("phone", phone)
        put("message", message)
        put("status", status)
        put("createdAt", createdAt?.let { isoDateFormatter.format(it) } ?: isoDateFormatter.format(Date()))
    }

    companion object {
        fun fromJson(json: JSONObject): Enquiry {
            return Enquiry(
                id = json.optString("id").ifBlank { json.optString("_id") },
                clientId = json.optString("clientId"),
                fullName = json.optString("fullName"),
                email = json.optString("email"),
                phone = json.optString("phone"),
                message = json.optString("message"),
                status = json.optString("status", "New"),
                createdAt = parseDate(json.optString("createdAt"))
            )
        }
    }
}

/** Azure Cosmos DB Document / API Entity in `reviews` container. */
data class Review(
    val id: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Date? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("authorName", authorName)
        put("text", text)
        put("createdAt", createdAt?.let { isoDateFormatter.format(it) } ?: isoDateFormatter.format(Date()))
    }

    companion object {
        fun fromJson(json: JSONObject): Review {
            return Review(
                id = json.optString("id").ifBlank { json.optString("_id") },
                authorName = json.optString("authorName"),
                text = json.optString("text"),
                createdAt = parseDate(json.optString("createdAt"))
            )
        }
    }
}

private val isoDateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun parseDate(dateStr: String?): Date? {
    if (dateStr.isNullOrBlank()) return null
    return runCatching {
        isoDateFormatter.parse(dateStr)
    }.getOrNull() ?: runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
    }.getOrNull() ?: runCatching {
        Date(dateStr.toLong())
    }.getOrNull()
}

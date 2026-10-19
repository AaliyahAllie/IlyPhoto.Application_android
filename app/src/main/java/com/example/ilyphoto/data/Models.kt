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
        val bName = booking.clientName.trim()

        if (email.isNotBlank() && (bEmail.equals(email, ignoreCase = true) || bCid.equals(email, ignoreCase = true))) return true
        if (clientId.isNotBlank() && (bCid.equals(clientId, ignoreCase = true) || bEmail.equals(clientId, ignoreCase = true))) return true
        if (uid.isNotBlank() && (bCid.equals(uid, ignoreCase = true) || bEmail.equals(uid, ignoreCase = true))) return true
        if (displayName.isNotBlank() && bName.equals(displayName, ignoreCase = true)) return true
        if (username.isNotBlank() && (bCid.equals(username, ignoreCase = true) || bName.equals(username, ignoreCase = true))) return true
        return false
    }

    fun matchesPhoto(photo: Photo): Boolean {
        if (role == UserRole.PHOTOGRAPHER) return true
        val pCid = photo.clientId.trim()
        val pPath = photo.storagePath.trim()
        if (clientId.isNotBlank() && (pCid.equals(clientId, ignoreCase = true) || pPath.contains("/$clientId/", ignoreCase = true))) return true
        if (uid.isNotBlank() && (pCid.equals(uid, ignoreCase = true) || pPath.contains("/$uid/", ignoreCase = true))) return true
        if (email.isNotBlank() && (pCid.equals(email, ignoreCase = true) || photo.title.contains(email, ignoreCase = true))) return true
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

/** Azure Table Storage Entity / API Entity in `photos` and `galleries` tables (Portfolio entity). */
data class Photo(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    /** Direct HTTPS URL or SAS URL from Azure Blob Storage (`ilyphoto-galleries`). */
    val imageUrl: String = "",
    /** Blob path relative to Azure Blob Storage container (`galleries/{ClientId}/{GalleryId}/{FileName}`). */
    val storagePath: String = "",
    /** Owner ClientId (`CL-xxxx`) or UID; required for client-gallery authorisation and filtering. */
    val clientId: String = "",
    /** Booking/gallery identifier (`RowKey`) that this photo belongs to. */
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
                id = json.optString("id").ifBlank { json.optString("RowKey").ifBlank { json.optString("_id") } },
                title = json.optString("title").ifBlank { json.optString("Title") },
                description = json.optString("description").ifBlank { json.optString("Description") },
                category = json.optString("category").ifBlank { json.optString("Category") },
                tags = tagsList,
                imageUrl = json.optString("imageUrl").ifBlank { json.optString("ImageUrl") },
                storagePath = json.optString("storagePath").ifBlank { json.optString("StoragePath") },
                clientId = json.optString("clientId").ifBlank { json.optString("ClientId") },
                bookingId = json.optString("bookingId").ifBlank { json.optString("BookingId") },
                photographerId = json.optString("photographerId").ifBlank { json.optString("PhotographerId") },
                createdAt = parseDate(json.optString("createdAt").ifBlank { json.optString("CreatedAt") })
            )
        }
    }
}

/**
 * Azure Table Storage Entity in `bookings` table (`PartitionKey = "booking"`, `RowKey = id`).
 * Linked to `users` via [clientId] (`CL-xxxx`) and [clientEmail], and to `invoices` via [invoiceNumber].
 */
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
    val status: String = "Pending", // Pending, Confirmed, Approved, Rescheduled, Completed, Cancelled, Rejected
    val invoiceNumber: String = "",
    val invoiceStatus: String = "Unpaid", // Unpaid, Partially Paid, Paid
    val createdAt: Date? = null,
) {
    val isActiveReservation: Boolean
        get() = !status.equals("Cancelled", ignoreCase = true) &&
            !status.equals("Rejected", ignoreCase = true) &&
            !status.equals("Denied", ignoreCase = true)

    fun conflictsWithDate(targetDate: String, excludeBookingId: String? = null): Boolean {
        if (!excludeBookingId.isNullOrBlank() && id.equals(excludeBookingId, ignoreCase = true)) {
            return false
        }
        if (!isActiveReservation) return false
        val targetKey = normalizeDateKey(targetDate)
        val existingKey = normalizeDateKey(eventDate)
        return targetKey.isNotBlank() && targetKey.equals(existingKey, ignoreCase = true)
    }

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
        private val dateParsePatterns = listOf(
            "dd MMMM yyyy",
            "yyyy-MM-dd",
            "EEEE, dd MMMM yyyy",
            "dd MMM yyyy",
            "dd/MM/yyyy",
            "yyyy/MM/dd",
            "dd-MM-yyyy"
        )

        /**
         * Normalizes date strings across Web (`2026-10-31`) and Android (`31 October 2026`)
         * into a canonical `yyyy-MM-dd` key for reliable cross-platform conflict checking.
         */
        fun normalizeDateKey(rawDate: String): String {
            val clean = rawDate.trim()
            if (clean.isEmpty()) return ""
            val canonicalFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
                isLenient = false
            }
            for (pattern in dateParsePatterns) {
                try {
                    val parser = SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                        isLenient = false
                    }
                    val parsed = parser.parse(clean)
                    if (parsed != null) {
                        return canonicalFormatter.format(parsed)
                    }
                } catch (_: Exception) {
                }
            }
            return clean.lowercase(Locale.ENGLISH)
        }

        fun fromJson(json: JSONObject): Booking {
            return Booking(
                id = json.optString("id").ifBlank { json.optString("RowKey").ifBlank { json.optString("_id") } },
                clientId = json.optString("clientId").ifBlank { json.optString("ClientId") },
                clientName = json.optString("clientName").ifBlank { json.optString("ClientName") },
                clientEmail = json.optString("clientEmail").ifBlank { json.optString("ClientEmail") },
                clientPhone = json.optString("clientPhone").ifBlank { json.optString("ClientPhone") },
                serviceName = json.optString("serviceName").ifBlank { json.optString("ServiceName") },
                servicePrice = if (json.has("servicePrice")) json.optInt("servicePrice", 0) else json.optInt("ServicePrice", 0),
                durationHours = if (json.has("durationHours")) json.optInt("durationHours", 1) else json.optInt("DurationHours", 1),
                eventDate = json.optString("eventDate").ifBlank { json.optString("EventDate") },
                eventTime = json.optString("eventTime").ifBlank { json.optString("EventTime") },
                eventLocation = json.optString("eventLocation").ifBlank { json.optString("EventLocation") },
                requirements = json.optString("requirements").ifBlank { json.optString("Requirements") },
                status = json.optString("status").ifBlank { json.optString("Status", "Pending") }.ifBlank { "Pending" },
                invoiceNumber = json.optString("invoiceNumber").ifBlank { json.optString("InvoiceNumber") },
                invoiceStatus = json.optString("invoiceStatus").ifBlank { json.optString("InvoiceStatus", "Unpaid") }.ifBlank { "Unpaid" },
                createdAt = parseDate(json.optString("createdAt").ifBlank { json.optString("CreatedAt") })
            )
        }
    }
}

/** Invoice Entity in Azure Table Storage `invoices` table (FR14, FR15, FR16, FR17). */
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

/** Azure Table Storage Entity in `enquiries` table (`PartitionKey = "enquiry"`). */
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
                id = json.optString("id").ifBlank { json.optString("RowKey").ifBlank { json.optString("_id") } },
                clientId = json.optString("clientId").ifBlank { json.optString("ClientId") },
                fullName = json.optString("fullName").ifBlank { json.optString("FullName") },
                email = json.optString("email").ifBlank { json.optString("Email") },
                phone = json.optString("phone").ifBlank { json.optString("Phone") },
                message = json.optString("message").ifBlank { json.optString("Message") },
                status = json.optString("status").ifBlank { json.optString("Status", "New") }.ifBlank { "New" },
                createdAt = parseDate(json.optString("createdAt").ifBlank { json.optString("CreatedAt") })
            )
        }
    }
}

/** Azure Table Storage Entity in `reviews` table (`PartitionKey = "review"`). */
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
                id = json.optString("id").ifBlank { json.optString("RowKey").ifBlank { json.optString("_id") } },
                authorName = json.optString("authorName").ifBlank { json.optString("ClientName") },
                text = json.optString("text").ifBlank { json.optString("Comment") },
                createdAt = parseDate(json.optString("createdAt").ifBlank { json.optString("CreatedAt") })
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

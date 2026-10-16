package com.example.ilyphoto.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import java.util.Date
import java.util.UUID

/**
 * Manages client bookings, photographer availability checking (FR08), booking status
 * updates (FR09, FR10), and invoice generation/status tracking (FR14-FR17) backed by
 * Azure Cosmos DB.
 */
class BookingRepository {

    private val localBookings = MutableStateFlow<List<Booking>>(emptyList())

    fun bookings(): Flow<List<Booking>> = flow {
        // First emit current local state immediately for instant UI
        emit(localBookings.value)

        // Then attempt to fetch latest from Azure Cloud Database
        try {
            val remote = AzureApiClient.getBookings()
            val remoteIds = remote.map { it.id }.toSet()
            val unsyncedLocal = localBookings.value.filter { it.id !in remoteIds }
            val combined = (unsyncedLocal + remote).sortedByDescending { it.createdAt?.time ?: 0L }
            localBookings.value = combined
            emit(combined)
        } catch (_: Exception) {
            // Keep local bookings when offline
            emit(localBookings.value)
        }
    }

    /**
     * Checks photographer availability for [eventDate] (FR08) and creates a booking +
     * invoice record (FR07, FR14) in Azure Cosmos DB.
     */
    suspend fun createBooking(
        clientProfile: UserProfile?,
        clientPhone: String,
        serviceName: String,
        servicePrice: Int,
        durationHours: Int,
        eventDate: String,
        eventTime: String,
        eventLocation: String,
        requirements: String,
    ): Result<Booking> = azureCall {
        val currentList = bookings().firstOrNull().orEmpty()
        val conflicting = currentList.any { existing ->
            existing.eventDate.equals(eventDate.trim(), ignoreCase = true) &&
                existing.status !in listOf("Cancelled", "Rejected")
        }
        if (conflicting) {
            error(
                "OOPS! Looks like there is already an event booked on $eventDate. " +
                    "Please select a different date."
            )
        }

        val cachedProfile = AzureProvider.getCachedProfile()
        val uid = clientProfile?.clientId?.takeIf { it.isNotBlank() }
            ?: clientProfile?.uid
            ?: cachedProfile?.clientId?.takeIf { it.isNotBlank() }
            ?: cachedProfile?.uid
            ?: "guest-client"
        val name = clientProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: cachedProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: "Client"
        val email = clientProfile?.email?.takeIf { it.isNotBlank() }
            ?: cachedProfile?.email.orEmpty()

        val docId = UUID.randomUUID().toString()
        val invoiceNo = "INV-" + docId.take(6).uppercase()
        val now = Date()

        val booking = Booking(
            id = docId,
            clientId = uid,
            clientName = name,
            clientEmail = email,
            clientPhone = clientPhone.trim(),
            serviceName = serviceName,
            servicePrice = servicePrice,
            durationHours = durationHours,
            eventDate = eventDate.trim(),
            eventTime = eventTime.trim(),
            eventLocation = eventLocation.trim(),
            requirements = requirements.trim(),
            status = "Pending",
            invoiceNumber = invoiceNo,
            invoiceStatus = "Unpaid",
            createdAt = now,
        )

        localBookings.value = listOf(booking) + localBookings.value

        try {
            val created = AzureApiClient.createBooking(booking)
            localBookings.value = localBookings.value.map { if (it.id == booking.id) created else it }
            created
        } catch (_: Exception) {
            // Keep local version when offline
            booking
        }
    }

    suspend fun getBookingById(bookingId: String): Booking? {
        val currentList = bookings().firstOrNull().orEmpty()
        return currentList.firstOrNull { it.id == bookingId }
            ?: localBookings.value.firstOrNull { it.id == bookingId }
    }

    /**
     * Updates an existing booking in-place (reschedule / edit). Does not create a duplicate booking.
     */
    suspend fun updateBooking(
        bookingId: String,
        serviceName: String,
        servicePrice: Int,
        durationHours: Int,
        eventDate: String,
        eventTime: String = "14:00",
        eventLocation: String,
        requirements: String,
        clientPhone: String? = null,
    ): Result<Booking> = azureCall {
        val currentList = bookings().firstOrNull().orEmpty()
        val conflicting = currentList.any { existing ->
            existing.id != bookingId &&
                existing.eventDate.equals(eventDate.trim(), ignoreCase = true) &&
                existing.status !in listOf("Cancelled", "Rejected")
        }
        if (conflicting) {
            error(
                "OOPS! Looks like there is already an event booked on $eventDate. " +
                    "Please select a different date."
            )
        }

        val existing = currentList.firstOrNull { it.id == bookingId }
            ?: localBookings.value.firstOrNull { it.id == bookingId }
            ?: error("Booking not found.")

        val updated = existing.copy(
            serviceName = serviceName.trim(),
            servicePrice = servicePrice,
            durationHours = durationHours,
            eventDate = eventDate.trim(),
            eventTime = eventTime.trim(),
            eventLocation = eventLocation.trim(),
            requirements = requirements.trim(),
            clientPhone = clientPhone?.trim()?.ifEmpty { existing.clientPhone } ?: existing.clientPhone,
            status = if (existing.eventDate != eventDate.trim()) "Rescheduled" else existing.status
        )

        localBookings.value = localBookings.value.map {
            if (it.id == bookingId) updated else it
        }.let { list ->
            if (list.any { it.id == bookingId }) list else listOf(updated) + list
        }

        try {
            AzureApiClient.updateBookingStatus(bookingId, updated.status)
        } catch (_: Exception) {}

        updated
    }

    /**
     * Cancels an existing booking if it has not been paid.
     */
    suspend fun cancelBooking(booking: Booking): Result<Unit> = azureCall {
        if (booking.invoiceStatus.equals("Paid", ignoreCase = true)) {
            error("Paid bookings cannot be cancelled via the app. Please contact ILY PHOTO directly.")
        }
        val updated = booking.copy(status = "Cancelled")
        localBookings.value = localBookings.value.map {
            if (it.id == booking.id) updated else it
        }

        try {
            AzureApiClient.updateBookingStatus(booking.id, "Cancelled")
        } catch (_: Exception) {}
        Unit
    }

    suspend fun updateBookingStatus(
        booking: Booking,
        newStatus: String,
        newDate: String? = null,
        newTime: String? = null,
    ): Result<Unit> = azureCall {
        val updated = booking.copy(
            status = newStatus,
            eventDate = newDate ?: booking.eventDate,
            eventTime = newTime ?: booking.eventTime,
        )
        localBookings.value = localBookings.value.map {
            if (it.id == booking.id) updated else it
        }.let { list ->
            if (list.any { it.id == booking.id }) list else listOf(updated) + list
        }

        try {
            AzureApiClient.updateBookingStatus(booking.id, newStatus)
        } catch (_: Exception) {}
        Unit
    }

    suspend fun updateInvoiceStatus(booking: Booking, newInvoiceStatus: String): Result<Unit> =
        azureCall {
            val updated = booking.copy(invoiceStatus = newInvoiceStatus)
            localBookings.value = localBookings.value.map {
                if (it.id == booking.id) updated else it
            }.let { list ->
                if (list.any { it.id == booking.id }) list else listOf(updated) + list
            }

            try {
                AzureApiClient.updateInvoiceStatus(booking.id, newInvoiceStatus)
            } catch (_: Exception) {}
            Unit
        }

    suspend fun updateInvoiceStatusById(bookingId: String, newInvoiceStatus: String): Result<Unit> =
        azureCall {
            localBookings.value = localBookings.value.map {
                if (it.id == bookingId) it.copy(invoiceStatus = newInvoiceStatus) else it
            }
            try {
                AzureApiClient.updateInvoiceStatus(bookingId, newInvoiceStatus)
            } catch (_: Exception) {}
            Unit
        }
}

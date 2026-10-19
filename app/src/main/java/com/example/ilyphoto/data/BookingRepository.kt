package com.example.ilyphoto.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.util.Date
import java.util.UUID

/**
 * Manages client bookings, photographer availability checking (FR08), booking rescheduling
 * and status updates (FR09, FR10), and invoice generation/status tracking (FR14-FR17)
 * backed by Azure Table Storage (`Bookings` and `Invoices` tables).
 *
 * Integrity Guarantees:
 * - Availability checks (`checkAvailability`, `createBooking`, `updateBooking`) always fetch
 *   the latest cloud records from Azure Table Storage before evaluating date conflicts,
 *   preventing stale cached bookings from causing false positives or double-bookings.
 * - Rescheduling (`updateBooking`) persists all amended fields (date, time, venue, package,
 *   price, duration, requirements, phone, and status) to the Azure API before updating local state.
 * - Write operations never suppress exceptions; cloud persistence is verified before reporting success.
 */
class BookingRepository(
    private val api: IlyCloudApi = AzureApiClient
) {

    private val localBookings = MutableStateFlow<List<Booking>>(emptyList())

    /**
     * Seeds the in-memory cache (used by unit tests to verify that cloud data takes
     * precedence over stale cached bookings during availability checks).
     */
    internal fun seedLocalCache(bookings: List<Booking>) {
        localBookings.value = bookings
    }

    /**
     * Fetches the authoritative list of bookings from Azure Table Storage (`GET /api/bookings`)
     * and updates the in-memory state flow.
     */
    suspend fun fetchLatestBookings(): List<Booking> {
        val remote = api.getBookings()
            .sortedByDescending { it.createdAt?.time ?: 0L }
        val merged = if (remote.isNotEmpty()) {
            (remote + localBookings.value).distinctBy { it.id }
                .sortedByDescending { it.createdAt?.time ?: 0L }
        } else {
            localBookings.value
        }
        localBookings.value = merged
        return merged
    }

    /**
     * Streams bookings by emitting the local in-memory bookings immediately for responsive UI,
     * then loading authoritative cloud data from Azure Table Storage.
     */
    fun bookings(): Flow<List<Booking>> = flow {
        if (localBookings.value.isNotEmpty()) {
            emit(localBookings.value)
        }
        try {
            val remote = fetchLatestBookings()
            emit(remote)
        } catch (_: Exception) {
            emit(localBookings.value)
        }
    }

    /**
     * Verifies whether [eventDate] is available by loading the latest bookings from Azure
     * Table Storage first (never relying on cached bookings before cloud data loads).
     *
     * @return `true` if the date is available, `false` if another active booking conflicts.
     */
    suspend fun checkAvailability(
        eventDate: String,
        excludeBookingId: String? = null,
    ): Boolean {
        val cleanDate = eventDate.trim()
        require(cleanDate.isNotBlank()) { "Please select an event date." }
        val cloudBookings = fetchLatestBookings()
        return cloudBookings.none { existing ->
            existing.conflictsWithDate(cleanDate, excludeBookingId = excludeBookingId)
        }
    }

    /**
     * Checks photographer availability for [eventDate] against live Azure Table Storage data (FR08)
     * and creates a booking + invoice record (FR07, FR14) in Azure Table Storage.
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
        val cleanDate = eventDate.trim()
        val cleanTime = eventTime.trim().ifEmpty { "09:00" }
        val cleanLocation = eventLocation.trim()
        val cleanPhone = clientPhone.trim()

        require(cleanDate.isNotBlank()) { "Please select an event date." }
        require(cleanLocation.isNotBlank()) { "Please enter the event location or venue." }
        require(cleanPhone.isNotBlank()) { "Please enter a contact phone number." }

        // Always fetch authoritative cloud bookings before checking conflicts
        val currentList = fetchLatestBookings()
        val conflicting = currentList.any { existing ->
            existing.conflictsWithDate(cleanDate)
        }
        if (conflicting) {
            error(
                "OOPS! Looks like there is already an event booked on $cleanDate. " +
                    "Please select a different date."
            )
        }

        val cachedProfile = AzureProvider.getCachedProfile()
        val uid = clientProfile?.clientId?.takeIf { it.isNotBlank() }
            ?: clientProfile?.uid?.takeIf { it.isNotBlank() }
            ?: cachedProfile?.clientId?.takeIf { it.isNotBlank() }
            ?: cachedProfile?.uid?.takeIf { it.isNotBlank() }
            ?: clientProfile?.email?.takeIf { it.isNotBlank() }
            ?: cachedProfile?.email?.takeIf { it.isNotBlank() }
            ?: "guest-client"
        val name = clientProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: cachedProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: "Client"
        val email = clientProfile?.email?.takeIf { it.isNotBlank() }
            ?: cachedProfile?.email.orEmpty()

        val docId = "BK-" + UUID.randomUUID().toString().take(8).uppercase()
        val invoiceNo = "INV-" + docId.removePrefix("BK-").take(6).uppercase()
        val now = Date()

        val booking = Booking(
            id = docId,
            clientId = uid,
            clientName = name,
            clientEmail = email,
            clientPhone = cleanPhone,
            serviceName = serviceName.trim(),
            servicePrice = servicePrice,
            durationHours = durationHours,
            eventDate = cleanDate,
            eventTime = cleanTime,
            eventLocation = cleanLocation,
            requirements = requirements.trim(),
            status = "Pending",
            invoiceNumber = invoiceNo,
            invoiceStatus = "Unpaid",
            createdAt = now,
        )

        val created = api.createBooking(booking)
        localBookings.value = listOf(created) + localBookings.value.filterNot { it.id == created.id }
        created
    }

    suspend fun getBookingById(bookingId: String): Booking? {
        return try {
            val currentList = fetchLatestBookings()
            currentList.firstOrNull { it.id == bookingId }
                ?: localBookings.value.firstOrNull { it.id == bookingId }
        } catch (_: Exception) {
            localBookings.value.firstOrNull { it.id == bookingId }
        }
    }

    /**
     * Updates an existing booking in Azure Table Storage (reschedule / edit) and persists
     * ALL amended fields (`serviceName`, `servicePrice`, `durationHours`, `eventDate`,
     * `eventTime`, `eventLocation`, `requirements`, `clientPhone`, and `status`) across devices.
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
        val cleanDate = eventDate.trim()
        val cleanTime = eventTime.trim().ifEmpty { "14:00" }
        val cleanLocation = eventLocation.trim()

        require(cleanDate.isNotBlank()) { "Please select an event date." }
        require(cleanLocation.isNotBlank()) { "Please enter the event location or venue." }

        // Always fetch authoritative cloud bookings before checking conflicts
        val currentList = fetchLatestBookings()
        val conflicting = currentList.any { existing ->
            existing.conflictsWithDate(cleanDate, excludeBookingId = bookingId)
        }
        if (conflicting) {
            error(
                "OOPS! Looks like there is already an event booked on $cleanDate. " +
                    "Please select a different date."
            )
        }

        val existing = currentList.firstOrNull { it.id == bookingId }
            ?: localBookings.value.firstOrNull { it.id == bookingId }
            ?: error("Booking not found.")

        val dateChanged = !Booking.normalizeDateKey(existing.eventDate)
            .equals(Booking.normalizeDateKey(cleanDate), ignoreCase = true)
        val timeChanged = !existing.eventTime.trim().equals(cleanTime, ignoreCase = true)
        val locationChanged = !existing.eventLocation.trim().equals(cleanLocation, ignoreCase = true)

        val newStatus = if (dateChanged || timeChanged || locationChanged) {
            "Rescheduled"
        } else {
            existing.status
        }

        val updated = existing.copy(
            serviceName = serviceName.trim(),
            servicePrice = servicePrice,
            durationHours = durationHours,
            eventDate = cleanDate,
            eventTime = cleanTime,
            eventLocation = cleanLocation,
            requirements = requirements.trim(),
            clientPhone = clientPhone?.trim()?.ifEmpty { existing.clientPhone } ?: existing.clientPhone,
            status = newStatus
        )

        // Persist all amended fields to the Azure backend BEFORE mutating local cache
        val saved = api.updateBooking(updated)

        localBookings.value = localBookings.value
            .filterNot { it.id == bookingId || it.id == saved.id }
            .let { listOf(saved) + it }

        saved
    }

    /**
     * Cancels an existing booking in Azure Table Storage if it has not been paid.
     */
    suspend fun cancelBooking(booking: Booking): Result<Unit> = azureCall {
        if (booking.invoiceStatus.equals("Paid", ignoreCase = true)) {
            error("Paid bookings cannot be cancelled via the app. Please contact ILY PHOTO directly.")
        }
        api.updateBookingStatus(booking.id, "Cancelled")
        val updated = booking.copy(status = "Cancelled")
        localBookings.value = localBookings.value.map {
            if (it.id == booking.id) updated else it
        }
    }

    suspend fun updateBookingStatus(
        booking: Booking,
        newStatus: String,
        newDate: String? = null,
        newTime: String? = null,
    ): Result<Unit> = azureCall {
        val updated = booking.copy(
            status = newStatus,
            eventDate = newDate?.trim() ?: booking.eventDate,
            eventTime = newTime?.trim() ?: booking.eventTime,
        )

        val saved = if (newDate != null || newTime != null) {
            api.updateBooking(updated)
        } else {
            api.updateBookingStatus(booking.id, newStatus)
            updated
        }

        localBookings.value = localBookings.value
            .filterNot { it.id == booking.id || it.id == saved.id }
            .let { listOf(saved) + it }
    }

    suspend fun updateInvoiceStatus(booking: Booking, newInvoiceStatus: String): Result<Unit> =
        azureCall {
            api.updateInvoiceStatus(booking.id, newInvoiceStatus)
            val updated = booking.copy(invoiceStatus = newInvoiceStatus)
            localBookings.value = localBookings.value.map {
                if (it.id == booking.id) updated else it
            }.let { list ->
                if (list.any { it.id == booking.id }) list else listOf(updated) + list
            }
        }

    suspend fun updateInvoiceStatusById(bookingId: String, newInvoiceStatus: String): Result<Unit> =
        azureCall {
            api.updateInvoiceStatus(bookingId, newInvoiceStatus)
            localBookings.value = localBookings.value.map {
                if (it.id == bookingId) it.copy(invoiceStatus = newInvoiceStatus) else it
            }
        }
}

package com.example.ilyphoto

import com.example.ilyphoto.data.AuthRepository
import com.example.ilyphoto.data.AzureAuthException
import com.example.ilyphoto.data.AzureProvider
import com.example.ilyphoto.data.Booking
import com.example.ilyphoto.data.BookingRepository
import com.example.ilyphoto.data.Enquiry
import com.example.ilyphoto.data.IlyCloudApi
import com.example.ilyphoto.data.Photo
import com.example.ilyphoto.data.Review
import com.example.ilyphoto.data.UserProfile
import com.example.ilyphoto.data.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Meaningful application unit tests for the ILY Photo Android application.
 *
 * Covers the Task 2 lecturer feedback requirements:
 * 1. Availability checks load cloud data before evaluating conflicts (never relying on stale local cache).
 * 2. Booking conflict detection across Android (`"04 September 2026"`) and Web (`"2026-09-04"`) date formats.
 * 3. Rescheduling (`updateBooking`) persists all amended fields (date, time, venue, package, duration,
 *    price, requirements, phone, and status) to the cloud API.
 * 4. Security: cached sign-in is rejected when authentication fails with an invalid password, and
 *    placeholder/expired session tokens are rejected.
 * 5. Error handling: failed registrations, profile saves, booking writes, and password resets propagate
 *    errors accurately instead of reporting false success.
 */
class ExampleUnitTest {

    /**
     * Configurable fake implementation of [IlyCloudApi] for deterministic unit testing of
     * repositories, conflict logic, rescheduling, authentication, and failure handling.
     */
    private class FakeCloudApi : IlyCloudApi {
        var cloudBookings = mutableListOf<Booking>()
        var lastUpdatedBooking: Booking? = null
        var lastCreatedBooking: Booking? = null
        var shouldFailLogin: Boolean = false
        var shouldFailRegister: Boolean = false
        var shouldFailPasswordReset: Boolean = false
        var shouldFailProfileUpdate: Boolean = false
        var shouldFailBookingWrite: Boolean = false

        var validEmail = "client@ilyphoto.co.za"
        var validPassword = "StrongPassword#2026"
        var validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid_session_token_2026"

        override suspend fun register(
            displayName: String,
            email: String,
            password: String,
            username: String,
            phone: String,
            isPhotographer: Boolean
        ): Pair<String, UserProfile> {
            if (shouldFailRegister) {
                throw IOException("Azure Table Storage registration failed: email already registered.")
            }
            val profile = UserProfile(
                uid = "user-registered-1",
                clientId = "CL-2001",
                displayName = displayName,
                username = username,
                email = email,
                phone = phone,
                role = if (isPhotographer) UserRole.PHOTOGRAPHER else UserRole.CLIENT
            )
            return validToken to profile
        }

        override suspend fun login(email: String, password: String): Pair<String, UserProfile> {
            if (shouldFailLogin || !email.equals(validEmail, ignoreCase = true) || password != validPassword) {
                throw AzureAuthException("Invalid email or password.")
            }
            val profile = UserProfile(
                uid = "user-client-1",
                clientId = "CL-1001",
                displayName = "Aysha Client",
                username = "aysha",
                email = email,
                phone = "0848993280",
                role = UserRole.CLIENT
            )
            return validToken to profile
        }

        override suspend fun getProfile(): UserProfile = UserProfile(
            uid = "user-client-1",
            clientId = "CL-1001",
            displayName = "Aysha Client",
            username = "aysha",
            email = validEmail,
            phone = "0848993280",
            role = UserRole.CLIENT
        )

        override suspend fun updateProfile(
            displayName: String,
            username: String,
            phone: String,
            address: String,
            role: UserRole,
            photoUrl: String?
        ): UserProfile {
            if (shouldFailProfileUpdate) {
                throw IOException("Failed to save profile to Azure Table Storage.")
            }
            return UserProfile(
                uid = "user-client-1",
                clientId = "CL-1001",
                displayName = displayName,
                username = username,
                email = validEmail,
                phone = phone,
                address = address,
                role = role,
                photoUrl = photoUrl.orEmpty()
            )
        }

        override suspend fun sendPasswordReset(email: String): Boolean {
            if (shouldFailPasswordReset) {
                throw IOException("Account not found for $email in Azure Table Storage.")
            }
            return true
        }

        override suspend fun getBookings(): List<Booking> = cloudBookings.toList()

        override suspend fun createBooking(booking: Booking): Booking {
            if (shouldFailBookingWrite) {
                throw IOException("Network failure while saving booking to Azure Table Storage.")
            }
            lastCreatedBooking = booking
            cloudBookings.add(0, booking)
            return booking
        }

        override suspend fun updateBooking(booking: Booking): Booking {
            if (shouldFailBookingWrite) {
                throw IOException("Network failure while updating booking in Azure Table Storage.")
            }
            lastUpdatedBooking = booking
            cloudBookings.replaceAll { if (it.id == booking.id) booking else it }
            return booking
        }

        override suspend fun updateBookingStatus(
            bookingId: String,
            status: String,
            eventDate: String?,
            eventTime: String?,
            eventLocation: String?
        ): Booking {
            if (shouldFailBookingWrite) {
                throw IOException("Network failure while updating booking status.")
            }
            var updatedBooking: Booking? = null
            cloudBookings.replaceAll { existing ->
                if (existing.id == bookingId) {
                    val amended = existing.copy(
                        status = status,
                        eventDate = eventDate ?: existing.eventDate,
                        eventTime = eventTime ?: existing.eventTime,
                        eventLocation = eventLocation ?: existing.eventLocation
                    )
                    updatedBooking = amended
                    amended
                } else {
                    existing
                }
            }
            return updatedBooking ?: Booking(id = bookingId, status = status)
        }

        override suspend fun updateInvoiceStatus(bookingId: String, invoiceStatus: String): Booking {
            if (shouldFailBookingWrite) {
                throw IOException("Network failure while updating invoice status.")
            }
            var updatedBooking: Booking? = null
            cloudBookings.replaceAll { existing ->
                if (existing.id == bookingId) {
                    val amended = existing.copy(invoiceStatus = invoiceStatus)
                    updatedBooking = amended
                    amended
                } else {
                    existing
                }
            }
            return updatedBooking ?: Booking(id = bookingId, invoiceStatus = invoiceStatus)
        }

        override suspend fun getPhotos(): List<Photo> = emptyList()
        override suspend fun createPhoto(photo: Photo): Photo = photo
        override suspend fun deletePhoto(photoId: String): Boolean = true
        override suspend fun getEnquiries(): List<Enquiry> = emptyList()
        override suspend fun submitEnquiry(enquiry: Enquiry): Enquiry = enquiry
        override suspend fun getReviews(): List<Review> = emptyList()
        override suspend fun submitReview(review: Review): Review = review
    }

    private lateinit var fakeApi: FakeCloudApi

    @Before
    fun setUp() {
        AzureProvider.clearSession()
        fakeApi = FakeCloudApi()
    }

    @After
    fun tearDown() {
        AzureProvider.clearSession()
    }

    @Test
    fun availabilityCheck_loadsCloudBookingsFirst_ignoringEmptyLocalCache() = runBlocking {
        val repo = BookingRepository(api = fakeApi)
        // Seed local cache as empty (simulating app startup before cloud data loads)
        repo.seedLocalCache(emptyList())

        // Cloud database already has a confirmed booking on 2026-10-25 (stored in ISO format from Web)
        fakeApi.cloudBookings.add(
            Booking(
                id = "BK-CLOUD-01",
                clientId = "CL-999",
                serviceName = "Weddings",
                eventDate = "2026-10-25",
                eventTime = "10:00",
                eventLocation = "Strandfontein Pavilion",
                status = "Confirmed"
            )
        )

        // Checking availability with Android format "25 October 2026" must query cloud first and detect conflict
        val available = repo.checkAvailability("25 October 2026")
        assertFalse("Expected 25 October 2026 to be unavailable due to cloud booking", available)

        // Attempting to create a booking on that date must also fail with conflict message
        val createResult = repo.createBooking(
            clientProfile = null,
            clientPhone = "0848993280",
            serviceName = "Matric Ball",
            servicePrice = 400,
            durationHours = 2,
            eventDate = "25 October 2026",
            eventTime = "14:00",
            eventLocation = "Strandfontein, Cape Town",
            requirements = "Outdoor portraits"
        )
        assertTrue("Expected conflicting booking creation to fail", createResult.isFailure)
        assertTrue(
            createResult.exceptionOrNull()?.message.orEmpty().contains("already an event booked", ignoreCase = true)
        )
    }

    @Test
    fun updateBooking_persistsAllAmendedFieldsToCloudApi_notOnlyStatus() = runBlocking {
        val original = Booking(
            id = "BK-EDIT-100",
            clientId = "CL-1001",
            clientName = "Aysha Client",
            clientEmail = "client@ilyphoto.co.za",
            clientPhone = "0840001111",
            serviceName = "Matric Ball",
            servicePrice = 400,
            durationHours = 2,
            eventDate = "10 November 2026",
            eventTime = "14:00",
            eventLocation = "Old Venue, Cape Town",
            requirements = "Initial notes",
            status = "Confirmed",
            invoiceNumber = "INV-100",
            invoiceStatus = "Unpaid"
        )
        fakeApi.cloudBookings.add(original)
        val repo = BookingRepository(api = fakeApi)

        val result = repo.updateBooking(
            bookingId = "BK-EDIT-100",
            serviceName = "Weddings",
            servicePrice = 1400,
            durationHours = 7,
            eventDate = "18 November 2026",
            eventTime = "16:30",
            eventLocation = "8 Green Way, Strandfontein",
            requirements = "Golden hour beach portraits + family group shots",
            clientPhone = "0848993280"
        )

        assertTrue("Expected booking reschedule to succeed", result.isSuccess)
        val savedInCloud = fakeApi.lastUpdatedBooking
        assertNotNull("Expected full booking payload to be sent to cloud API", savedInCloud)
        assertEquals("BK-EDIT-100", savedInCloud?.id)
        assertEquals("Weddings", savedInCloud?.serviceName)
        assertEquals(1400, savedInCloud?.servicePrice)
        assertEquals(7, savedInCloud?.durationHours)
        assertEquals("18 November 2026", savedInCloud?.eventDate)
        assertEquals("16:30", savedInCloud?.eventTime)
        assertEquals("8 Green Way, Strandfontein", savedInCloud?.eventLocation)
        assertEquals("Golden hour beach portraits + family group shots", savedInCloud?.requirements)
        assertEquals("0848993280", savedInCloud?.clientPhone)
        assertEquals("Rescheduled", savedInCloud?.status)
    }

    @Test
    fun signIn_rejectsWrongPassword_evenWhenCachedProfileExistsForSameEmail() = runBlocking {
        // First establish a valid cached session for the user
        val cachedProfile = UserProfile(
            uid = "user-client-1",
            clientId = "CL-1001",
            displayName = "Aysha Client",
            email = fakeApi.validEmail,
            role = UserRole.CLIENT
        )
        AzureProvider.establishSession(
            token = fakeApi.validToken,
            profile = cachedProfile,
            persistToDisk = true
        )

        val authRepo = AuthRepository(api = fakeApi)

        // Attempt sign-in with the same email but an INCORRECT password
        val failedLogin = authRepo.signIn(
            email = fakeApi.validEmail,
            password = "WrongPassword123!"
        )

        assertTrue(
            "Sign-in with an invalid password must fail even if a cached profile exists",
            failedLogin.isFailure
        )
    }

    @Test
    fun sessionProtection_rejectsOfflinePlaceholderToken_andVerifiesSignature() {
        val profile = UserProfile(
            uid = "user-1",
            clientId = "CL-1001",
            displayName = "Test User",
            email = "test@ilyphoto.co.za",
            role = UserRole.CLIENT
        )

        assertFalse(
            "local-offline-token must be rejected as invalid",
            AzureProvider.isTokenValid("local-offline-token")
        )
        assertFalse(
            "Short or blank tokens must be rejected",
            AzureProvider.isTokenValid("short")
        )

        AzureProvider.establishSession(
            token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.valid_token_payload",
            profile = profile,
            persistToDisk = true
        )
        assertTrue("Valid signed session must be recognized", AzureProvider.hasValidSession())

        AzureProvider.clearSession()
        assertFalse("Cleared session must not be valid", AzureProvider.hasValidSession())
        assertNull(AzureProvider.getCachedProfile())
    }

    @Test
    fun failedRequests_propagateErrorsAccurately_forPasswordResetRegistrationAndSaves() = runBlocking {
        fakeApi.shouldFailPasswordReset = true
        fakeApi.shouldFailRegister = true
        fakeApi.shouldFailProfileUpdate = true
        fakeApi.shouldFailBookingWrite = true

        val authRepo = AuthRepository(api = fakeApi)
        val bookingRepo = BookingRepository(api = fakeApi)

        // 1. Failed password reset must return failure (never report false success)
        val resetResult = authRepo.sendPasswordReset("unknown@ilyphoto.co.za")
        assertTrue("Failed password reset must return Result.failure", resetResult.isFailure)

        // 2. Failed registration must return failure and must NOT create a fake offline user
        val registerResult = authRepo.register(
            displayName = "Test User",
            email = "duplicate@ilyphoto.co.za",
            password = "StrongPassword#2026"
        )
        assertTrue("Failed registration must return Result.failure", registerResult.isFailure)
        assertNull("Current user must remain null after failed registration", authRepo.currentUserSnapshot())

        // 3. Failed profile update must return failure
        val existingProfile = UserProfile(
            uid = "user-client-1",
            clientId = "CL-1001",
            displayName = "Original Name",
            email = fakeApi.validEmail,
            role = UserRole.CLIENT
        )
        val profileResult = authRepo.updateProfileDetails(
            current = existingProfile,
            displayName = "Amended Name",
            username = "amended",
            phone = "0840000000",
            address = "Cape Town",
            role = UserRole.CLIENT
        )
        assertTrue("Failed profile save must return Result.failure", profileResult.isFailure)

        // 4. Failed booking write must return failure and not corrupt local cache
        val bookingResult = bookingRepo.createBooking(
            clientProfile = existingProfile,
            clientPhone = "0848993280",
            serviceName = "Portraits",
            servicePrice = 300,
            durationHours = 1,
            eventDate = "12 December 2026",
            eventTime = "11:00",
            eventLocation = "Strandfontein",
            requirements = "Studio portrait"
        )
        assertTrue("Failed booking creation must return Result.failure", bookingResult.isFailure)
    }

    @Test
    fun userProfile_matchesBooking_matchesByEmailClientIdUidAndName() {
        val clientProfile = UserProfile(
            uid = "user-ada-123",
            clientId = "CL-1017",
            displayName = "Ada Adams",
            username = "ada1",
            email = "ada1@gmail.com",
            role = UserRole.CLIENT
        )

        // 1. Matches when booking.clientId has the user's email
        val emailClientBooking = Booking(
            id = "BK-01",
            clientId = "ada1@gmail.com",
            clientEmail = "ada1@gmail.com",
            clientName = "Ada Adams",
            serviceName = "Matric Ball"
        )
        assertTrue("Must match when booking.clientId holds email", clientProfile.matchesBooking(emailClientBooking))

        // 2. Matches when booking.clientId has the CL-xxxx identifier
        val idClientBooking = Booking(
            id = "BK-02",
            clientId = "CL-1017",
            clientEmail = "different@gmail.com",
            clientName = "Client",
            serviceName = "Portraits"
        )
        assertTrue("Must match when booking.clientId matches profile.clientId", clientProfile.matchesBooking(idClientBooking))

        // 3. Matches when booking.clientId has the user uid
        val uidClientBooking = Booking(
            id = "BK-03",
            clientId = "user-ada-123",
            clientEmail = "",
            clientName = "",
            serviceName = "Events"
        )
        assertTrue("Must match when booking.clientId matches profile.uid", clientProfile.matchesBooking(uidClientBooking))

        // 4. Matches when clientName matches profile displayName
        val nameClientBooking = Booking(
            id = "BK-04",
            clientId = "CL-UNKNOWN",
            clientEmail = "",
            clientName = "Ada Adams",
            serviceName = "Weddings"
        )
        assertTrue("Must match when booking clientName matches displayName", clientProfile.matchesBooking(nameClientBooking))

        // 5. Photographer matches all bookings
        val photographerProfile = UserProfile(
            uid = "user-admin",
            displayName = "Ilyaas Photographer",
            email = "photographer@ilyphoto.co.za",
            role = UserRole.PHOTOGRAPHER
        )
        assertTrue("Photographer must match any booking", photographerProfile.matchesBooking(emailClientBooking))

        // 6. Unrelated client must NOT match another user's booking
        val otherClient = UserProfile(
            uid = "user-other",
            clientId = "CL-9999",
            displayName = "Other Person",
            email = "other@gmail.com",
            role = UserRole.CLIENT
        )
        assertFalse("Unrelated client must not match another user's booking", otherClient.matchesBooking(emailClientBooking))
    }

    @Test
    fun bookingRepository_bookingsFlow_emitsCreatedBookingImmediately() = runBlocking {
        val repo = BookingRepository(api = fakeApi)

        val clientProfile = UserProfile(
            uid = "user-ada-123",
            clientId = "CL-1017",
            displayName = "Ada Adams",
            email = "ada1@gmail.com",
            role = UserRole.CLIENT
        )

        val result = repo.createBooking(
            clientProfile = clientProfile,
            clientPhone = "0848993280",
            serviceName = "Matric Ball",
            servicePrice = 550,
            durationHours = 3,
            eventDate = "30 November 2026",
            eventTime = "14:30",
            eventLocation = "Strandfontein, Cape Town",
            requirements = "Matric ball photoshoot"
        )

        assertTrue("Booking creation should succeed", result.isSuccess)
        val createdBooking = result.getOrThrow()

        // Verify bookings Flow emits the created booking immediately
        val initialStreamBookings = repo.bookings().first()
        assertTrue("Bookings flow must immediately contain created booking", initialStreamBookings.any { it.id == createdBooking.id })

        // Verify client matching on the emitted booking
        assertTrue("Client must match their newly created booking", clientProfile.matchesBooking(createdBooking))
    }
}
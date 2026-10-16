package com.example.ilyphoto.data

/**
 * Service locator for repositories backed by Azure Cloud Database & Blob Storage.
 */
object ServiceLocator {
    val auth: AuthRepository by lazy { AuthRepository() }
    val photos: PhotoRepository by lazy { PhotoRepository() }
    val bookings: BookingRepository by lazy { BookingRepository() }
    val enquiries: EnquiryRepository by lazy { EnquiryRepository() }
    val reviews: ReviewRepository by lazy { ReviewRepository() }

    val authRepository: AuthRepository get() = auth
    val photoRepository: PhotoRepository get() = photos
    val bookingRepository: BookingRepository get() = bookings
    val enquiryRepository: EnquiryRepository get() = enquiries
    val reviewRepository: ReviewRepository get() = reviews
}

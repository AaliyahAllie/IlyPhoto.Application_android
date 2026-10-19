package com.example.ilyphoto.data

/**
 * Global Azure Cloud & Cross-Platform Database configuration.
 *
 * This configuration targets:
 * - Azure App Service REST API (connected to Azure Table Storage)
 * - Azure Blob Storage (`ilyphoto-galleries`) for client photo galleries and portfolio assets
 * - Seamless cross-platform access across Android and Web applications.
 *
 * ### Azure Table Storage Schema, Keys & Integrity Rules
 * - **users**: `PartitionKey = "user"`, `RowKey = <userId>`, `ClientId = "CL-xxxx"` (or `"ADMIN"`).
 *   Enforces unique email registration and PBKDF2/SHA-256 password hashing.
 * - **bookings**: `PartitionKey = "booking"`, `RowKey = "book-<guid>"`, linked to `users` via `ClientId` and `ClientEmail`.
 *   Integrity rule: Active bookings (`Pending`, `Confirmed`, `Approved`, `Rescheduled`) cannot overlap on the same `EventDate`.
 * - **invoices**: `PartitionKey = "invoice"`, `RowKey = "inv-<guid>"`, linked via `BookingId` and `ClientId`.
 *   Integrity rule: Paid bookings cannot be cancelled via client self-service.
 * - **galleries** / **photos**: `PartitionKey = "gallery"` / `"photo"`, `RowKey = "gal-<guid>"`, linked via `ClientId` and `BookingId`.
 *   Blob paths follow `galleries/{ClientId}/{GalleryId}/{FileName}` in container `ilyphoto-galleries`.
 * - **services**: `PartitionKey = "service"`, `RowKey = <serviceSlug>` (`matric-ball`, `wedding`, `corporate-event`, `other-events`).
 * - **enquiries**: `PartitionKey = "enquiry"`, `RowKey = "enq-<guid>"`, optional `ClientId` link for registered clients.
 * - **reviews**: `PartitionKey = "review"`, `RowKey = "rev-<guid>"`, linked via `ClientId`.
 */
object AzureConfig {

    /**
     * Base URL for the Azure App Service REST API.
     *
     * In production, points to the deployed Azure App Service:
     *   "https://ilyphoto-api-st10212542.azurewebsites.net/api"
     * For local development with an Android emulator:
     *   "http://10.0.2.2:5000/api"
     */
    var API_BASE_URL: String = "https://ilyphoto-api-st10212542.azurewebsites.net/api"

    // Azure Storage Account & Blob Storage settings
    const val STORAGE_ACCOUNT_NAME = "ilyphotostorage"
    const val STORAGE_CONTAINER = "ilyphoto-galleries"

    val BLOB_CONTAINER_URL: String
        get() = "https://$STORAGE_ACCOUNT_NAME.blob.core.windows.net/$STORAGE_CONTAINER"

    val TABLE_ENDPOINT_URL: String
        get() = "https://$STORAGE_ACCOUNT_NAME.table.core.windows.net"

    // Azure Table Storage table names (shared across web and mobile via the API)
    const val TABLE_USERS = "users"
    const val TABLE_BOOKINGS = "bookings"
    const val TABLE_INVOICES = "invoices"
    const val TABLE_GALLERIES = "galleries"
    const val TABLE_PHOTOS = "photos"
    const val TABLE_SERVICES = "services"
    const val TABLE_ENQUIRIES = "enquiries"
    const val TABLE_REVIEWS = "reviews"
    const val TABLE_BLOCKED_DATES = "blockedDates"

    /**
     * Timeout for HTTP connections to Azure services in milliseconds.
     */
    const val CONNECT_TIMEOUT_MS = 10000
    const val READ_TIMEOUT_MS = 15000
}

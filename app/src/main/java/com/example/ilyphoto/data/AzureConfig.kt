package com.example.ilyphoto.data

/**
 * Global Azure Cloud & Cross-Platform Database configuration.
 *
 * This configuration targets:
 * - Azure App Service / Azure Functions REST API (connected to Azure Cosmos DB)
 * - Azure Blob Storage for photo galleries and portfolio assets
 * - Seamless cross-platform access across Android, iOS, Web, and Desktop applications.
 */
object AzureConfig {

    /**
     * Base URL for the Azure App Service or Azure Functions REST API.
     *
     * In production, point this to your deployed Azure App Service:
     *   "https://ilyphoto-api.azurewebsites.net/api"
     * For local development with an Android emulator:
     *   "http://10.0.2.2:5000/api"
     */
    var API_BASE_URL: String = "https://ilyphoto-api-st10212542.azurewebsites.net/api"

    // Azure Blob Storage settings
    const val STORAGE_ACCOUNT_NAME = "ilyphotostorage"
    const val STORAGE_CONTAINER = "ilyphoto-galleries"

    val BLOB_CONTAINER_URL: String
        get() = "https://$STORAGE_ACCOUNT_NAME.blob.core.windows.net/$STORAGE_CONTAINER"

    // Cosmos DB database metadata (shared across web and mobile via the API)
    const val COSMOS_DATABASE_ID = "IlyPhotoDB"
    const val CONTAINER_USERS = "users"
    const val CONTAINER_BOOKINGS = "bookings"
    const val CONTAINER_PHOTOS = "photos"
    const val CONTAINER_ENQUIRIES = "enquiries"
    const val CONTAINER_REVIEWS = "reviews"

    /**
     * Timeout for HTTP connections to Azure services in milliseconds.
     */
    const val CONNECT_TIMEOUT_MS = 10000
    const val READ_TIMEOUT_MS = 15000
}

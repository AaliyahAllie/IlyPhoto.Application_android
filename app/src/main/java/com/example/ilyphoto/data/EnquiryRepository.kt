package com.example.ilyphoto.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.util.Date
import java.util.UUID

/**
 * Manages client enquiries (FR19 & Enquiry entity in Task 1 ERD) backed by Azure Cosmos DB.
 */
class EnquiryRepository {

    private val localEnquiries = MutableStateFlow<List<Enquiry>>(emptyList())

    fun enquiries(): Flow<List<Enquiry>> = flow {
        emit(localEnquiries.value)

        try {
            val remote = AzureApiClient.getEnquiries()
            val remoteIds = remote.map { it.id }.toSet()
            val unsyncedLocal = localEnquiries.value.filter { it.id !in remoteIds }
            val combined = (unsyncedLocal + remote).sortedByDescending { it.createdAt?.time ?: 0L }
            localEnquiries.value = combined
            emit(combined)
        } catch (_: Exception) {
            emit(localEnquiries.value)
        }
    }

    suspend fun submitEnquiry(
        fullName: String,
        email: String,
        phone: String,
        message: String,
    ): Result<Unit> = azureCall {
        val uid = AzureProvider.getCachedProfile()?.uid.orEmpty()
        val docId = UUID.randomUUID().toString()
        val now = Date()
        val enquiry = Enquiry(
            id = docId,
            clientId = uid,
            fullName = fullName.trim(),
            email = email.trim(),
            phone = phone.trim(),
            message = message.trim(),
            status = "New",
            createdAt = now,
        )

        localEnquiries.value = listOf(enquiry) + localEnquiries.value

        try {
            AzureApiClient.submitEnquiry(enquiry)
        } catch (_: Exception) {}
        Unit
    }
}

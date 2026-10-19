package com.example.ilyphoto.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.util.Date
import java.util.UUID

/**
 * Manages client enquiries (FR19 & Enquiry entity in Task 1 ERD) backed by
 * Azure Table Storage (`Enquiries` table).
 */
class EnquiryRepository(
    private val api: IlyCloudApi = AzureApiClient
) {

    private val localEnquiries = MutableStateFlow<List<Enquiry>>(emptyList())

    fun enquiries(): Flow<List<Enquiry>> = flow {
        try {
            val remote = api.getEnquiries().sortedByDescending { it.createdAt?.time ?: 0L }
            localEnquiries.value = remote
            emit(remote)
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
        val cleanName = fullName.trim()
        val cleanEmail = email.trim()
        val cleanMessage = message.trim()
        require(cleanName.isNotBlank()) { "Please enter your name." }
        require(cleanEmail.isNotBlank() && cleanEmail.contains("@")) { "Please enter a valid email address." }
        require(cleanMessage.isNotBlank()) { "Please enter an enquiry message." }

        val profile = AzureProvider.getCachedProfile()
        val uid = profile?.clientId?.ifBlank { profile.uid }.orEmpty()
        val docId = "ENQ-" + UUID.randomUUID().toString().take(8).uppercase()
        val now = Date()
        val enquiry = Enquiry(
            id = docId,
            clientId = uid,
            fullName = cleanName,
            email = cleanEmail,
            phone = phone.trim(),
            message = cleanMessage,
            status = "New",
            createdAt = now,
        )

        api.submitEnquiry(enquiry)
        localEnquiries.value = listOf(enquiry) + localEnquiries.value
    }
}

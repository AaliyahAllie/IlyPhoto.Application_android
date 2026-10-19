package com.example.ilyphoto.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.util.Date

/**
 * Client reviews backed by Azure Table Storage (`Reviews` table).
 * One review per signed-in client (`RowKey = ClientId`).
 */
class ReviewRepository(
    private val api: IlyCloudApi = AzureApiClient
) {

    private val localReviews = MutableStateFlow<List<Review>>(emptyList())

    fun reviews(): Flow<List<Review>> = flow {
        try {
            val remote = api.getReviews().sortedByDescending { it.createdAt?.time ?: 0L }
            localReviews.value = remote
            emit(remote)
        } catch (_: Exception) {
            emit(localReviews.value)
        }
    }

    /** Creates or replaces the current user's review in Azure Table Storage. */
    suspend fun submit(text: String): Result<Unit> = azureCall {
        val cleanText = text.trim()
        require(cleanText.isNotBlank()) { "Please write a review before submitting." }

        val user = AzureProvider.getCachedProfile()
            ?: error("Please sign in to leave a review.")
        val now = Date()
        val reviewId = user.clientId.ifBlank { user.uid }
        val review = Review(
            id = reviewId,
            authorName = user.displayName.takeIf { it.isNotBlank() }
                ?: user.email.substringBefore('@'),
            text = cleanText,
            createdAt = now,
        )

        api.submitReview(review)
        localReviews.value = listOf(review) + localReviews.value.filterNot { it.id == reviewId }
    }
}

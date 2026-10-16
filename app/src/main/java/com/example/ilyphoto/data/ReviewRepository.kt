package com.example.ilyphoto.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.util.Date

/** Client reviews backed by Azure Cosmos DB. One review per signed-in client. */
class ReviewRepository {

    private val localReviews = MutableStateFlow<List<Review>>(emptyList())

    fun reviews(): Flow<List<Review>> = flow {
        emit(localReviews.value)

        try {
            val remote = AzureApiClient.getReviews()
            val remoteIds = remote.map { it.id }.toSet()
            val unsyncedLocal = localReviews.value.filter { it.id !in remoteIds }
            val combined = (unsyncedLocal + remote).sortedByDescending { it.createdAt?.time ?: 0L }
            localReviews.value = combined
            emit(combined)
        } catch (_: Exception) {
            emit(localReviews.value)
        }
    }

    /** Creates or replaces the current user's review in Azure Cosmos DB. */
    suspend fun submit(text: String): Result<Unit> = azureCall {
        val user = AzureProvider.getCachedProfile()
            ?: error("Please sign in to leave a review.")
        val now = Date()
        val review = Review(
            id = user.uid,
            authorName = user.displayName.takeIf { it.isNotBlank() }
                ?: user.email.substringBefore('@'),
            text = text.trim(),
            createdAt = now,
        )
        localReviews.value = listOf(review) + localReviews.value.filterNot { it.id == user.uid }

        try {
            AzureApiClient.submitReview(review)
        } catch (_: Exception) {}
        Unit
    }
}

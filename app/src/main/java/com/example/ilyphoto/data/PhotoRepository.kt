package com.example.ilyphoto.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.UUID
import kotlin.math.max

/**
 * Photos repository backed by Azure Blob Storage (`ilyphoto-galleries`) and
 * Azure Table Storage (`Galleries` and `Photos` tables).
 */
class PhotoRepository(
    private val api: IlyCloudApi = AzureApiClient
) {

    private val localPhotos = MutableStateFlow<List<Photo>>(emptyList())

    /** Live list of all photos, newest first. */
    fun photos(): Flow<List<Photo>> = flow {
        try {
            val remote = api.getPhotos().sortedByDescending { it.createdAt?.time ?: 0L }
            localPhotos.value = remote
            emit(remote)
        } catch (_: Exception) {
            emit(localPhotos.value)
        }
    }

    /**
     * Uploads a photo from a device [imageUri] or direct [imageUrlInput], then writes its
     * metadata to Azure Table Storage and Azure Blob Storage before updating local state.
     */
    suspend fun upload(
        context: Context? = null,
        imageUri: Uri? = null,
        imageUrlInput: String = "",
        title: String,
        description: String,
        category: String,
        tags: List<String>,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> = azureCall {
        val uid = AzureProvider.getCachedProfile()?.uid ?: "azure-photographer"

        var finalImageUrl = imageUrlInput.trim()

        if (imageUri != null && context != null) {
            finalImageUrl = encodeUriToDataUrl(context, imageUri)
                ?: error("Could not read the selected image.")
            onProgress(1f)
        }

        if (finalImageUrl.isEmpty()) {
            error("Please choose an image or provide an image URL.")
        }

        val docId = UUID.randomUUID().toString()
        val now = Date()
        val newPhoto = Photo(
            id = docId,
            title = title.trim(),
            description = description.trim(),
            category = category.trim().ifEmpty { "Other" },
            tags = tags,
            imageUrl = finalImageUrl,
            storagePath = if (finalImageUrl.startsWith("https://")) finalImageUrl else "galleries/$docId.jpg",
            photographerId = uid,
            createdAt = now,
        )

        val created = api.createPhoto(newPhoto)
        localPhotos.value = listOf(created) + localPhotos.value.filterNot { it.id == created.id }
    }

    private suspend fun encodeUriToDataUrl(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching null
                val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@runCatching null

                val maxDim = 720
                val largest = max(original.width, original.height)
                val scaled = if (largest > maxDim) {
                    val ratio = maxDim.toFloat() / largest.toFloat()
                    val w = (original.width * ratio).toInt().coerceAtLeast(1)
                    val h = (original.height * ratio).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(original, w, h, true)
                } else {
                    original
                }

                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
                val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                "data:image/jpeg;base64,$base64"
            }.getOrNull()
        }

    suspend fun delete(photo: Photo): Result<Unit> = azureCall {
        api.deletePhoto(photo.id)
        localPhotos.value = localPhotos.value.filterNot { it.id == photo.id }
    }
}

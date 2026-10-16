package com.example.ilyphoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import com.example.ilyphoto.data.ServiceLocator
import com.example.ilyphoto.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ProfileAvatarHelper {

    /**
     * Loads the avatar for the given [profile] or currently logged in client into [imageView].
     * Scoped strictly to the client ID and retrieved from the database or local cache.
     */
    fun loadAvatar(context: Context, imageView: ImageView?, profile: UserProfile?) {
        if (imageView == null) return

        val uid = profile?.uid ?: ServiceLocator.auth.currentUserSnapshot()?.uid
        if (uid.isNullOrBlank()) {
            imageView.setImageResource(R.drawable.ic_avatar_girl)
            return
        }

        val photoUrl = profile?.photoUrl.orEmpty()

        // 1. If photoUrl is Base64 stored in database
        if (photoUrl.startsWith("data:image") || (photoUrl.length > 100 && !photoUrl.startsWith("http") && !photoUrl.startsWith("file") && !photoUrl.startsWith("content"))) {
            val base64Clean = if (photoUrl.contains(",")) photoUrl.substringAfter(",") else photoUrl
            runCatching {
                val decodedBytes = Base64.decode(base64Clean, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    return
                }
            }
        }

        // 2. Check persistent internal storage file for this client ID
        val avatarFile = File(context.filesDir, "avatars/avatar_${uid}.jpg")
        if (avatarFile.exists() && avatarFile.length() > 0) {
            runCatching {
                val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    return
                }
            }
        }

        // 3. If photoUrl is a valid URI or local path
        if (photoUrl.isNotBlank()) {
            runCatching {
                imageView.setImageURI(Uri.parse(photoUrl))
                return
            }
        }

        // 4. Check client-scoped SharedPreferences
        val prefs = context.getSharedPreferences("ily_photo_prefs", Context.MODE_PRIVATE)
        val savedClientUri = prefs.getString("custom_avatar_uri_$uid", null)
        if (!savedClientUri.isNullOrBlank()) {
            runCatching {
                imageView.setImageURI(Uri.parse(savedClientUri))
                return
            }
        }

        // Fallback default avatar
        imageView.setImageResource(R.drawable.ic_avatar_girl)
    }

    /**
     * Saves selected avatar image locally and updates Firestore database linked to client UID.
     */
    suspend fun saveAndUploadAvatar(
        context: Context,
        imageUri: Uri,
        profile: UserProfile,
        onComplete: (Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        val uid = profile.uid
        try {
            val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
            val destFile = File(avatarDir, "avatar_${uid}.jpg")

            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                withContext(Dispatchers.Main) { onComplete(false) }
                return@withContext
            }

            // Downscale to reasonable avatar size (max 400x400)
            val maxDim = 400
            val scale = minOf(1f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }

            // 1. Save to internal persistent storage
            FileOutputStream(destFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            // 2. Convert to Base64 data string to save directly into Firestore users/{uid}.photoUrl
            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val base64String = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            // 3. Save to client-scoped SharedPreferences
            val prefs = context.getSharedPreferences("ily_photo_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("custom_avatar_uri_$uid", Uri.fromFile(destFile).toString())
                .apply()

            // 4. Update Database (Firestore & Auth)
            ServiceLocator.auth.updateProfilePhoto(uid, base64String)

            withContext(Dispatchers.Main) {
                onComplete(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onComplete(false)
            }
        }
    }
}

// ProfileStore.kt
//
// Saving the profile's name and picture. The picture is copied into the app's own files, because
// the one the picker hands over can vanish (a cropped copy in the cache, a deleted gallery photo).

package com.kiyo.music.ui.screens.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.datastore.preferences.core.edit
import com.kiyo.music.constants.ProfilePicUriKey
import com.kiyo.music.constants.UsernameKey
import com.kiyo.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ProfileStore {

    private const val PhotoPrefix = "profile_pic_"

    /** Largest side of a saved picture; big enough to look sharp, small enough to load instantly. */
    private const val MaxPhotoSide = 512

    /**
     * Decodes [uri], applies its rotation, scales it down and saves it as a JPEG in the app's files,
     * returning the new file's path, or null if it couldn't be read. Re-encoding means any format the
     * picker hands over (HEIC, WebP, a huge camera shot, a rotated photo) becomes a plain JPEG that
     * every image loader in the app can show.
     */
    fun copyPhoto(context: Context, uri: Uri): String? = runCatching {
        // a fresh name each time, so image caches never hand back the old picture
        val file = File(context.filesDir, "$PhotoPrefix${System.currentTimeMillis()}.jpg")
        val bitmap = decodePhoto(context, uri) ?: error("Picked photo could not be decoded")
        file.outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) { "JPEG encode failed" }
        }
        bitmap.recycle()
        file.absolutePath
    }.onFailure { Timber.w(it, "Couldn't copy the profile picture") }.getOrNull()

    private fun decodePhoto(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF rotation itself and can downscale while decoding
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = min(1f, MaxPhotoSide.toFloat() / max(info.size.width, info.size.height))
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        // Android 7–8: decode at a reduced sample size; a rotated camera photo may come out sideways here
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val largest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (largest / (sample * 2) >= MaxPhotoSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /** A value the image loaders can open: profile pictures are stored as plain paths, which need a file:// scheme. */
    fun imageModel(path: String?): String? {
        val value = path?.trim().orEmpty()
        return when {
            value.isEmpty() -> null
            value.contains("://") -> value
            else -> "file://$value"
        }
    }

    /**
     * Saves [name], and [photo] if a new one was picked. It finishes even if the screen that asked
     * goes away halfway, so a closed sheet never leaves a picture copied but not saved.
     */
    suspend fun save(context: Context, name: String, photo: Uri?) = withContext(NonCancellable + Dispatchers.IO) {
        val app = context.applicationContext
        val newPath = photo?.let { copyPhoto(app, it) }
        var oldPath: String? = null
        app.dataStore.edit { prefs ->
            prefs[UsernameKey] = name
            if (newPath != null) {
                oldPath = prefs[ProfilePicUriKey]
                prefs[ProfilePicUriKey] = newPath
            }
        }
        oldPath?.let { deleteOldPhoto(app, it) }
    }

    /** Removes a picture this class copied earlier; anything else (a content uri, someone else's file) stays. */
    private fun deleteOldPhoto(context: Context, path: String) {
        val file = File(path)
        if (file.parentFile == context.filesDir && file.name.startsWith(PhotoPrefix)) file.delete()
    }
}

package com.turbometa.rayban.data

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.turbometa.rayban.models.QuickVisionRecord
import java.io.File
import java.io.FileOutputStream

/**
 * Storage for Quick Vision records with thumbnail management
 */
class QuickVisionStorage(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val thumbnailDir: File by lazy {
        File(context.filesDir, THUMBNAIL_DIR).also {
            if (!it.exists()) it.mkdirs()
        }
    }
    private val imagesDir: File by lazy {
        File(context.filesDir, IMAGES_DIR).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    companion object {
        private const val TAG = "QuickVisionStorage"
        private const val PREFS_NAME = "turbometa_quick_vision"
        private const val KEY_RECORDS = "saved_records"
        private const val THUMBNAIL_DIR = "quick_vision_thumbnails"
        private const val IMAGES_DIR = "quick_vision_images"
        private const val MAX_RECORDS = 100

        @Volatile
        private var instance: QuickVisionStorage? = null

        fun getInstance(context: Context): QuickVisionStorage {
            return instance ?: synchronized(this) {
                instance ?: QuickVisionStorage(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Save a Quick Vision record with thumbnail
     */
    fun saveRecord(
        bitmap: Bitmap,
        prompt: String,
        result: String,
        mode: com.turbometa.rayban.models.QuickVisionMode,
        visionModel: String
    ): Boolean {
        return try {
            val id = java.util.UUID.randomUUID().toString()
            val thumbnailPath = saveThumbnail(id, bitmap)
            val imagePath = saveFullImage(id, bitmap)

            if (thumbnailPath == null) {
                Log.e(TAG, "Failed to save thumbnail")
                return false
            }

            val record = QuickVisionRecord(
                id = id,
                thumbnailPath = thumbnailPath,
                imagePath = imagePath,
                prompt = prompt,
                result = result,
                mode = mode,
                visionModel = visionModel
            )

            val records = getAllRecords().toMutableList()
            records.add(0, record)

            // Trim to max records and clean up old thumbnails
            if (records.size > MAX_RECORDS) {
                val toRemove = records.subList(MAX_RECORDS, records.size)
                toRemove.forEach { 
                    deleteThumbnail(it.thumbnailPath)
                    it.imagePath?.let { path -> deleteImage(path) }
                }
                records.removeAll(toRemove.toSet())
            }

            val json = gson.toJson(records)
            prefs.edit().putString(KEY_RECORDS, json).apply()
            Log.d(TAG, "Record saved: $id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save record: ${e.message}", e)
            false
        }
    }

    /**
     * Get all Quick Vision records
     */
    fun getAllRecords(): List<QuickVisionRecord> {
        return try {
            val json = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
            val type = object : TypeToken<List<QuickVisionRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load records: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get a single record by ID
     */
    fun getRecord(id: String): QuickVisionRecord? {
        return getAllRecords().find { it.id == id }
    }

    /**
     * Delete a record and its thumbnail
     */
    fun deleteRecord(id: String): Boolean {
        return try {
            val records = getAllRecords().toMutableList()
            val record = records.find { it.id == id }

            if (record != null) {
                deleteThumbnail(record.thumbnailPath)
                record.imagePath?.let { deleteImage(it) }
                records.removeAll { it.id == id }
                val json = gson.toJson(records)
                prefs.edit().putString(KEY_RECORDS, json).apply()
                Log.d(TAG, "Record deleted: $id")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete record: ${e.message}", e)
            false
        }
    }

    /**
     * Delete all records and thumbnails
     */
    fun deleteAllRecords(): Boolean {
        return try {
            // Delete all thumbnail files
            thumbnailDir.listFiles()?.forEach { it.delete() }
            imagesDir.listFiles()?.forEach { it.delete() }
            prefs.edit().remove(KEY_RECORDS).apply()
            Log.d(TAG, "All records deleted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete all records: ${e.message}", e)
            false
        }
    }

    /**
     * Get record count
     */
    fun getRecordCount(): Int {
        return getAllRecords().size
    }

    /**
     * Save bitmap to Gallery (MediaStore)
     */
    fun saveToGallery(bitmap: Bitmap): String? {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        var fos: java.io.OutputStream? = null
        var imageUri: Uri? = null

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/TurboMeta")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
            }

            val contentResolver = context.contentResolver
            imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (imageUri != null) {
                fos = contentResolver.openOutputStream(imageUri)
                if (fos != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                    fos.close()

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(imageUri, contentValues, null, null)
                    }
                    Log.d(TAG, "Image saved to gallery: $imageUri")
                    return imageUri.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to gallery: ${e.message}", e)
            // If we failed, try to cleanup the empty file
            if (imageUri != null) {
                try {
                    context.contentResolver.delete(imageUri, null, null)
                } catch (cleanupEx: Exception) {
                    // Ignore
                }
            }
        } finally {
            try {
                fos?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }

    /**
     * Save bitmap as thumbnail and return file path
     */
    private fun saveThumbnail(id: String, bitmap: Bitmap): String? {
        return try {
            val file = File(thumbnailDir, "${id}.jpg")
            FileOutputStream(file).use { out ->
                // Scale down for thumbnail (max 480px width)
                val scaledBitmap = if (bitmap.width > 480) {
                    val scale = 480f / bitmap.width
                    Bitmap.createScaledBitmap(
                        bitmap,
                        480,
                        (bitmap.height * scale).toInt(),
                        true
                    )
                } else {
                    bitmap
                }
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save thumbnail: ${e.message}", e)
            null
        }
    }

    /**
     * Delete a thumbnail file
     */
    private fun deleteThumbnail(path: String) {
        try {
            File(path).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete thumbnail: ${e.message}", e)
        }
    }

    private fun saveFullImage(id: String, bitmap: Bitmap): String? {
        return try {
            val file = File(imagesDir, "${id}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save full image: ${e.message}", e)
            null
        }
    }

    private fun deleteImage(path: String) {
        try {
            File(path).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image: ${e.message}", e)
        }
    }
}

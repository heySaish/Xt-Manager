package com.xtmanager.core.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import com.xtmanager.core.model.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object ThumbnailManager {

    // 1. Memory Cache bounded to ~1/8th max memory or 32MB max
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceIn(8192, 32768)
    private val memoryCache = object : LruCache<String, Bitmap>(maxMemoryKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    // 2. Controlled Dispatcher for Background Decoding (Concurrency Limit = 3)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(3)

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "3gp", "webm", "mov", "flv", "wmv", "m4v")

    fun isThumbnailSupported(fileEntry: FileEntry): Boolean {
        if (fileEntry.isDirectory) return false
        val ext = fileEntry.name.substringAfterLast('.', "").lowercase()
        return ext == "apk" || IMAGE_EXTENSIONS.contains(ext) || VIDEO_EXTENSIONS.contains(ext)
    }

    fun getFromMemoryCache(key: String): Bitmap? {
        return memoryCache.get(key)
    }

    fun buildCacheKey(path: String, lastModified: Long, fileSize: Long, targetPx: Int = 120): String {
        return "${path.hashCode()}_${lastModified}_${fileSize}_$targetPx"
    }

    suspend fun getThumbnail(
        context: Context,
        fileEntry: FileEntry,
        targetPx: Int = 120
    ): Bitmap? = withContext(thumbnailDispatcher) {
        val cacheKey = buildCacheKey(fileEntry.path, fileEntry.lastModified, fileEntry.size, targetPx)

        // Step A: Memory Cache Lookup (Instantaneous)
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // Step B: Disk Cache Lookup
        val diskFile = getDiskCacheFile(context, cacheKey)
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    return@withContext bitmap
                }
            } catch (_: Throwable) {
                diskFile.delete()
            }
        }

        // Step C: Background Decoding off UI thread
        val decodedBitmap = decodeOriginal(context, fileEntry, targetPx) ?: return@withContext null

        // Step D: Save to Disk & Memory Cache
        saveToDiskCache(diskFile, decodedBitmap)
        memoryCache.put(cacheKey, decodedBitmap)

        decodedBitmap
    }

    private fun decodeOriginal(context: Context, fileEntry: FileEntry, targetPx: Int): Bitmap? {
        val path = fileEntry.path
        val ext = fileEntry.name.substringAfterLast('.', "").lowercase()

        return when {
            ext == "apk" -> decodeApkIcon(context, path, targetPx)
            VIDEO_EXTENSIONS.contains(ext) -> decodeVideoFrame(path, targetPx)
            IMAGE_EXTENSIONS.contains(ext) -> decodeImage(path, targetPx)
            else -> null
        }
    }

    private fun decodeImage(path: String, targetPx: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, targetPx, targetPx)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565 // Memory optimized

            BitmapFactory.decodeFile(path, options)
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeApkIcon(context: Context, path: String, targetPx: Int): Bitmap? {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(path, 0) ?: return null
            info.applicationInfo.sourceDir = path
            info.applicationInfo.publicSourceDir = path
            val drawable = info.applicationInfo.loadIcon(pm) ?: return null
            drawableToBitmap(drawable, targetPx)
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeVideoFrame(path: String, targetPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val frame: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                retriever.getScaledFrameAtTime(
                    1000000L, // 1 second into video
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetPx,
                    targetPx
                ) ?: retriever.frameAtTime
            } else {
                retriever.frameAtTime
            }

            if (frame != null && (frame.width > targetPx || frame.height > targetPx)) {
                Bitmap.createScaledBitmap(frame, targetPx, targetPx, true)
            } else {
                frame
            }
        } catch (_: Throwable) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {}
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun drawableToBitmap(drawable: Drawable, targetPx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bmp = drawable.bitmap
            if (bmp.width <= targetPx && bmp.height <= targetPx) return bmp
            return Bitmap.createScaledBitmap(bmp, targetPx, targetPx, true)
        }

        val bitmap = Bitmap.createBitmap(
            targetPx.coerceAtLeast(1),
            targetPx.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getDiskCacheFile(context: Context, cacheKey: String): File {
        val dir = File(context.cacheDir, "thumbnails")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$cacheKey.jpg")
    }

    private fun saveToDiskCache(file: File, bitmap: Bitmap) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (_: Throwable) {}
    }

    private fun md5(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            input.hashCode().toString()
        }
    }
}

package com.example.data.util

import android.content.Context
import java.io.File

object SecureStorageHelper {
    private const val DIR_VIDEOS = "videos"
    private const val DIR_THUMBNAILS = "thumbnails"
    private const val DIR_PREVIEWS = "previews"
    private const val DIR_BACKGROUNDS = "backgrounds"
    private const val DIR_TEMP = "temp"

    fun getVideosDirectory(context: Context): File {
        val dir = context.getExternalFilesDir("videos") ?: File(context.filesDir, DIR_VIDEOS)
        return dir.apply { mkdirs() }
    }

    fun getThumbnailsDirectory(context: Context): File {
        val dir = context.getExternalFilesDir("thumbnails") ?: File(context.filesDir, DIR_THUMBNAILS)
        return dir.apply { mkdirs() }
    }

    fun getPreviewsDirectory(context: Context): File {
        val dir = context.getExternalFilesDir("previews") ?: File(context.filesDir, DIR_PREVIEWS)
        return dir.apply { mkdirs() }
    }

    fun getBackgroundsDirectory(context: Context): File {
        val dir = context.getExternalFilesDir("backgrounds") ?: File(context.filesDir, DIR_BACKGROUNDS)
        return dir.apply { mkdirs() }
    }

    fun getTempDirectory(context: Context): File {
        val dir = context.externalCacheDir ?: File(context.cacheDir, DIR_TEMP)
        return dir.apply { mkdirs() }
    }

    fun getSecureVideoPath(context: Context, videoId: String): File {
        return File(getVideosDirectory(context), "$videoId.enc")
    }

    fun getSecureThumbnailPath(context: Context, thumbnailId: String): File {
        return File(getThumbnailsDirectory(context), "$thumbnailId.enc")
    }

    fun getSecurePreviewPath(context: Context, previewId: String): File {
        return File(getPreviewsDirectory(context), "$previewId.enc")
    }

    fun getSecureBackgroundPath(context: Context, backgroundId: String): File {
        return File(getBackgroundsDirectory(context), "$backgroundId.enc")
    }

    fun clearTempFiles(context: Context) {
        val tempDir = getTempDirectory(context)
        tempDir.listFiles()?.forEach { file ->
            try {
                file.delete()
            } catch (e: Exception) {
                // Ignore failure to delete individual temp files
            }
        }
    }

    fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach { child ->
            size += getFolderSize(child)
        }
        return size
    }

    fun getTotalSecureSpaceUsed(context: Context): Long {
        var total = 0L
        total += getFolderSize(getVideosDirectory(context))
        total += getFolderSize(getThumbnailsDirectory(context))
        total += getFolderSize(getPreviewsDirectory(context))
        total += getFolderSize(getBackgroundsDirectory(context))
        return total
    }
}

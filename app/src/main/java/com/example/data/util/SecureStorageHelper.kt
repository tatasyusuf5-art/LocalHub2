package com.example.data.util

import android.content.Context
import android.os.Environment
import java.io.File

object SecureStorageHelper {
    private const val DIR_ROOT = ".lh"
    private const val DIR_VIDEOS = ".v"
    private const val DIR_THUMBNAILS = ".t"
    private const val DIR_PREVIEWS = ".p"
    private const val DIR_BACKGROUNDS = ".b"
    private const val DIR_TEMP = ".tmp"

    private fun getBaseDir(): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val lhDir = File(docsDir, DIR_ROOT)
        return lhDir.apply { mkdirs() }
    }

    fun getVideosDirectory(context: Context): File {
        return File(getBaseDir(), DIR_VIDEOS).apply { mkdirs() }
    }

    fun getThumbnailsDirectory(context: Context): File {
        return File(getBaseDir(), DIR_THUMBNAILS).apply { mkdirs() }
    }

    fun getPreviewsDirectory(context: Context): File {
        return File(getBaseDir(), DIR_PREVIEWS).apply { mkdirs() }
    }

    fun getBackgroundsDirectory(context: Context): File {
        return File(getBaseDir(), DIR_BACKGROUNDS).apply { mkdirs() }
    }

    fun getTempDirectory(context: Context): File {
        return File(getBaseDir(), DIR_TEMP).apply { mkdirs() }
    }

    fun getSecureVideoPath(context: Context, videoId: String): File {
        return File(getVideosDirectory(context), "$videoId.mp4")
    }

    fun getSecureThumbnailPath(context: Context, thumbnailId: String): File {
        return File(getThumbnailsDirectory(context), "$thumbnailId.jpg")
    }

    fun getSecurePreviewPath(context: Context, previewId: String): File {
        return File(getPreviewsDirectory(context), "$previewId.mp4")
    }

    fun getSecureBackgroundPath(context: Context, backgroundId: String): File {
        return File(getBackgroundsDirectory(context), "$backgroundId.jpg")
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

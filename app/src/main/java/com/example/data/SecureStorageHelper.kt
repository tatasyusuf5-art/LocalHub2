package com.example.data.util

import android.content.Context
import android.os.Environment
import java.io.File

object SecureStorageHelper {
    // === GİZLİLİK: Labirent klasör yapısı ===
    // Masum görünen "sistem önbelleği" adı + iç içe kısa kodlu klasörler.
    // Dosya yöneticisinde gezen biri buradaki yapıyı anlamlandıramaz.
    // Documents/.sys_cache/.0a/{.9x video, .3k thumb, .7m preview, .2b bg, .5u user}
    private const val DIR_ROOT = ".sys_cache"
    private const val DIR_MID = ".0a"          // ara katman (labirent)
    private const val DIR_VIDEOS = ".9x"
    private const val DIR_THUMBNAILS = ".3k"
    private const val DIR_PREVIEWS = ".7m"
    private const val DIR_BACKGROUNDS = ".2b"
    private const val DIR_USERS = ".5u"
    private const val DIR_TEMP = ".tmp"

    // === Tüm gizli dosyalar bu uzantıyla kaydedilir ===
    // .dat = dosya yöneticisi/galeri "bu ne dosyası" diye tanıyamaz,
    // önizleme gösteremez, oynatamaz. Uygulama içeriğinden okur, sorun olmaz.
    private const val EXT = ".dat"

    private fun getBaseDir(): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val root = File(docsDir, DIR_ROOT)
        return root.apply { mkdirs() }
    }

    // Ara labirent katmanı (.0a)
    private fun getMidDir(): File {
        return File(getBaseDir(), DIR_MID).apply { mkdirs() }
    }

    fun getVideosDirectory(context: Context): File {
        return File(getMidDir(), DIR_VIDEOS).apply { mkdirs() }
    }

    fun getThumbnailsDirectory(context: Context): File {
        return File(getMidDir(), DIR_THUMBNAILS).apply { mkdirs() }
    }

    fun getPreviewsDirectory(context: Context): File {
        return File(getMidDir(), DIR_PREVIEWS).apply { mkdirs() }
    }

    fun getBackgroundsDirectory(context: Context): File {
        return File(getMidDir(), DIR_BACKGROUNDS).apply { mkdirs() }
    }

    fun getUsersDirectory(context: Context): File {
        return File(getMidDir(), DIR_USERS).apply { mkdirs() }
    }

    // Temp doğrudan root altında (uygulama içi geçici, gizlemeye gerek yok)
    fun getTempDirectory(context: Context): File {
        return File(getBaseDir(), DIR_TEMP).apply { mkdirs() }
    }

    // === Dosya yolları: hepsi .dat uzantılı ===
    fun getSecureUserPhotoPath(context: Context, userId: String): File {
        return File(getUsersDirectory(context), "$userId$EXT")
    }

    fun getSecureVideoPath(context: Context, videoId: String): File {
        return File(getVideosDirectory(context), "$videoId$EXT")
    }

    fun getSecureThumbnailPath(context: Context, thumbnailId: String): File {
        return File(getThumbnailsDirectory(context), "$thumbnailId$EXT")
    }

    fun getSecurePreviewPath(context: Context, previewId: String): File {
        return File(getPreviewsDirectory(context), "$previewId$EXT")
    }

    fun getSecureBackgroundPath(context: Context, backgroundId: String): File {
        return File(getBackgroundsDirectory(context), "$backgroundId$EXT")
    }

    fun clearTempFiles(context: Context) {
        val tempDir = getTempDirectory(context)
        tempDir.listFiles()?.forEach { file ->
            try {
                file.delete()
            } catch (e: Exception) {
                // Ignore
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
        // Tüm labirent yapısını kökten hesapla
        return getFolderSize(getBaseDir())
    }
}

package com.example.data.util

import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.content.ContentUris
import android.app.RecoverableSecurityException
import com.example.data.crypto.AES256CryptoManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.random.Random

object MediaProcessingHelper {

    private const val SEGMENT_COUNT = 10
    private const val SEGMENT_DURATION_MS = 2000L

    fun getVideoDurationMs(context: Context, videoUri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSourceCompat(context, videoUri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    fun extractThumbnailAtTime(context: Context, videoUri: Uri, timeMs: Long, destFile: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSourceCompat(context, videoUri)
            val bitmap = retriever.getFrameAtTime(
                timeMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime
            ?: throw IllegalArgumentException("Frame alınamadı: $timeMs ms")

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val encryptedBytes = AES256CryptoManager.encryptBytes(baos.toByteArray())
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { it.write(encryptedBytes) }
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    /**
     * 10 adet 2'şer saniyelik segment alır, kronolojik sıraya göre birleştirir.
     * Toplam 20 saniyelik preview klibi oluşturur ve şifreli olarak kaydeder.
     *
     * Her çağrıda farklı rastgele noktalar seçilir → 5 kez çağrılınca 5 farklı preview oluşur.
     */
    fun createPreviewClip(context: Context, videoUri: Uri, totalDurationMs: Long, destFile: File) {
        val tempOut = File(context.cacheDir, "preview_temp_${UUID.randomUUID()}.mp4")
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            // Video süresini doğrula
            val safeDuration = totalDurationMs.coerceAtLeast(SEGMENT_DURATION_MS * SEGMENT_COUNT + 1000L)

            // 1. Rastgele 10 zaman noktası üret
            val maxStart = safeDuration - SEGMENT_DURATION_MS - 500L
            val rawPoints = mutableSetOf<Long>()
            var attempts = 0
            while (rawPoints.size < SEGMENT_COUNT && attempts < SEGMENT_COUNT * 20) {
                rawPoints.add(Random.nextLong(0L, maxStart.coerceAtLeast(1L)))
                attempts++
            }
            // Yeterli nokta yoksa eşit aralıklı ekle
            if (rawPoints.size < SEGMENT_COUNT) {
                val step = maxStart / (SEGMENT_COUNT + 1)
                for (i in 1..SEGMENT_COUNT) {
                    if (rawPoints.size >= SEGMENT_COUNT) break
                    rawPoints.add(step * i)
                }
            }

            // 2. Kronolojik sırala (küçükten büyüğe)
            val sortedPoints = rawPoints.sorted()

            // 3. MediaExtractor kurulumu
            extractor = MediaExtractor()
            extractor.setDataSourceCompat(context, videoUri)

            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                // Video track bulunamadı, direkt şifrele
                AES256CryptoManager.encryptFile(context, videoUri, destFile)
                return
            }

            extractor.selectTrack(videoTrackIndex)

            // 4. MediaMuxer kurulumu
            muxer = MediaMuxer(tempOut.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val writeTrackIndex = muxer.addTrack(videoFormat)
            muxer.start()

            val maxBufferSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
            val buffer = ByteBuffer.allocate(maxBufferSize.coerceAtLeast(1024 * 1024))
            val bufferInfo = MediaCodec.BufferInfo()

            var accumulatedTimeUs = 0L
            var lastPtsUs = -1L

            // 5. Her zaman noktasından 2 saniyelik segment al
            for (startMs in sortedPoints) {
                extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                var firstSampleTimeUs: Long? = null
                val segmentEndUs = SEGMENT_DURATION_MS * 1000L

                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)

                    if (bufferInfo.size < 0) break

                    val sampleTimeUs = extractor.sampleTime
                    if (firstSampleTimeUs == null) {
                        firstSampleTimeUs = sampleTimeUs
                    }

                    // Segment bitişini kontrol et (2 saniye)
                    if (sampleTimeUs - firstSampleTimeUs > segmentEndUs) break

                    // Monoton artan PTS hesapla
                    val relativePts = sampleTimeUs - firstSampleTimeUs
                    val absolutePts = relativePts + accumulatedTimeUs
                    val safePts = if (absolutePts > lastPtsUs) absolutePts else lastPtsUs + 1000L

                    bufferInfo.presentationTimeUs = safePts
                    bufferInfo.flags = extractor.sampleFlags
                    lastPtsUs = safePts

                    muxer.writeSampleData(writeTrackIndex, buffer, bufferInfo)
                    extractor.advance()
                }

                // Bir sonraki segment için zaman offsetini güncelle
                accumulatedTimeUs += segmentEndUs
            }

            muxer.stop()
            muxer.release()
            muxer = null

            // 6. Oluşturulan MP4'ü AES-256 ile şifrele
            AES256CryptoManager.encryptFile(context, Uri.fromFile(tempOut), destFile)

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: hata olursa videoyu direkt şifrele
            try {
                if (!destFile.exists() || destFile.length() == 0L) {
                    AES256CryptoManager.encryptFile(context, videoUri, destFile)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } finally {
            try { extractor?.release() } catch (e: Exception) {}
            try { muxer?.release() } catch (e: Exception) {}
            if (tempOut.exists()) tempOut.delete()
        }
    }

    private fun MediaMetadataRetriever.setDataSourceCompat(context: Context, uri: Uri) {
        if (uri.scheme == "file") setDataSource(uri.path)
        else setDataSource(context, uri)
    }

    private fun MediaExtractor.setDataSourceCompat(context: Context, uri: Uri) {
        if (uri.scheme == "file") setDataSource(uri.path ?: throw IllegalArgumentException("Geçersiz dosya yolu"))
        else setDataSource(context, uri, null)
    }

    fun getMediaStoreUri(context: Context, uri: Uri): Uri {
        val uriString = uri.toString()
        if (uriString.startsWith("content://media/external/")) return uri

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            if (split.size == 2) {
                val type = split[0]
                val id = split[1]
                val contentUri = when (type) {
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    else -> null
                }
                if (contentUri != null) {
                    try { return ContentUris.withAppendedId(contentUri, id.toLong()) }
                    catch (e: Exception) { e.printStackTrace() }
                }
            }
        }

        try {
            val idStr = uri.lastPathSegment
            if (idStr != null && idStr.all { it.isDigit() }) {
                val mimeType = context.contentResolver.getType(uri)
                val contentUri = if (mimeType?.startsWith("image") == true)
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                return ContentUris.withAppendedId(contentUri, idStr.toLong())
            }
        } catch (e: Exception) { e.printStackTrace() }

        return uri
    }

    fun getDeleteRequestIntentSender(context: Context, uri: Uri): IntentSender? {
        val cleanUri = getMediaStoreUri(context, uri)
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                MediaStore.createDeleteRequest(resolver, listOf(cleanUri)).intentSender
            } catch (e: Exception) { e.printStackTrace(); null }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                resolver.delete(cleanUri, null, null)
                null
            } catch (e: SecurityException) {
                try {
                    (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                } catch (ex: Exception) { null }
            } catch (e: Exception) { e.printStackTrace(); null }
        } else {
            try { resolver.delete(cleanUri, null, null) } catch (e: Exception) { e.printStackTrace() }
            null
        }
    }
}

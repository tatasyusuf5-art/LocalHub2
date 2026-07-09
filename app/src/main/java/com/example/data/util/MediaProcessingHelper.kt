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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { it.write(baos.toByteArray()) }
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    private fun copyFile(context: Context, uri: Uri, destFile: File) {
        destFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
            FileOutputStream(destFile).use { output: OutputStream ->
                val buffer = ByteArray(4096)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    /**
     * 10 adet 2'şer saniyelik segment alır, kronolojik sıraya göre birleştirir.
     * Toplam 20 saniyelik preview klibi oluşturur.
     */
    fun createPreviewClip(context: Context, videoUri: Uri, totalDurationMs: Long, destFile: File) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            destFile.parentFile?.mkdirs()

            // Duration 0 veya çok küçükse MediaMetadataRetriever ile yeniden al
            var realDuration = totalDurationMs
            if (realDuration <= 0L) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSourceCompat(context, videoUri)
                    realDuration = retriever.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L
                    retriever.release()
                } catch (e: Exception) { e.printStackTrace() }
            }
            // Hala 0 ise videoyu direkt kopyala (fallback)
            if (realDuration <= 0L) {
                copyFile(context, videoUri, destFile)
                return
            }

            // Video süresini doğrula
            val safeDuration = realDuration.coerceAtLeast(SEGMENT_DURATION_MS * SEGMENT_COUNT + 1000L)

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
                // Video track bulunamadı, direkt kopyala
                copyFile(context, videoUri, destFile)
                return
            }

            extractor.selectTrack(videoTrackIndex)

            // 4. MediaMuxer kurulumu
            muxer = MediaMuxer(destFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
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

            // Güvenlik: dosya 0 byte olduysa videoyu direkt kopyala
            if (!destFile.exists() || destFile.length() == 0L) {
                copyFile(context, videoUri, destFile)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: hata olursa videoyu direkt kopyala
            try {
                if (!destFile.exists() || destFile.length() == 0L) {
                    copyFile(context, videoUri, destFile)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        } finally {
            try { extractor?.release() } catch (e: Exception) {}
            try { muxer?.release() } catch (e: Exception) {}
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

    /**
     * Video + harici ses dosyasını tek MP4'te birleştirir (kalıcı mux).
     * Yeniden encode YOK - sadece stream kopyalama, hızlı.
     * Başarılıysa true, hata olursa false döner.
     */
    fun muxVideoWithAudio(videoFile: File, audioFile: File, outputFile: File): Boolean {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            // Video extractor
            videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoFile.absolutePath)
            var videoTrackIndex = -1
            var videoFormat: android.media.MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    videoTrackIndex = i
                    videoFormat = fmt
                    break
                }
            }
            if (videoTrackIndex == -1 || videoFormat == null) return false

            // Audio extractor
            audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(audioFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: android.media.MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) return false

            // Muxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outVideoTrack = muxer.addTrack(videoFormat)
            val outAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            // Video kopyala
            videoExtractor.selectTrack(videoTrackIndex)
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(outVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Ses kopyala
            audioExtractor.selectTrack(audioTrackIndex)
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = audioExtractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(outAudioTrack, buffer, bufferInfo)
                audioExtractor.advance()
            }

            muxer.stop()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try { videoExtractor?.release() } catch (e: Exception) {}
            try { audioExtractor?.release() } catch (e: Exception) {}
            try { muxer?.release() } catch (e: Exception) {}
        }
    }

}

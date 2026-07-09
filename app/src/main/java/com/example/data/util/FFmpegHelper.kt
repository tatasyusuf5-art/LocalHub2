package com.example.data.util

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * FFmpeg tabanlı medya işleme.
 * Preview oluşturma ve ses birleştirme için MediaMuxer yerine FFmpeg kullanır.
 * Daha güvenilir, tüm formatları destekler.
 */
object FFmpegHelper {

    // Uri'yi FFmpeg'in okuyabileceği gerçek dosya yoluna çevir
    private fun resolvePath(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "file" -> uri.path
            "content" -> {
                // content:// uri'yi geçici dosyaya kopyala
                try {
                    val temp = File(context.cacheDir, "ff_src_${System.currentTimeMillis()}.tmp")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    }
                    temp.absolutePath
                } catch (e: Exception) { null }
            }
            else -> uri.path
        }
    }

    /**
     * Video + ses birleştir (ses videoya kalıcı gömülür).
     * Yeniden encode YOK (-c copy), hızlı. Başarılıysa true.
     */
    fun muxVideoWithAudio(videoFile: File, audioFile: File, outputFile: File): Boolean {
        return try {
            if (outputFile.exists()) outputFile.delete()
            // -c:v copy: video olduğu gibi, -c:a aac: ses aac'ye çevrilir (uyumluluk)
            // -map 0:v:0 videodan video, -map 1:a:0 ses dosyasından ses
            // -shortest: kısa olana göre bitir
            val cmd = "-y -i \"${videoFile.absolutePath}\" -i \"${audioFile.absolutePath}\" " +
                    "-map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -shortest \"${outputFile.absolutePath}\""
            val session = FFmpegKit.execute(cmd)
            ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Preview klibi oluştur: videonun farklı noktalarından kısa segmentler alıp birleştirir.
     * FFmpeg ile güvenilir - 0 byte sorunu olmaz.
     */
    fun createPreviewClip(context: Context, videoUri: Uri, totalDurationMs: Long, destFile: File): Boolean {
        val srcPath = resolvePath(context, videoUri) ?: return false
        return try {
            if (destFile.exists()) destFile.delete()
            destFile.parentFile?.mkdirs()

            val totalSec = totalDurationMs / 1000.0
            // Çok kısa videolar için: direkt ilk 15 saniyeyi al
            if (totalSec < 25) {
                val cmd = "-y -i \"$srcPath\" -t 15 -an -c:v libx264 -preset ultrafast \"${destFile.absolutePath}\""
                val session = FFmpegKit.execute(cmd)
                return ReturnCode.isSuccess(session.returnCode) && destFile.exists() && destFile.length() > 0
            }

            // 6 farklı noktadan 2.5 saniyelik segment al, birleştir
            val segmentCount = 6
            val segmentDur = 2.5
            val points = mutableListOf<Double>()
            val usable = totalSec - segmentDur - 1
            val step = usable / (segmentCount + 1)
            for (i in 1..segmentCount) {
                points.add(step * i)
            }

            // Her segment için ayrı geçici dosya oluştur
            val tempSegments = mutableListOf<File>()
            for ((index, startSec) in points.withIndex()) {
                val segFile = File(context.cacheDir, "ff_seg_${System.currentTimeMillis()}_$index.mp4")
                // -ss start, -t süre, -an ses yok (preview sessiz), hızlı encode
                val cmd = "-y -ss $startSec -i \"$srcPath\" -t $segmentDur -an " +
                        "-c:v libx264 -preset ultrafast -vf scale=480:-2 \"${segFile.absolutePath}\""
                val session = FFmpegKit.execute(cmd)
                if (ReturnCode.isSuccess(session.returnCode) && segFile.exists() && segFile.length() > 0) {
                    tempSegments.add(segFile)
                }
            }

            if (tempSegments.isEmpty()) return false

            // Segmentleri birleştir (concat)
            val listFile = File(context.cacheDir, "ff_concat_${System.currentTimeMillis()}.txt")
            listFile.writeText(tempSegments.joinToString("\n") { "file '${it.absolutePath}'" })

            val concatCmd = "-y -f concat -safe 0 -i \"${listFile.absolutePath}\" -c copy \"${destFile.absolutePath}\""
            val concatSession = FFmpegKit.execute(concatCmd)
            val success = ReturnCode.isSuccess(concatSession.returnCode) && destFile.exists() && destFile.length() > 0

            // Temizlik
            tempSegments.forEach { try { it.delete() } catch (e: Exception) {} }
            try { listFile.delete() } catch (e: Exception) {}

            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

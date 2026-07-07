import re

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

target_prepare = r'fun preparePickedVideo.*?\{.*?(?=\n    fun |\Z)'
target_import = r'fun importVideo.*?\{.*?(?=\n    fun |\Z)'

auto_import_code = """fun autoImportVideo(context: Context, uri: Uri) {
        if (_isImporting.value) return
        _isImporting.value = true
        _importProgress.value = 0f
        _importStatus.value = "Video hazırlanıyor..."

        viewModelScope.launch(Dispatchers.IO) {
            val videoId = UUID.randomUUID().toString()
            var tempCacheFile: File? = null
            try {
                // 1. Get original name from MediaStore or path
                var title = ""
                if (uri.scheme == "content") {
                    // Try MediaStore TITLE first
                    context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Video.Media.TITLE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.TITLE)
                            if (index != -1) {
                                title = cursor.getString(index) ?: ""
                            }
                        }
                    }
                    // Fallback to DISPLAY_NAME
                    if (title.isBlank()) {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (index != -1) {
                                    title = cursor.getString(index) ?: ""
                                }
                            }
                        }
                    }
                }
                if (title.isBlank()) {
                    title = uri.path?.substringAfterLast('/') ?: ""
                }
                
                title = title.substringBeforeLast(".")
                if (title.startsWith("msf:")) {
                    title = title.substringAfter("msf:")
                }
                
                if (title.isBlank()) {
                    val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    title = "Video_${sdf.format(java.util.Date())}"
                }

                _importProgress.value = 0.05f
                
                // 2. Copy to cache if needed
                val cacheUri = if (uri.scheme == "file") {
                    uri
                } else {
                    tempCacheFile = File(context.cacheDir, "import_temp_${videoId}.mp4")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempCacheFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalArgumentException("Seçilen video dosyası açılamadı.")
                    Uri.fromFile(tempCacheFile)
                }

                // 3. Encrypt & Save Video
                _importStatus.value = "Video kaydediliyor..."
                _importProgress.value = 0.2f
                val destVideoFile = SecureStorageHelper.getSecureVideoPath(context, videoId)
                copyUriToFile(context, cacheUri, destVideoFile)
                
                _importProgress.value = 0.4f
                _importStatus.value = "Video bilgileri analiz ediliyor..."
                val durationMs = MediaProcessingHelper.getVideoDurationMs(context, cacheUri)

                // 4. Generate 1 Thumbnail at 0ms
                _importStatus.value = "Kapak fotoğrafı oluşturuluyor..."
                _importProgress.value = 0.5f
                
                val thumbId = UUID.randomUUID().toString()
                val destThumbFile = SecureStorageHelper.getSecureThumbnailPath(context, thumbId)
                MediaProcessingHelper.extractThumbnailAtTime(context, cacheUri, 0L, destThumbFile)
                
                val thumbnailsList = if (destThumbFile.exists() && destThumbFile.length() > 0) {
                    listOf(ThumbnailEntity(id = thumbId, videoId = videoId, encryptedPath = destThumbFile.absolutePath, orderIndex = 0))
                } else {
                    emptyList()
                }

                // 5. Generate 3 Preview Clips
                _importStatus.value = "Önizleme klipleri oluşturuluyor... (0/3)"
                val previewsList = mutableListOf<PreviewClipEntity>()
                for (i in 0 until 3) {
                    _importStatus.value = "Önizleme klipleri oluşturuluyor... (${i + 1}/3)"
                    try {
                        val previewId = UUID.randomUUID().toString()
                        val destPreviewFile = SecureStorageHelper.getSecurePreviewPath(context, previewId)
                        MediaProcessingHelper.createPreviewClip(context, cacheUri, durationMs, destPreviewFile)
                        if (destPreviewFile.exists() && destPreviewFile.length() > 0) {
                            previewsList.add(
                                PreviewClipEntity(
                                    id = previewId,
                                    videoId = videoId,
                                    encryptedPath = destPreviewFile.absolutePath,
                                    orderIndex = i
                                )
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    _importProgress.value = 0.65f + ((i + 1) * 0.06f)
                }

                // 6. Save to Database
                _importStatus.value = "Kaydediliyor..."
                _importProgress.value = 0.95f
                val videoEntity = VideoEntity(
                    id = videoId,
                    title = title,
                    encryptedVideoPath = destVideoFile.absolutePath,
                    duration = durationMs,
                    addedAt = System.currentTimeMillis(),
                    lastWatchedAt = null,
                    lastWatchedPosition = 0L
                )
                
                // No tags initially (user can add later)
                videoRepository.insertVideo(
                    videoEntity,
                    emptyList(),
                    thumbnailsList,
                    previewsList
                )
                
                _importProgress.value = 1.0f
                _importStatus.value = "İçe aktarım tamamlandı!"
                SystemClock.sleep(500)

                // Optional: Attempt to delete original from gallery
                val sender = MediaProcessingHelper.getDeleteRequestIntentSender(context, uri)
                if (sender != null) {
                    _pendingDeleteSender.value = sender
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _importStatus.value = "Hata oluştu: ${e.localizedMessage}"
                SystemClock.sleep(2000)
            } finally {
                _isImporting.value = false
                try {
                    tempCacheFile?.let {
                        if (it.exists()) it.delete()
                    }
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }
    }"""

import_match = re.search(target_import, content, re.DOTALL)
if import_match:
    content = content.replace(import_match.group(0), auto_import_code + "\n")

# Remove preparePickedVideo entirely
prepare_match = re.search(target_prepare, content, re.DOTALL)
if prepare_match:
    content = content.replace(prepare_match.group(0), "")

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)

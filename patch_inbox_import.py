import re

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

batch_import_logic = """
    fun batchImportInboxVideos(context: Context, videos: List<File>, globalTags: List<TagEntity>) {
        if (_isImporting.value) return
        _isImporting.value = true
        _importProgress.value = 0f
        _importStatus.value = "Videolar hazırlanıyor..."

        viewModelScope.launch(Dispatchers.IO) {
            val totalVideos = videos.size
            for ((index, file) in videos.withIndex()) {
                val baseProgress = index.toFloat() / totalVideos
                val progressStep = 1.0f / totalVideos
                
                try {
                    _importStatus.value = "İçe aktarılıyor (${index + 1}/$totalVideos): ${file.name}"
                    _importProgress.value = baseProgress + (progressStep * 0.1f)
                    
                    val videoId = UUID.randomUUID().toString()
                    var title = file.nameWithoutExtension
                    if (title.isBlank()) {
                        val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                        title = "Video_${sdf.format(java.util.Date())}"
                    }
                    
                    // Directly use the file path instead of copying to cache
                    val cacheUri = Uri.fromFile(file)
                    
                    // 1. Encrypt & Save Video
                    _importProgress.value = baseProgress + (progressStep * 0.3f)
                    val destVideoFile = SecureStorageHelper.getSecureVideoPath(context, videoId)
                    copyUriToFile(context, cacheUri, destVideoFile)
                    
                    // 2. Info
                    val durationMs = MediaProcessingHelper.getVideoDurationMs(context, cacheUri)
                    
                    // 3. 1 Thumbnail at 0ms
                    _importProgress.value = baseProgress + (progressStep * 0.5f)
                    val thumbId = UUID.randomUUID().toString()
                    val destThumbFile = SecureStorageHelper.getSecureThumbnailPath(context, thumbId)
                    MediaProcessingHelper.extractThumbnailAtTime(context, cacheUri, 0L, destThumbFile)
                    
                    val thumbnailsList = if (destThumbFile.exists() && destThumbFile.length() > 0) {
                        listOf(ThumbnailEntity(id = thumbId, videoId = videoId, encryptedPath = destThumbFile.absolutePath, orderIndex = 0))
                    } else {
                        emptyList()
                    }
                    
                    // 4. 3 Previews
                    _importProgress.value = baseProgress + (progressStep * 0.7f)
                    val previewsList = mutableListOf<PreviewClipEntity>()
                    for (i in 0 until 3) {
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
                        } catch (e: Exception) {}
                    }
                    
                    // 5. Save to DB
                    _importProgress.value = baseProgress + (progressStep * 0.9f)
                    val videoEntity = VideoEntity(
                        id = videoId,
                        title = title,
                        encryptedVideoPath = destVideoFile.absolutePath,
                        duration = durationMs,
                        addedAt = System.currentTimeMillis(),
                        lastWatchedAt = null,
                        lastWatchedPosition = 0L
                    )
                    
                    videoRepository.insertVideo(videoEntity, globalTags, thumbnailsList, previewsList)
                    
                    // 6. Delete original file
                    file.delete()
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            _importProgress.value = 1.0f
            _importStatus.value = "Tüm videolar içe aktarıldı!"
            SystemClock.sleep(1000)
            _isImporting.value = false
            _inboxVideos.value = emptyList() // clear inbox
        }
    }
"""

if "fun batchImportInboxVideos" not in content:
    content = content.replace("fun autoImportVideo(context: Context, uri: Uri)", batch_import_logic + "\n    fun autoImportVideo(context: Context, uri: Uri)")

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)

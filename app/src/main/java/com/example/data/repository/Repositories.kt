package com.example.data.repository

import com.example.data.db.*
import kotlinx.coroutines.flow.Flow
import java.io.File

class VideoRepository(private val videoDao: VideoDao) {
    val allVideosWithDetails: Flow<List<VideoWithTagsAndAssets>> = videoDao.getAllVideosWithDetails()

    suspend fun getVideoDetailsById(videoId: String): VideoWithTagsAndAssets? {
        return videoDao.getVideoWithDetailsById(videoId)
    }

    fun getVideoDetailsFlowById(videoId: String): Flow<VideoWithTagsAndAssets?> {
        return videoDao.getVideoWithDetailsFlowById(videoId)
    }

    fun getVideosByUserId(userId: String): Flow<List<VideoWithTagsAndAssets>> {
        return videoDao.getVideosByUserId(userId)
    }

    suspend fun assignUserToVideo(videoId: String, userId: String?) {
        videoDao.assignUserToVideo(videoId, userId)
    }

    suspend fun insertVideo(
        video: VideoEntity,
        tags: List<TagEntity>,
        thumbnails: List<ThumbnailEntity>,
        previews: List<PreviewClipEntity>
    ) {
        videoDao.insertVideo(video)
        
        // Save relations
        tags.forEach { tag ->
            videoDao.insertVideoTagCrossRef(VideoTagCrossRef(video.id, tag.id))
        }
        
        videoDao.insertThumbnails(thumbnails)
        videoDao.insertPreviews(previews)
    }

    suspend fun updateVideoTitle(videoId: String, newTitle: String) {
        val details = videoDao.getVideoWithDetailsById(videoId)
        if (details != null) {
            videoDao.insertVideo(details.video.copy(title = newTitle))
        }
    }

    suspend fun updateVideoTags(videoId: String, tags: List<TagEntity>) {
        videoDao.deleteVideoTagCrossRefsByVideoId(videoId)
        tags.forEach { tag ->
            videoDao.insertVideoTagCrossRef(VideoTagCrossRef(videoId, tag.id))
        }
    }

    suspend fun updateLastWatched(videoId: String, position: Long, timestamp: Long) {
        videoDao.updateLastWatched(videoId, position, timestamp)
    }

    suspend fun deleteVideo(videoId: String, filesToDelete: List<File>) {
        videoDao.deleteVideoById(videoId)
        videoDao.deleteVideoTagCrossRefsByVideoId(videoId)
        videoDao.deleteThumbnailsByVideoId(videoId)
        videoDao.deletePreviewsByVideoId(videoId)
        
        // Delete all associated files
        filesToDelete.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }

    suspend fun updateVideoThumbnails(videoId: String, thumbnails: List<ThumbnailEntity>, filesToDelete: List<File>) {
        videoDao.deleteThumbnailsByVideoId(videoId)
        videoDao.insertThumbnails(thumbnails)
        
        filesToDelete.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }

    suspend fun updateVideoPreviews(videoId: String, previews: List<PreviewClipEntity>, filesToDelete: List<File>) {
        videoDao.deletePreviewsByVideoId(videoId)
        videoDao.insertPreviews(previews)
        
        filesToDelete.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }
}

class SettingsRepository(private val tagDao: TagDao, private val backgroundImageDao: BackgroundImageDao) {
    val allTags: Flow<List<TagEntity>> = tagDao.getAllTags()
    val allBackgroundImages: Flow<List<BackgroundImageEntity>> = backgroundImageDao.getAllBackgroundImages()

    suspend fun insertTag(tag: TagEntity) {
        tagDao.insertTag(tag)
    }

    suspend fun deleteTag(tagId: String) {
        tagDao.deleteTagById(tagId)
        tagDao.deleteVideoTagCrossRefsByTagId(tagId)
    }

    suspend fun insertBackgroundImage(image: BackgroundImageEntity) {
        backgroundImageDao.insertBackgroundImage(image)
    }

    suspend fun deleteBackgroundImage(id: String, filePath: String) {
        backgroundImageDao.deleteBackgroundImageById(id)
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        }
    }
}

class LogRepository(private val failedAttemptDao: FailedAttemptDao) {
    val allFailedAttempts: Flow<List<FailedAttemptEntity>> = failedAttemptDao.getAllFailedAttempts()

    suspend fun logFailedAttempt(attemptedCombination: String) {
        val attempt = FailedAttemptEntity(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            attemptedCombination = attemptedCombination
        )
        failedAttemptDao.insertFailedAttempt(attempt)
    }

    suspend fun clearFailedAttempts() {
        failedAttemptDao.clearAllFailedAttempts()
    }
}

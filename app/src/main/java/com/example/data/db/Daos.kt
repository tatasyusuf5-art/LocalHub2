package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class VideoWithTagsAndAssets(
    @Embedded val video: VideoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = VideoTagCrossRef::class,
            parentColumn = "videoId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "videoId"
    )
    val thumbnails: List<ThumbnailEntity>,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "videoId"
    )
    val previews: List<PreviewClipEntity>,

    @Relation(
        parentColumn = "userId",
        entityColumn = "id"
    )
    val user: UserEntity?
)

@Dao
interface VideoDao {
    @Transaction
    @Query("SELECT * FROM videos")
    fun getAllVideosWithDetails(): Flow<List<VideoWithTagsAndAssets>>

    @Transaction
    @Query("SELECT * FROM videos WHERE id = :videoId")
    suspend fun getVideoWithDetailsById(videoId: String): VideoWithTagsAndAssets?

    @Transaction
    @Query("SELECT * FROM videos WHERE id = :videoId")
    fun getVideoWithDetailsFlowById(videoId: String): kotlinx.coroutines.flow.Flow<VideoWithTagsAndAssets?>

    @Transaction
    @Query("SELECT * FROM videos WHERE userId = :userId")
    fun getVideosByUserId(userId: String): Flow<List<VideoWithTagsAndAssets>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("DELETE FROM videos WHERE id = :videoId")
    suspend fun deleteVideoById(videoId: String)

    @Query("UPDATE videos SET userId = :userId WHERE id = :videoId")
    suspend fun assignUserToVideo(videoId: String, userId: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoTagCrossRef(crossRef: VideoTagCrossRef)

    @Query("DELETE FROM video_tag_cross_ref WHERE videoId = :videoId")
    suspend fun deleteVideoTagCrossRefsByVideoId(videoId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThumbnails(thumbnails: List<ThumbnailEntity>)

    @Query("DELETE FROM thumbnails WHERE videoId = :videoId")
    suspend fun deleteThumbnailsByVideoId(videoId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreviews(previews: List<PreviewClipEntity>)

    @Query("DELETE FROM preview_clips WHERE videoId = :videoId")
    suspend fun deletePreviewsByVideoId(videoId: String)

    @Query("UPDATE videos SET lastWatchedPosition = :position, lastWatchedAt = :timestamp WHERE id = :videoId")
    suspend fun updateLastWatched(videoId: String, position: Long, timestamp: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTagById(tagId: String)

    @Query("DELETE FROM video_tag_cross_ref WHERE tagId = :tagId")
    suspend fun deleteVideoTagCrossRefsByTagId(tagId: String)
}

@Dao
interface FailedAttemptDao {
    @Query("SELECT * FROM failed_attempts ORDER BY timestamp DESC")
    fun getAllFailedAttempts(): Flow<List<FailedAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFailedAttempt(failedAttempt: FailedAttemptEntity)

    @Query("DELETE FROM failed_attempts")
    suspend fun clearAllFailedAttempts()
}

@Dao
interface BackgroundImageDao {
    @Query("SELECT * FROM background_images ORDER BY addedAt DESC")
    fun getAllBackgroundImages(): Flow<List<BackgroundImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackgroundImage(backgroundImage: BackgroundImageEntity)

    @Query("DELETE FROM background_images WHERE id = :id")
    suspend fun deleteBackgroundImageById(id: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY rank ASC")
    fun getAllUsersByRank(): Flow<List<UserEntity>>

    // SENKRON okuma - sıra hesaplaması için (Flow beklemeden)
    @Query("SELECT * FROM users ORDER BY rank ASC")
    suspend fun getAllUsersOnce(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserByIdFlow(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE name LIKE '%' || :query || '%' ORDER BY rank ASC")
    fun searchUsers(query: String): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: String)
}

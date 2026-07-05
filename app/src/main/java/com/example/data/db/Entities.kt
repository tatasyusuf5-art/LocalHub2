package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val encryptedVideoPath: String,
    val duration: Long,
    val addedAt: Long,
    val lastWatchedAt: Long?,
    val lastWatchedPosition: Long
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String
)

@Entity(tableName = "video_tag_cross_ref", primaryKeys = ["videoId", "tagId"])
data class VideoTagCrossRef(
    val videoId: String,
    val tagId: String
)

@Entity(tableName = "thumbnails")
data class ThumbnailEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val encryptedPath: String,
    val orderIndex: Int
)

@Entity(tableName = "preview_clips")
data class PreviewClipEntity(
    @PrimaryKey val id: String,
    val videoId: String,
    val encryptedPath: String,
    val orderIndex: Int
)

@Entity(tableName = "failed_attempts")
data class FailedAttemptEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val attemptedCombination: String
)

@Entity(tableName = "background_images")
data class BackgroundImageEntity(
    @PrimaryKey val id: String,
    val encryptedPath: String,
    val isRandomPool: Boolean,
    val addedAt: Long
)

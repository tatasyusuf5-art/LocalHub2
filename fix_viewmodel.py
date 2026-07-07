import re

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

# Add back StorageStats and the closing bracket
missing_code = """
    // Storage info helper
    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val totalSize = SecureStorageHelper.getTotalSecureSpaceUsed(context)
        val details = videoRepository.allVideosWithDetails.first()
        val videoCount = details.size
        var thumbCount = 0
        var previewCount = 0
        details.forEach {
            thumbCount += it.thumbnails.size
            previewCount += it.previews.size
        }
        StorageStats(
            totalBytes = totalSize,
            videoCount = videoCount,
            thumbnailCount = thumbCount,
            previewCount = previewCount
        )
    }
}

data class StorageStats(
    val totalBytes: Long,
    val videoCount: Int,
    val thumbnailCount: Int,
    val previewCount: Int
)
"""

if "data class StorageStats" not in content:
    if content.endswith("    }\n"):
        content = content + missing_code
    else:
        content = content + "\n}\n" + missing_code

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)

package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import android.util.Log
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.AES256CryptoManager
import com.example.data.db.*
import com.example.data.repository.LogRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.VideoRepository
import com.example.data.util.MediaProcessingHelper
import com.example.data.util.SecureStorageHelper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultRenderersFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlin.random.Random

data class TempThumbnail(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long,
    val encryptedFilePath: String
)

enum class CalcState {
    IDLE,
    STEP1_DONE,
    UNLOCKED
}

enum class SortType {
    RANDOM,
    NEWEST,
    LAST_WATCHED,
    NAME_AZ
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    
    val videoRepository = VideoRepository(database.videoDao())
    val settingsRepository = SettingsRepository(database.tagDao(), database.backgroundImageDao())
    val logRepository = LogRepository(database.failedAttemptDao())

    // Preview cache kaldırıldı - reset sorunu çözümü

    // --- SharedPreferences for Persistent Settings ---
    private val prefs = context.getSharedPreferences("localhub_settings", Context.MODE_PRIVATE)

    // --- Calculator State ---
    private val _calcDisplay = MutableStateFlow("0")
    val calcDisplay: StateFlow<String> = _calcDisplay.asStateFlow()

    private val _calcExpression = MutableStateFlow("")
    val calcExpression: StateFlow<String> = _calcExpression.asStateFlow()

    private var calcCurrentState = CalcState.IDLE
    private var isNewInput = true
    private var lastOperator: String? = null
    private var operand1: Double? = null

    // --- Hub Screen States ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilterTagId = MutableStateFlow<String?>(null)
    val selectedFilterTagId: StateFlow<String?> = _selectedFilterTagId.asStateFlow()

    private val _activeSortType = MutableStateFlow(SortType.RANDOM)
    val activeSortType: StateFlow<SortType> = _activeSortType.asStateFlow()

    // Multi-select state
    private val _isInSelectionMode = MutableStateFlow(false)
    val isInSelectionMode: StateFlow<Boolean> = _isInSelectionMode.asStateFlow()

    private val _selectedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedVideoIds: StateFlow<Set<String>> = _selectedVideoIds.asStateFlow()

    // UI-level random shuffler seed that can be re-triggered on Hub open
    private val hubOpenTrigger = MutableStateFlow(0L)

    // Videos listing composed of database Flow, search query, filter tag, and sort type
    val videosList: StateFlow<List<VideoWithTagsAndAssets>> = combine(
        videoRepository.allVideosWithDetails,
        _searchQuery,
        _selectedFilterTagId,
        _activeSortType,
        hubOpenTrigger
    ) { rawVideos, query, filterTagId, sortType, trigger ->
        var list = rawVideos.filter { item ->
            val matchesQuery = query.isEmpty() ||
                    item.video.title.contains(query, ignoreCase = true) ||
                    item.tags.any { it.name.contains(query, ignoreCase = true) }
            
            val matchesTag = filterTagId == null || item.tags.any { it.id == filterTagId }
            
            matchesQuery && matchesTag
        }

        list = when (sortType) {
            SortType.RANDOM -> {
                // Shuffle deterministically based on trigger, or standard shuffle
                val r = Random(trigger)
                list.shuffled(r)
            }
            SortType.NEWEST -> list.sortedByDescending { it.video.addedAt }
            SortType.LAST_WATCHED -> list.sortedWith { a, b ->
                val timeA = a.video.lastWatchedAt ?: 0L
                val timeB = b.video.lastWatchedAt ?: 0L
                timeB.compareTo(timeA) // Newest watched first
            }
            SortType.NAME_AZ -> list.sortedBy { it.video.title.lowercase() }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Tags State ---
    val allTags: StateFlow<List<TagEntity>> = settingsRepository.allTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Background Settings State ---
    val allBackgrounds: StateFlow<List<BackgroundImageEntity>> = settingsRepository.allBackgroundImages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRandomBackgroundEnabled = MutableStateFlow(prefs.getBoolean("random_bg", false))
    val isRandomBackgroundEnabled: StateFlow<Boolean> = _isRandomBackgroundEnabled.asStateFlow()

    private val _selectedBackgroundId = MutableStateFlow(prefs.getString("selected_bg_id", "") ?: "")
    val selectedBackgroundId: StateFlow<String> = _selectedBackgroundId.asStateFlow()

    // Current active background image path (reactive)
    private val _activeBackgroundPath = MutableStateFlow<String?>(null)
    val activeBackgroundPath: StateFlow<String?> = _activeBackgroundPath.asStateFlow()

    // Failed attempts list
    val failedAttempts: StateFlow<List<FailedAttemptEntity>> = logRepository.allFailedAttempts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Video Import / Processing States ---
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress.asStateFlow()

    private val _importStatus = MutableStateFlow("")
    val importStatus: StateFlow<String> = _importStatus.asStateFlow()

    private val _pickedVideoUri = MutableStateFlow<Uri?>(null)
    val pickedVideoUri: StateFlow<Uri?> = _pickedVideoUri.asStateFlow()

    private val _originalPickedVideoUri = MutableStateFlow<Uri?>(null)
    val originalPickedVideoUri: StateFlow<Uri?> = _originalPickedVideoUri.asStateFlow()

    private val _pendingDeleteSender = MutableStateFlow<IntentSender?>(null)
    val pendingDeleteSender: StateFlow<IntentSender?> = _pendingDeleteSender.asStateFlow()

    private val _isPreparingVideo = MutableStateFlow(false)
    val isPreparingVideo: StateFlow<Boolean> = _isPreparingVideo.asStateFlow()

    private val _tempThumbnails = MutableStateFlow<List<TempThumbnail>>(emptyList())
    val tempThumbnails: StateFlow<List<TempThumbnail>> = _tempThumbnails.asStateFlow()

    fun deleteTempThumbnail(id: String) {
        val current = _tempThumbnails.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val thumb = current[index]
            try {
                File(thumb.encryptedFilePath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            current.removeAt(index)
            _tempThumbnails.value = current
        }
    }

    fun addTempThumbnailAtTime(context: Context, timeSeconds: Float) {
        val pickedUri = _pickedVideoUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timeMs = (timeSeconds * 1000).toLong()
                val tempId = UUID.randomUUID().toString()
                val tempFile = File(context.cacheDir, "temp_thumb_${tempId}.enc")
                MediaProcessingHelper.extractThumbnailAtTime(context, pickedUri, timeMs, tempFile)
                val newThumb = TempThumbnail(
                    id = tempId,
                    timeMs = timeMs,
                    encryptedFilePath = tempFile.absolutePath
                )
                val current = _tempThumbnails.value.toMutableList()
                current.add(newThumb)
                _tempThumbnails.value = current
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateInitialThumbnails(context: Context, videoUri: Uri) {
        val durationMs = MediaProcessingHelper.getVideoDurationMs(context, videoUri)
        val safeDuration = if (durationMs > 1000) durationMs else 1000L
        val list = mutableListOf<TempThumbnail>()
        
        // Delete old temp thumbnails if any
        _tempThumbnails.value.forEach {
            try {
                File(it.encryptedFilePath).delete()
            } catch (e: Exception) {}
        }

        for (i in 0 until 5) {
            val randomTimeMs = Random.nextLong(0, safeDuration)
            val thumbId = UUID.randomUUID().toString()
            val tempFile = File(context.cacheDir, "temp_thumb_${thumbId}.enc")
            try {
                MediaProcessingHelper.extractThumbnailAtTime(context, videoUri, randomTimeMs, tempFile)
                list.add(
                    TempThumbnail(
                        id = thumbId,
                        timeMs = randomTimeMs,
                        encryptedFilePath = tempFile.absolutePath
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _tempThumbnails.value = list
    }

    fun setPickedVideoUri(uri: Uri?) {
        val oldUri = _pickedVideoUri.value
        if (oldUri != null && oldUri.scheme == "file") {
            try {
                val file = File(oldUri.path ?: "")
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        _pickedVideoUri.value = uri
        if (uri == null) {
            _originalPickedVideoUri.value = null
        }
    }

    fun clearPendingDeleteSender() {
        _pendingDeleteSender.value = null
        _originalPickedVideoUri.value = null
    }

    fun preparePickedVideo(context: Context, uri: Uri) {
        _isPreparingVideo.value = true
        _importProgress.value = 0.0f
        _importStatus.value = "Video hazırlanıyor..."
        _pickedVideoUri.value = null
        _originalPickedVideoUri.value = uri // Save original content-scheme URI
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tempId = UUID.randomUUID().toString()
                val tempCacheFile = File(context.cacheDir, "import_temp_${tempId}.mp4")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempCacheFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalArgumentException("Seçilen video dosyası açılamadı.")
                
                _pickedVideoUri.value = Uri.fromFile(tempCacheFile)
                generateInitialThumbnails(context, Uri.fromFile(tempCacheFile))
            } catch (e: Exception) {
                e.printStackTrace()
                _importStatus.value = "Hata oluştu: ${e.localizedMessage}"
            } finally {
                _isPreparingVideo.value = false
            }
        }
    }

    init {
        // Safe clear temp files on start
        viewModelScope.launch(Dispatchers.IO) {
            SecureStorageHelper.clearTempFiles(context)
        }
        updateActiveBackground()
    }

    // Call this whenever Hub is opened
    fun onHubOpened() {
        hubOpenTrigger.value = System.currentTimeMillis()
        updateActiveBackground()
    }

    // --- Calculator Input Logic ---
    fun onCalcKey(key: String) {
        when (key) {
            "C" -> {
                _calcDisplay.value = "0"
                _calcExpression.value = ""
                isNewInput = true
                lastOperator = null
                operand1 = null
                calcCurrentState = CalcState.IDLE
            }
            "+", "-", "x", "÷", "%" -> {
                val currentNum = _calcDisplay.value.toDoubleOrNull() ?: 0.0
                val op1 = operand1
                val op = lastOperator
                if (op1 != null && op != null && !isNewInput) {
                    val result = calculate(op1, currentNum, op)
                    operand1 = result
                    _calcDisplay.value = formatNumber(result)
                    _calcExpression.value = "${formatNumber(result)} $key"
                } else {
                    operand1 = currentNum
                    _calcExpression.value = "${formatNumber(currentNum)} $key"
                }
                lastOperator = key
                isNewInput = true
            }
            "±" -> {
                val num = _calcDisplay.value.toDoubleOrNull() ?: 0.0
                if (num != 0.0) {
                    _calcDisplay.value = formatNumber(-num)
                }
            }
            "=" -> {
                val op1 = operand1
                val op2 = _calcDisplay.value.toDoubleOrNull() ?: 0.0
                val op = lastOperator
                if (op1 != null && op != null) {
                    val result = calculate(op1, op2, op)
                    val resultStr = formatNumber(result)
                    
                    val expressionStr = "${formatNumber(op1)}${op}${formatNumber(op2)}"
                    _calcExpression.value = "$expressionStr="
                    _calcDisplay.value = resultStr

                    // Process Passcode Entry
                    checkPasscodeState(expressionStr, resultStr)

                    operand1 = null
                    lastOperator = null
                    isNewInput = true
                }
            }
            "." -> {
                if (isNewInput) {
                    _calcDisplay.value = "0."
                    isNewInput = false
                } else if (!_calcDisplay.value.contains(".")) {
                    _calcDisplay.value += "."
                }
            }
            else -> { // Digit keys "0"-"9"
                if (isNewInput || _calcDisplay.value == "0") {
                    _calcDisplay.value = key
                    isNewInput = false
                } else {
                    _calcDisplay.value += key
                }
            }
        }
    }

    private fun checkPasscodeState(expression: String, result: String) {
        // Normalize whitespaces for checking
        val cleanExpr = expression.replace(" ", "")
        
        when (calcCurrentState) {
            CalcState.IDLE -> {
                if (cleanExpr == "1000-7" && result == "993") {
                    calcCurrentState = CalcState.STEP1_DONE
                } else {
                    // Check if it looks like a failed code attempt and log it
                    if (cleanExpr.contains("1000") || cleanExpr.contains("993")) {
                        logFailedAttempt(expression + "=")
                    }
                    calcCurrentState = CalcState.IDLE
                }
            }
            CalcState.STEP1_DONE -> {
                if (cleanExpr == "993-7" && result == "986") {
                    calcCurrentState = CalcState.UNLOCKED
                } else {
                    // Fail log
                    logFailedAttempt("993" + expression + "=")
                    calcCurrentState = CalcState.IDLE
                }
            }
            CalcState.UNLOCKED -> {
                // Already unlocked
            }
        }
    }

    fun lockVault() {
        calcCurrentState = CalcState.IDLE
        _calcDisplay.value = "0"
        _calcExpression.value = ""
        isNewInput = true
    }

    fun isVaultUnlocked(): Boolean {
        return calcCurrentState == CalcState.UNLOCKED
    }

    private fun logFailedAttempt(expr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            logRepository.logFailedAttempt(expr)
        }
    }

    fun clearFailedAttemptsLog() {
        viewModelScope.launch(Dispatchers.IO) {
            logRepository.clearFailedAttempts()
        }
    }

    private fun calculate(op1: Double, op2: Double, op: String): Double {
        return when (op) {
            "+" -> op1 + op2
            "-" -> op1 - op2
            "x" -> op1 * op2
            "÷" -> if (op2 != 0.0) op1 / op2 else 0.0
            "%" -> op1 % op2
            else -> 0.0
        }
    }

    private fun formatNumber(num: Double): String {
        return if (num == num.toLong().toDouble()) {
            num.toLong().toString()
        } else {
            num.toString()
        }
    }

    // --- Search & Filters ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterTag(tagId: String?) {
        _selectedFilterTagId.value = tagId
    }

    fun setSortType(sortType: SortType) {
        _activeSortType.value = sortType
    }

    // --- Multi-select Mode ---
    fun toggleVideoSelection(videoId: String) {
        val current = _selectedVideoIds.value
        if (current.contains(videoId)) {
            _selectedVideoIds.value = current - videoId
        } else {
            _selectedVideoIds.value = current + videoId
        }
    }

    fun startSelectionMode(videoId: String? = null) {
        _isInSelectionMode.value = true
        _selectedVideoIds.value = if (videoId != null) setOf(videoId) else emptySet()
    }

    fun exitSelectionMode() {
        _isInSelectionMode.value = false
        _selectedVideoIds.value = emptySet()
    }

    fun deleteSelectedVideos() {
        val idsToDelete = _selectedVideoIds.value
        viewModelScope.launch(Dispatchers.IO) {
            idsToDelete.forEach { id ->
                deleteVideoInternal(id)
            }
            withContext(Dispatchers.Main) {
                exitSelectionMode()
            }
        }
    }

    private suspend fun deleteVideoInternal(videoId: String) {
        val details = videoRepository.getVideoDetailsById(videoId) ?: return
        val files = mutableListOf<File>()
        
        // Add secure path of video itself
        files.add(File(details.video.encryptedVideoPath))
        
        // Add thumbnails
        details.thumbnails.forEach {
            files.add(File(it.encryptedPath))
        }
        
        // Add previews
        details.previews.forEach {
            files.add(File(it.encryptedPath))
        }

        videoRepository.deleteVideo(videoId, files)
    }

    fun restoreVideoToGallery(context: Context, videoId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            var uri: Uri? = null
            try {
                val details = videoRepository.getVideoDetailsById(videoId)
                if (details == null) {
                    withContext(Dispatchers.Main) { onFailure("Geri yükleme başarısız: Video bulunamadı") }
                    return@launch
                }
                
                val encryptedFile = File(details.video.encryptedVideoPath)
                if (!encryptedFile.exists()) {
                    withContext(Dispatchers.Main) { onFailure("Geri yükleme başarısız: Şifreli video dosyası bulunamadı") }
                    return@launch
                }

                // Step 1: Write decrypted file to temp directory
                tempFile = File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}.mp4")
                AES256CryptoManager.decryptManualToFile(encryptedFile, tempFile)

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    withContext(Dispatchers.Main) { onFailure("Geri yükleme başarısız: Şifreli dosya çözülemedi veya boş.") }
                    return@launch
                }

                // Step 2: Write to Gallery via MediaStore
                val title = details.video.title
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "$title.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Restored")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }

                    uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri == null) {
                        throw Exception("Galeriye kayıt oluşturulamadı. Lütfen depolama izinlerini kontrol edin.")
                    }

                    val outputStream = resolver.openOutputStream(uri) ?: throw Exception("Galeri çıkış akışı açılamadı")
                    outputStream.use { out ->
                        FileInputStream(tempFile).use { fileInputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                            }
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                } else {
                    // For Android < Q, write directly to external storage DCIM
                    val dcimDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
                    val restoredDir = File(dcimDir, "Restored")
                    if (!restoredDir.exists()) {
                        restoredDir.mkdirs()
                    }
                    val outFile = File(restoredDir, "${title}_${System.currentTimeMillis()}.mp4")
                    
                    FileOutputStream(outFile).use { out ->
                        FileInputStream(tempFile).use { fileInputStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                    
                    // Trigger media scanner for older Android versions
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(outFile.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                }

                // Delete decrypted temporary file
                try {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Remove video from database and delete its secure/encrypted storage files
                deleteVideoInternal(videoId)

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // If anything fails, cleanup the MediaStore URI we just created
                uri?.let {
                    try {
                        context.contentResolver.delete(it, null, null)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
                // Cleanup decrypted temporary file if it still exists
                tempFile?.let {
                    try {
                        if (it.exists()) {
                            it.delete()
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
                withContext(Dispatchers.Main) {
                    onFailure("Geri yükleme başarısız: ${e.message ?: e.toString()}")
                }
            }
        }
    }

    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        var path: String? = null
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    path = cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return path
    }

    // --- Background Image Management ---
    fun toggleRandomBackground(enabled: Boolean) {
        _isRandomBackgroundEnabled.value = enabled
        prefs.edit().putBoolean("random_bg", enabled).apply()
        updateActiveBackground()
    }

    fun selectActiveBackground(bgId: String) {
        _selectedBackgroundId.value = bgId
        prefs.edit().putString("selected_bg_id", bgId).apply()
        updateActiveBackground()
    }

    fun updateActiveBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = settingsRepository.allBackgroundImages.first()
            if (list.isEmpty()) {
                _activeBackgroundPath.value = null
                return@launch
            }

            if (_isRandomBackgroundEnabled.value) {
                // Pick random from pool
                val randomBg = list.randomOrNull()
                _activeBackgroundPath.value = randomBg?.encryptedPath
            } else {
                val savedId = _selectedBackgroundId.value
                val matching = list.firstOrNull { it.id == savedId }
                _activeBackgroundPath.value = matching?.encryptedPath ?: list.firstOrNull()?.encryptedPath
            }
        }
    }

    fun addBackgroundImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bgId = UUID.randomUUID().toString()
                val destFile = SecureStorageHelper.getSecureBackgroundPath(context, bgId)
                AES256CryptoManager.encryptFile(context, uri, destFile)

                val bgEntity = BackgroundImageEntity(
                    id = bgId,
                    encryptedPath = destFile.absolutePath,
                    isRandomPool = true,
                    addedAt = System.currentTimeMillis()
                )
                settingsRepository.insertBackgroundImage(bgEntity)
                
                // Select if first one
                if (_selectedBackgroundId.value.isEmpty()) {
                    _selectedBackgroundId.value = bgId
                    prefs.edit().putString("selected_bg_id", bgId).apply()
                }

                updateActiveBackground()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteBackgroundImage(bgId: String, encryptedPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.deleteBackgroundImage(bgId, encryptedPath)
            if (_selectedBackgroundId.value == bgId) {
                _selectedBackgroundId.value = ""
                prefs.edit().putString("selected_bg_id", "").apply()
            }
            updateActiveBackground()
        }
    }

    // --- Tag Operations ---
    fun addTag(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val tag = TagEntity(id = UUID.randomUUID().toString(), name = name)
            settingsRepository.insertTag(tag)
        }
    }

    fun deleteTag(tagId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.deleteTag(tagId)
        }
    }

    // --- Video Settings Modifications ---
    fun updateVideoTitle(videoId: String, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            videoRepository.updateVideoTitle(videoId, newTitle)
        }
    }

    fun updateVideoTags(videoId: String, tags: List<TagEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            videoRepository.updateVideoTags(videoId, tags)
        }
    }

    fun deleteSingleVideo(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                deleteVideoInternal(videoId)
            } catch (e: Exception) {
                Log.e("AppViewModel", "Failed to delete video", e)
            }
        }
    }

    fun refreshVideoThumbnails(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val details = videoRepository.getVideoDetailsById(videoId) ?: return@launch
            val decryptedSourceTemp = decryptToTempFileBlocking(File(details.video.encryptedVideoPath))
            val videoUri = Uri.fromFile(decryptedSourceTemp)
            
            // Delete old files
            val oldFiles = details.thumbnails.map { File(it.encryptedPath) }
            
            // Create new entities
            val newThumbnails = mutableListOf<ThumbnailEntity>()
            val durationMs = details.video.duration
            val safeDuration = if (durationMs > 1000) durationMs else 1000L

            for (i in 0 until 5) {
                val thumbId = UUID.randomUUID().toString()
                val destFile = SecureStorageHelper.getSecureThumbnailPath(context, thumbId)
                val randomTimeMs = kotlin.random.Random.nextLong(0, safeDuration)
                try {
                    MediaProcessingHelper.extractThumbnailAtTime(context, videoUri, randomTimeMs, destFile)
                    if (destFile.exists() && destFile.length() > 0) {
                        newThumbnails.add(
                            ThumbnailEntity(
                                id = thumbId,
                                videoId = videoId,
                                encryptedPath = destFile.absolutePath,
                                orderIndex = i
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            videoRepository.updateVideoThumbnails(videoId, newThumbnails, oldFiles)
            
            // Clean temp decrypted source
            decryptedSourceTemp.delete()
        }
    }

    fun refreshVideoPreviews(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val details = videoRepository.getVideoDetailsById(videoId) ?: return@launch
            
            // Delete old previews immediately so UI updates
            val oldFiles = details.previews.map { File(it.encryptedPath) }
            videoRepository.updateVideoPreviews(videoId, emptyList(), oldFiles)

            val decryptedSourceTemp = decryptToTempFileBlocking(File(details.video.encryptedVideoPath))
            val videoUri = Uri.fromFile(decryptedSourceTemp)

            // Generate 1 new preview to be fast
            val newPreviews = mutableListOf<PreviewClipEntity>()
            val duration = details.video.duration

            for (i in 0 until 1) {
                val previewId = UUID.randomUUID().toString()
                val destFile = SecureStorageHelper.getSecurePreviewPath(context, previewId)
                
                try {
                    MediaProcessingHelper.createPreviewClip(context, videoUri, duration, destFile)
                    newPreviews.add(
                        PreviewClipEntity(
                            id = previewId,
                            videoId = videoId,
                            encryptedPath = destFile.absolutePath,
                            orderIndex = i
                        )
                    )
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Preview generation failed", e)
                }
            }

            videoRepository.updateVideoPreviews(videoId, newPreviews.toList(), emptyList())
            
            decryptedSourceTemp.delete()
        }
    }

    // --- Temporary Decryption For Playback Helper ---
    suspend fun decryptToTempFileBlocking(encryptedFile: File): File = withContext(Dispatchers.IO) {
        val tempFile = File(SecureStorageHelper.getTempDirectory(context), "play_${System.currentTimeMillis()}.mp4")
        AES256CryptoManager.decryptToTempFile(encryptedFile, tempFile)
        tempFile
    }

    // Cache YOK - her seferinde sıfırdan çöz, reset sorunu çözümü
    suspend fun decryptPreviewToTempFileBlocking(encryptedFile: File): File = withContext(Dispatchers.IO) {
        val tempFile = File(
            SecureStorageHelper.getTempDirectory(context),
            "preview_play_${UUID.randomUUID()}.mp4"
        )
        AES256CryptoManager.decryptToTempFile(encryptedFile, tempFile)
        tempFile
    }

    var previewPlayer: ExoPlayer? = null
        private set

    fun getOrCreatePreviewPlayer(context: Context): ExoPlayer {
        val player = previewPlayer
        if (player != null) return player
        
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .build().also {
                previewPlayer = it
            }
    }

    fun releasePreviewPlayer() {
        try {
            previewPlayer?.pause()
            previewPlayer?.stop()
            previewPlayer?.clearMediaItems()
            previewPlayer?.release()
            previewPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        releasePreviewPlayer()
    }

    // --- Video Import Execution ---
    fun importVideo(videoUri: Uri, title: String, selectedTags: List<TagEntity>) {
        if (_isImporting.value) return
        _isImporting.value = true
        _importProgress.value = 0f
        _importStatus.value = "Kuyruğa alınıyor..."

        viewModelScope.launch(Dispatchers.IO) {
            val videoId = UUID.randomUUID().toString()
            var tempCacheFile: File? = null
            try {
                _importStatus.value = "Video hazırlanıyor..."
                _importProgress.value = 0.05f

                // Use file directly if already copied to cache, otherwise copy it
                val cacheUri = if (videoUri.scheme == "file") {
                    videoUri
                } else {
                    tempCacheFile = File(context.cacheDir, "import_temp_${videoId}.mp4")
                    context.contentResolver.openInputStream(videoUri)?.use { input ->
                        FileOutputStream(tempCacheFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalArgumentException("Seçilen video dosyası açılamadı.")
                    Uri.fromFile(tempCacheFile)
                }

                // Step 1: Copy and Encrypt Video itself
                _importStatus.value = "Video şifreleniyor..."
                _importProgress.value = 0.2f
                
                val destVideoFile = SecureStorageHelper.getSecureVideoPath(context, videoId)
                AES256CryptoManager.encryptFile(context, cacheUri, destVideoFile)
                
                _importProgress.value = 0.4f
                _importStatus.value = "Video bilgileri analiz ediliyor..."

                // Calculate duration
                val durationMs = MediaProcessingHelper.getVideoDurationMs(context, cacheUri)

                // Step 2: Save Customized Thumbnails
                _importStatus.value = "Kapak fotoğrafları kaydediliyor..."
                _importProgress.value = 0.5f
                val thumbnailsList = mutableListOf<ThumbnailEntity>()
                val userThumbnails = _tempThumbnails.value

                // Fallback: if user deleted all of them, generate at least one at 0ms
                val finalThumbsToSave = if (userThumbnails.isEmpty()) {
                    val fallbackTempId = UUID.randomUUID().toString()
                    val fallbackTempFile = File(context.cacheDir, "temp_thumb_${fallbackTempId}.enc")
                    try {
                        MediaProcessingHelper.extractThumbnailAtTime(context, cacheUri, 0L, fallbackTempFile)
                        listOf(TempThumbnail(id = fallbackTempId, timeMs = 0L, encryptedFilePath = fallbackTempFile.absolutePath))
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    userThumbnails
                }

                finalThumbsToSave.forEachIndexed { index, tempThumb ->
                    val thumbId = UUID.randomUUID().toString()
                    val destThumbFile = SecureStorageHelper.getSecureThumbnailPath(context, thumbId)
                    
                    val srcFile = File(tempThumb.encryptedFilePath)
                    if (srcFile.exists()) {
                        srcFile.copyTo(destThumbFile, overwrite = true)
                    }
                    
                    thumbnailsList.add(
                        ThumbnailEntity(
                            id = thumbId,
                            videoId = videoId,
                            encryptedPath = destThumbFile.absolutePath,
                            orderIndex = index
                        )
                    )
                    _importProgress.value = 0.5f + (index + 1) * (0.15f / finalThumbsToSave.size.coerceAtLeast(1))
                }

                // Cleanup temporary cached thumbnails
                _tempThumbnails.value.forEach {
                    try {
                        File(it.encryptedFilePath).delete()
                    } catch (e: Exception) {}
                }
                _tempThumbnails.value = emptyList()

                // Step 3: Generate 5 Preview Clips sequentially
                _importStatus.value = "Önizleme klipleri oluşturuluyor... (0/5)"
                val previewsList = mutableListOf<PreviewClipEntity>()

                for (i in 0 until 5) {
                    _importStatus.value = "Önizleme klipleri oluşturuluyor... (${i + 1}/5)"
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

                // Step 4: Save to Database
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

                videoRepository.insertVideo(
                    videoEntity,
                    selectedTags,
                    thumbnailsList,
                    previewsList
                )

                _importProgress.value = 1.0f
                _importStatus.value = "İçe aktarım tamamlandı!"
                SystemClock.sleep(500) // Small pause for the user to see completion

                // Attempt to delete original from gallery
                val origUri = _originalPickedVideoUri.value
                if (origUri != null) {
                    val sender = MediaProcessingHelper.getDeleteRequestIntentSender(context, origUri)
                    if (sender != null) {
                        _pendingDeleteSender.value = sender
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _importStatus.value = "Hata oluştu: ${e.localizedMessage}"
            } finally {
                _isImporting.value = false
                try {
                    tempCacheFile?.let {
                        if (it.exists()) {
                            it.delete()
                        }
                    }
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }
    }

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

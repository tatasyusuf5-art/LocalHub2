package com.example


import androidx.compose.ui.platform.LocalConfiguration
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.PlaybackException
import android.util.Log
import android.content.ContentValues
import android.provider.MediaStore
import java.io.FileInputStream
import com.example.data.crypto.EncryptedFileDataSourceFactory
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.SurfaceView
import android.view.SurfaceHolder
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.PressInteraction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import com.example.data.crypto.AES256CryptoManager
import com.example.data.db.BackgroundImageEntity
import com.example.data.db.TagEntity
import com.example.data.db.VideoWithTagsAndAssets
import com.example.data.util.SecureStorageHelper
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.CalcState
import com.example.ui.viewmodel.SortType
import com.example.ui.viewmodel.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val appViewModel: AppViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return AppViewModel(context.applicationContext as Application) as T
                        }
                    }
                )

                // Main App Navigation
                MainNavigation(viewModel = appViewModel)
            }
        }
    }
}

// --- Navigation Definitions ---
const val ROUTE_CALCULATOR = "calculator"
const val ROUTE_HUB = "hub"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_PROCESSING = "processing"

@Composable
fun MainNavigation(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val activeBgPath by viewModel.activeBackgroundPath.collectAsStateWithLifecycle()
    val bgBitmap = rememberEncryptedImage(activeBgPath ?: "")

    val pendingDeleteSender by viewModel.pendingDeleteSender.collectAsStateWithLifecycle()

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { _ ->
        viewModel.clearPendingDeleteSender()
    }

    LaunchedEffect(pendingDeleteSender) {
        val sender = pendingDeleteSender
        if (sender != null) {
            try {
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.clearPendingDeleteSender()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Render Background Image only if on hub/settings/processing, NOT on calculator screen
        if (bgBitmap != null && currentRoute != ROUTE_CALCULATOR) {
            Image(
                bitmap = bgBitmap,
                contentDescription = "Custom vault background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Dark translucent layer to keep text readable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
            )
        } else {
            // Standard black background for calculator or when no image is set
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black)
            )
        }

        NavHost(
            navController = navController,
            startDestination = ROUTE_CALCULATOR
        ) {
            composable(ROUTE_CALCULATOR) {
                CalculatorScreen(viewModel = viewModel, onNavigateToHub = {
                    navController.navigate(ROUTE_HUB) {
                        popUpTo(ROUTE_CALCULATOR) { inclusive = false }
                    }
                })
            }
            composable(ROUTE_HUB) {
                HubScreen(viewModel = viewModel, onNavigateToSettings = {
                    navController.navigate(ROUTE_SETTINGS)
                }, onNavigateToProcessing = {
                    navController.navigate(ROUTE_PROCESSING)
                })
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(viewModel = viewModel, onBack = {
                    navController.popBackStack()
                })
            }
            composable(ROUTE_PROCESSING) {
                VideoProcessingScreen(viewModel = viewModel, onComplete = {
                    navController.navigate(ROUTE_HUB) {
                        popUpTo(ROUTE_PROCESSING) { inclusive = true }
                    }
                })
            }
        }
    }
}

// --- Image Decryption Helper ---
@Composable
fun rememberEncryptedImage(filePath: String): ImageBitmap? {
    var bitmap by remember(filePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(filePath) {
        if (filePath.isEmpty()) {
            bitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val encryptedBytes = file.readBytes()
                    val rawBytes = AES256CryptoManager.decryptBytes(encryptedBytes)
                    val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    if (bmp != null) {
                        bitmap = bmp.asImageBitmap()
                    }
                } else {
                    bitmap = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                bitmap = null
            }
        }
    }
    return bitmap
}

// ==========================================
// 1. CALCULATOR SCREEN
// ==========================================
@Composable
fun CalculatorScreen(
    viewModel: AppViewModel,
    onNavigateToHub: () -> Unit
) {
    val display by viewModel.calcDisplay.collectAsStateWithLifecycle()
    val expression by viewModel.calcExpression.collectAsStateWithLifecycle()

    // Monitor vault unlock status to navigate
    LaunchedEffect(Unit) {
        viewModel.lockVault() // lock on entry
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Text displays
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = expression,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        textAlign = TextAlign.End
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = display,
                    style = MaterialTheme.typography.displayLarge.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 48.sp,
                        textAlign = TextAlign.End
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("calculator_display")
                )
            }

            // Keyboard
            val buttons = listOf(
                listOf("C", "±", "%", "÷"),
                listOf("7", "8", "9", "x"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=")
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                buttons.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { char ->
                            val isOperator = char in listOf("÷", "x", "-", "+", "=")
                            val isAction = char in listOf("C", "±", "%")
                            
                            val bg = when {
                                isOperator -> PrimaryOrange
                                isAction -> Color.DarkGray
                                else -> CardBackground
                            }
                            val fg = when {
                                isOperator || isAction -> TextPrimary
                                else -> TextPrimary
                            }

                            val weight = if (char == "0") 2f else 1f

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(weight)
                                    .aspectRatio(if (char == "0") 2f else 1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(bg)
                                    .clickable {
                                        viewModel.onCalcKey(char)
                                        if (viewModel.isVaultUnlocked()) {
                                            onNavigateToHub()
                                        }
                                    }
                                    .testTag("btn_$char")
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = char,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = fg
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. LOCALHUB ANA EKRAN (HUB SCREEN)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HubScreen(
    viewModel: AppViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToProcessing: () -> Unit
) {
    val context = LocalContext.current
    val videos by viewModel.videosList.collectAsStateWithLifecycle()
    val tags by viewModel.allTags.collectAsStateWithLifecycle()
    val selectedFilterTagId by viewModel.selectedFilterTagId.collectAsStateWithLifecycle()
    val activeSortType by viewModel.activeSortType.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Multi-select state
    val isInSelectionMode by viewModel.isInSelectionMode.collectAsStateWithLifecycle()
    val selectedVideoIds by viewModel.selectedVideoIds.collectAsStateWithLifecycle()

    // Logs for warning failed entries
    val failedAttemptsLog by viewModel.failedAttempts.collectAsStateWithLifecycle()

    var showSortBottomSheet by remember { mutableStateOf(false) }
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    var activeSettingsVideoId by remember { mutableStateOf<String?>(null) }
    var activePlayingVideoId by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Picker for adding new videos
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.preparePickedVideo(context, uri)
            onNavigateToProcessing()
        }
    }

    // Load trigger on first open
    LaunchedEffect(Unit) {
        viewModel.onHubOpened()

        // Check failed passcode tries in last 24h
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val recentFails = failedAttemptsLog.filter { it.timestamp > oneDayAgo }
        if (recentFails.isNotEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Son 24 saatte ${recentFails.size} hatalı giriş denemesi tespit edildi!",
                    actionLabel = "Temizle",
                    duration = SnackbarDuration.Long
                ).let { result ->
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.clearFailedAttemptsLog()
                    }
                }
            }
        }
    }

    // Intercept back key to exit multi-select mode if active
    BackHandler(enabled = isInSelectionMode || activePlayingVideoId != null) {
        if (activePlayingVideoId != null) {
            activePlayingVideoId = null
        } else {
            viewModel.exitSelectionMode()
        }
    }

    // Render Fullscreen Video Player if a video is selected for playback
    if (activePlayingVideoId != null) {
        FullscreenPlayerWrapper(
            videoId = activePlayingVideoId!!,
            viewModel = viewModel,
            onClose = { activePlayingVideoId = null }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isInSelectionMode) {
                // Top bar for multi selection
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
                    title = {
                        Text(
                            text = "${selectedVideoIds.size} video seçildi",
                            color = PrimaryOrange,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            // Confirm delete dialogue
                            viewModel.deleteSelectedVideos()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = PrimaryOrange)
                        }
                    }
                )
            } else {
                // Regular Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Black)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showSortBottomSheet = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Sort options", tint = TextPrimary)
                    }
                    IconButton(onClick = { showFilterBottomSheet = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filter options",
                            tint = if (selectedFilterTagId != null) PrimaryOrange else TextPrimary
                        )
                    }
                    
                    // Search text field
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Video veya etiket ara...", color = TextSecondary, fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .padding(horizontal = 4.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CardBackground,
                            unfocusedContainerColor = CardBackground,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryOrange,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon", tint = TextSecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = TextSecondary)
                                }
                            }
                        }
                    )

                    IconButton(onClick = { onNavigateToSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isInSelectionMode) {
                FloatingActionButton(
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    containerColor = PrimaryOrange,
                    contentColor = TextPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add video")
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (videos.isEmpty()) {
                    // Empty list state
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "No videos",
                            tint = TextSecondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedFilterTagId != null) "Arama kriterlerine uygun video bulunamadı." else "Henüz hiç video eklenmemiş.",
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    // LazyColumn displaying 1 video per row, styled with M3 cards
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(videos, key = { it.video.id }) { item ->
                            val isSelected = selectedVideoIds.contains(item.video.id)
                            val isVisible = remember(listState) {
                                derivedStateOf {
                                    listState.layoutInfo.visibleItemsInfo.any { it.key == item.video.id }
                                }
                            }.value
                            VideoCard(
                                item = item,
                                isSelected = isSelected,
                                isInSelectionMode = isInSelectionMode,
                                viewModel = viewModel,
                                isVisible = isVisible,
                                onClick = {
                                    if (isInSelectionMode) {
                                        viewModel.toggleVideoSelection(item.video.id)
                                    } else {
                                        activePlayingVideoId = item.video.id
                                    }
                                },
                                onOptionsClick = {
                                    activeSettingsVideoId = item.video.id
                                }
                            )
                        }
                    }
                }

                // Bottom total video count label
                Text(
                    text = "${videos.size} video",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }

    // --- Sort Options BottomSheet ---
    if (showSortBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortBottomSheet = false },
            containerColor = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Sıralama Seçenekleri",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryOrange,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                val options = listOf(
                    SortType.RANDOM to "Rastgele (Her açılışta)",
                    SortType.NEWEST to "En son eklenen",
                    SortType.LAST_WATCHED to "En son izlenen",
                    SortType.NAME_AZ to "İsme göre (A-Z)"
                )

                options.forEach { (type, title) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSortType(type)
                                showSortBottomSheet = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = activeSortType == type,
                            onClick = {
                                viewModel.setSortType(type)
                                showSortBottomSheet = false
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryOrange)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = title, color = TextPrimary)
                    }
                }

                Divider(
                    color = Color.DarkGray,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.startSelectionMode()
                            showSortBottomSheet = false
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Videoları Seç",
                        tint = PrimaryOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Videoları Seç",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // --- Filter Tags BottomSheet ---
    if (showFilterBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterBottomSheet = false },
            containerColor = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Etiket Filtresi",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryOrange
                    )
                    if (selectedFilterTagId != null) {
                        TextButton(onClick = { viewModel.setFilterTag(null) }) {
                            Text("Filtreyi Temizle", color = PrimaryOrange)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                if (tags.isEmpty()) {
                    Text("Kayıtlı etiket bulunmamaktadır.", color = TextSecondary, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(100.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tags) { tag ->
                            val isSelected = selectedFilterTagId == tag.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setFilterTag(if (isSelected) null else tag.id)
                                    showFilterBottomSheet = false
                                },
                                label = { Text(tag.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryOrange,
                                    selectedLabelColor = TextPrimary,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Video Settings Modifications Dialog & Bottom Sheet ---
    if (activeSettingsVideoId != null) {
        VideoSettingsBottomSheetWrapper(
            videoId = activeSettingsVideoId!!,
            viewModel = viewModel,
            onDismiss = { activeSettingsVideoId = null }
        )
    }
}

// --- Individual Video Grid Item Card ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    item: VideoWithTagsAndAssets,
    isSelected: Boolean,
    isInSelectionMode: Boolean,
    viewModel: AppViewModel,
    isVisible: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    // Select one random thumbnail index stored in a remember block for visual randomness
    val thumbnailPath = remember(item.thumbnails) {
        item.thumbnails.randomOrNull()?.encryptedPath ?: ""
    }
    val bmp = rememberEncryptedImage(thumbnailPath)
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    var isHolding by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) PrimaryOrange else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (isInSelectionMode) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                } else {
                    Modifier
                        .indication(interactionSource, LocalIndication.current)
                        .pointerInput(onClick) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    val downTime = System.currentTimeMillis()
                                    
                                    val press = PressInteraction.Press(down.position)
                                    scope.launch {
                                        interactionSource.emit(press)
                                    }
                                    
                                    var isHoldTriggered = false
                                    
                                    val holdJob = scope.launch {
                                        delay(400) // 400ms preview delay
                                        isHolding = true
                                        isHoldTriggered = true
                                    }
                                    
                                    var upOrCancel = false
                                    while (!upOrCancel) {
                                        val event = awaitPointerEvent()
                                        val anyDown = event.changes.any { it.pressed }
                                        val consumed = event.changes.any { it.isConsumed }
                                        if (consumed) {
                                            upOrCancel = true
                                            scope.launch {
                                                interactionSource.emit(PressInteraction.Cancel(press))
                                            }
                                        } else if (!anyDown) {
                                            upOrCancel = true
                                            scope.launch {
                                                interactionSource.emit(PressInteraction.Release(press))
                                            }
                                            val elapsed = System.currentTimeMillis() - downTime
                                            if (elapsed < 400 && !isHoldTriggered) {
                                                onClick()
                                            }
                                        }
                                    }
                                    
                                    holdJob.cancel()
                                    isHolding = false
                                }
                            }
                        }
                }
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.7777f) // 16:9 aspect ratio
            ) {
                if (isHolding && isVisible) {
                    val previewPath = remember(item) {
                        item.previews.randomOrNull()?.encryptedPath ?: item.video.encryptedVideoPath
                    }
                    if (previewPath.isNotEmpty()) {
                        InlineSilentPlayer(encryptedFilePath = previewPath, viewModel = viewModel)
                    }
                } else {
                    // Render static thumbnail image
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = item.video.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = "Loading thumb", tint = TextSecondary)
                        }
                    }
                }

                // Options action button (3-dots) on top right
                if (!isInSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    ) {
                        IconButton(
                            onClick = onOptionsClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Video options",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    // Checkbox on top-left in selection mode
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(4.dp))
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryOrange,
                                uncheckedColor = TextSecondary,
                                checkmarkColor = Black
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Video Duration Overlay (translucent black box, bottom-right)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(DurationOverlay, shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(item.video.duration),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Video Details below the image card area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = item.video.title,
                    color = PrimaryOrange,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (item.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item.tags.forEach { tag ->
                            Text(
                                text = "#${tag.name}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- High Performance Inline Silent looping player for thumbnails ---
class PlayerHolder(val context: Context) {
    var player: ExoPlayer? = null
    var isReleased = false

    fun setAndPlay(decrypted: File, onReady: (ExoPlayer) -> Unit) {
        if (isReleased) return
        val p = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(decrypted)))
            volume = 0f // fully silent
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
        player = p
        onReady(p)
    }

    fun release() {
        isReleased = true
        player?.release()
        player = null
    }
}

@Composable
fun InlineSilentPlayer(encryptedFilePath: String, viewModel: AppViewModel) {
    var tempFile by remember { mutableStateOf<File?>(null) }
    var isReady by remember { mutableStateOf(false) }
    val playerRef = remember { java.util.concurrent.atomic.AtomicReference<android.media.MediaPlayer?>(null) }

    DisposableEffect(encryptedFilePath) {
        val encFile = File(encryptedFilePath)
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            if (!encFile.exists()) return@launch
            try {
                val decrypted = viewModel.decryptPreviewToTempFileBlocking(encFile)
                withContext(Dispatchers.Main) {
                    tempFile = decrypted
                    isReady = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onDispose {
            job.cancel()
            try {
                val mp = playerRef.getAndSet(null)
                mp?.stop()
                mp?.reset()
                mp?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                tempFile?.delete()
                tempFile = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (isReady && tempFile != null) {
        AndroidView(
            factory = { ctx ->
                android.view.SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    holder.addCallback(object : android.view.SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                            try {
                                val mp = android.media.MediaPlayer().apply {
                                    setDataSource(tempFile!!.absolutePath)
                                    setSurface(holder.surface)
                                    isLooping = true
                                    setVolume(0f, 0f)
                                    setOnPreparedListener { 
                                        it.start()
                                    }
                                    prepareAsync()
                                }
                                playerRef.set(mp)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                            try {
                                val mp = playerRef.getAndSet(null)
                                mp?.stop()
                                mp?.reset()
                                mp?.release()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    })
                }
            },
            update = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

// Helper formatting duration string MM:SS or H:MM:SS
fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = durationMs / (1000 * 60 * 60)

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

// ==========================================
// 3. VİDEO EKLEME VE İŞLEME (PROCESSING SCREEN)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoProcessingScreen(
    viewModel: AppViewModel,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val progress by viewModel.importProgress.collectAsStateWithLifecycle()
    val statusText by viewModel.importStatus.collectAsStateWithLifecycle()
    val tags by viewModel.allTags.collectAsStateWithLifecycle()
    val pickedVideoUri by viewModel.pickedVideoUri.collectAsStateWithLifecycle()
    val isPreparingVideo by viewModel.isPreparingVideo.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var selectedTagsList = remember { mutableStateListOf<TagEntity>() }

    // Read picked Uri from ViewModel or trigger once
    var startProcessed by remember { mutableStateOf(false) }

    LaunchedEffect(pickedVideoUri, isPreparingVideo) {
        if (pickedVideoUri == null && !startProcessed && !isPreparingVideo) {
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video İşleme ve Ekleme") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.setPickedVideoUri(null)
                        onComplete()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri Git",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Video Başlığı (Zorunlu)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryOrange,
                    unfocusedBorderColor = TextSecondary,
                    focusedLabelColor = PrimaryOrange,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Dynamic tags select
            Text(
                text = "Etiket Seçin (İsteğe Bağlı)",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )

            if (tags.isEmpty()) {
                Text(
                    text = "Etiket listeniz boş. Ayarlar altından yeni etiketler ekleyebilirsiniz.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tags) { tag ->
                        val isSelected = selectedTagsList.contains(tag)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedTagsList.remove(tag) else selectedTagsList.add(tag)
                            },
                            label = { Text(tag.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryOrange,
                                selectedLabelColor = TextPrimary,
                                labelColor = TextPrimary
                            )
                        )
                    }
                }
            }

            // --- Kapak Fotoğrafları Yönetimi ---
            val tempThumbnails by viewModel.tempThumbnails.collectAsStateWithLifecycle()
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Kapak Fotoğrafları (${tempThumbnails.size})",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tempThumbnails, key = { it.id }) { thumb ->
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 75.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                        ) {
                            val bmp = rememberEncryptedImage(thumb.encryptedFilePath)
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.DarkGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryOrange)
                                }
                            }
                            
                            // Delete button
                            IconButton(
                                onClick = { viewModel.deleteTempThumbnail(thumb.id) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Sil",
                                    tint = RedFailed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            // Timestamp overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${thumb.timeMs / 1000}s",
                                    color = TextPrimary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                
                // Add custom thumbnail UI
                var customTimeStr by remember { mutableStateOf("") }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customTimeStr,
                        onValueChange = { customTimeStr = it },
                        label = { Text("Zaman Noktası (Saniye)") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryOrange,
                            unfocusedBorderColor = TextSecondary,
                            focusedLabelColor = PrimaryOrange,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    
                    Button(
                        onClick = {
                            val secs = customTimeStr.toFloatOrNull()
                            if (secs != null && secs >= 0f) {
                                viewModel.addTempThumbnailAtTime(context, secs)
                                customTimeStr = ""
                            } else {
                                android.widget.Toast.makeText(context, "Lütfen geçerli bir saniye girin", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ekle", tint = TextPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kapak Ekle", color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isPreparingVideo) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = PrimaryOrange,
                        trackColor = Color.DarkGray
                    )
                    Text(text = "Seçilen video hazırlanıyor, lütfen bekleyin...", color = TextPrimary, fontSize = 14.sp)
                }
            } else if (isImporting) {
                // Large ProgressBar showing 0-100 progress
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = PrimaryOrange,
                        trackColor = Color.DarkGray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = statusText, color = TextPrimary, fontSize = 14.sp)
                        Text(text = "${(progress * 100).toInt()}%", color = PrimaryOrange, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // If not currently importing, check if we can run
                Button(
                    onClick = {
                        val videoUri = pickedVideoUri
                        if (videoUri != null) {
                            viewModel.importVideo(videoUri, title, selectedTagsList.toList())
                            startProcessed = true
                        }
                    },
                    enabled = title.isNotBlank() && pickedVideoUri != null,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp)
                ) {
                    Text("Şifrele ve Güvenli Klasöre Aktar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }

            // Monitor when importing finished to navigate back
            if (startProcessed && !isImporting && progress >= 1.0f) {
                LaunchedEffect(Unit) {
                    viewModel.setPickedVideoUri(null)
                    onComplete()
                }
            }
        }
    }
}

// ==========================================
// 4. VİDEO AYARLARI (VIDEO SETTINGS SHEET)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsBottomSheetWrapper(
    videoId: String,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val details by viewModel.videoRepository.getVideoDetailsFlowById(videoId).collectAsState(initial = null)
    var showEditTitleDialog by remember { mutableStateOf(false) }
    var showEditTagsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (details == null) return

    if (!showEditTitleDialog && !showEditTagsDialog && !showDeleteConfirmDialog) {
        VideoSettingsBottomSheet(
            videoTitle = details!!.video.title,
            encryptedVideoPath = details!!.video.encryptedVideoPath,
            cryptoManager = com.example.data.crypto.AES256CryptoManager,
            onDismiss = onDismiss,
            onTitleEdit = { showEditTitleDialog = true },
            onTagsEdit = { showEditTagsDialog = true },
            onThumbnailRenew = { 
                viewModel.refreshVideoThumbnails(videoId)
                onDismiss()
            },
            onPreviewRenew = { 
                viewModel.refreshVideoPreviews(videoId)
                onDismiss()
            },
            onDelete = { showDeleteConfirmDialog = true },
            onRestore = { onSuccess, onFailure ->
                viewModel.restoreVideoToGallery(context, videoId, onSuccess, onFailure)
            }
        )
    }

    // Edit Title Dialog
    if (showEditTitleDialog) {
        var newTitle by remember { mutableStateOf(details!!.video.title) }
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false; onDismiss() },
            title = { Text("Başlığı Düzenle") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateVideoTitle(videoId, newTitle)
                        showEditTitleDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("Kaydet", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false; onDismiss() }) {
                    Text("İptal", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Edit Tags Dialog
    if (showEditTagsDialog) {
        val allTagsList by viewModel.settingsRepository.allTags.collectAsState(initial = emptyList())
        val updatedTags = remember { mutableStateListOf<com.example.data.db.TagEntity>() }
        
        LaunchedEffect(details) {
            updatedTags.clear()
            updatedTags.addAll(details!!.tags)
        }

        AlertDialog(
            onDismissRequest = { showEditTagsDialog = false; onDismiss() },
            title = { Text("Etiketleri Düzenle") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    if (allTagsList.isEmpty()) {
                        Text("Henüz etiket oluşturulmadı.", color = TextSecondary)
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allTagsList.forEach { tag ->
                                val selected = updatedTags.contains(tag)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (selected) updatedTags.remove(tag) else updatedTags.add(tag)
                                    },
                                    label = { Text(tag.name) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateVideoTags(videoId, updatedTags.toList())
                        showEditTagsDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("Tamam", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTagsDialog = false; onDismiss() }) {
                    Text("Vazgeç", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Delete Video Confirm Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false; onDismiss() },
            title = { Text("Videoyu Sil?") },
            text = { Text("Bu video ve ilişkili tüm gizli veriler (kapak resimleri, önizlemeler) kalıcı olarak silinecektir. Bu işlem geri alınamaz!") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSingleVideo(videoId)
                        showDeleteConfirmDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedFailed)
                ) {
                    Text("Kalıcı Olarak Sil", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false; onDismiss() }) {
                    Text("İptal", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

// ==========================================
// 5. GÖSTERİŞLİ VİDEO OYNATICI (PLAYER SCREEN)
// ==========================================
@Composable
fun FullscreenPlayerWrapper(
    videoId: String,
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    val details by viewModel.videoRepository.getVideoDetailsFlowById(videoId).collectAsState(initial = null)
    
    if (details != null) {
        FullscreenPlayer(
            encryptedVideoPath = details!!.video.encryptedVideoPath,
            videoTitle = details!!.video.title,
            cryptoManager = com.example.data.crypto.AES256CryptoManager,
            onClose = onClose
        )
    }
}

// ==========================================
// 6. GENEL AYARLAR (SETTINGS SCREEN)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val tags by viewModel.allTags.collectAsStateWithLifecycle()
    val backgrounds by viewModel.allBackgrounds.collectAsStateWithLifecycle()
    val isRandomBgEnabled by viewModel.isRandomBackgroundEnabled.collectAsStateWithLifecycle()
    val selectedBgId by viewModel.selectedBackgroundId.collectAsStateWithLifecycle()

    var showAddTagDialog by remember { mutableStateOf(false) }
    var storageStats by remember { mutableStateOf<StorageStats?>(null) }

    // Picker for custom backgrounds
    val backgroundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addBackgroundImage(uri)
        }
    }

    LaunchedEffect(Unit) {
        storageStats = viewModel.getStorageStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uygulama Ayarları", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Tags Manager
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Etiket Havuzu", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
                        IconButton(onClick = { showAddTagDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add tag", tint = PrimaryOrange)
                        }
                    }

                    if (tags.isEmpty()) {
                        Text("Henüz etiket tanımlanmamış.", color = TextSecondary, fontSize = 14.sp)
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                var showDeletePrompt by remember { mutableStateOf(false) }
                                InputChip(
                                    selected = false,
                                    onClick = { showDeletePrompt = true },
                                    label = { Text(tag.name) },
                                    trailingIcon = { Icon(Icons.Default.Cancel, contentDescription = "Delete tag", modifier = Modifier.size(16.dp)) },
                                    colors = InputChipDefaults.inputChipColors(
                                        labelColor = TextPrimary,
                                        trailingIconColor = TextSecondary
                                    )
                                )

                                if (showDeletePrompt) {
                                    AlertDialog(
                                        onDismissRequest = { showDeletePrompt = false },
                                        title = { Text("Etiketi Sil?") },
                                        text = { Text("\"${tag.name}\" etiketi tüm videolardan kaldırılacaktır. Onaylıyor musunuz?") },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    viewModel.deleteTag(tag.id)
                                                    showDeletePrompt = false
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = RedFailed)
                                            ) {
                                                Text("Sil", color = TextPrimary)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeletePrompt = false }) {
                                                Text("Vazgeç", color = TextSecondary)
                                            }
                                        },
                                        containerColor = DarkSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Vault Theme Backgrounds Settings
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Özel Arka Plan Görselleri", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rastgele Arka Plan Modu", color = TextPrimary)
                        Switch(
                            checked = isRandomBgEnabled,
                            onCheckedChange = { viewModel.toggleRandomBackground(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryOrange)
                        )
                    }

                    Button(
                        onClick = {
                            backgroundPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Arka Plan Fotoğrafı Ekle", color = TextPrimary)
                    }

                    if (backgrounds.isEmpty()) {
                        Text("Kayıtlı arka plan görseli bulunmuyor.", color = TextSecondary, fontSize = 14.sp)
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                        ) {
                            items(backgrounds) { bg ->
                                val active = selectedBgId == bg.id
                                val bmp = rememberEncryptedImage(bg.encryptedPath)

                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (active) 3.dp else 0.dp,
                                            color = if (active) PrimaryOrange else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.selectActiveBackground(bg.id)
                                        }
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = "Background option",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
                                    }

                                    // Delete background button
                                    IconButton(
                                        onClick = { viewModel.deleteBackgroundImage(bg.id, bg.encryptedPath) },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .align(Alignment.TopEnd)
                                            .background(Color.Black.copy(0.6f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete image", tint = RedFailed, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Storage Analysis
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Depolama Bilgisi", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)

                    storageStats?.let { stats ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Toplam Güvenli Disk Alanı: ${formatBytes(stats.totalBytes)}",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Divider(color = Color.DarkGray)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Şifreli Video Dosyası:", color = TextSecondary)
                                    Text("${stats.videoCount} adet", color = TextPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Güvenli Kapak Fotoğrafı:", color = TextSecondary)
                                    Text("${stats.thumbnailCount} adet", color = TextPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Önizleme Klip Segmenti:", color = TextSecondary)
                                    Text("${stats.previewCount} adet", color = TextPrimary)
                                }
                            }
                        }
                    } ?: CircularProgressIndicator(color = PrimaryOrange)
                }
            }

            // Section 4: Version Info
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sürüm: v0.1",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    // Add Tag Input Prompt Dialog
    if (showAddTagDialog) {
        var tagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Yeni Etiket Ekle") },
            text = {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text("Etiket Adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tagName.isNotBlank()) {
                            viewModel.addTag(tagName.trim())
                            showAddTagDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("Ekle", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("İptal", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

// Formatting size units
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// FlowRow layout implementation for tags
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val layoutWidth = constraints.maxWidth
        
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        var totalHeight = 0
        val spaceX = 8.dp.roundToPx()
        val spaceY = 8.dp.roundToPx()

        placeables.forEach { placeable ->
            if (currentRowWidth + placeable.width > layoutWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + spaceX
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        totalHeight = rowHeights.sum() + (rows.size - 1).coerceAtLeast(0) * spaceY

        layout(layoutWidth, totalHeight) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                var x = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + spaceX
                }
                y += rowHeights[rowIndex] + spaceY
            }
        }
    }
}

// ==========================================
// USER'S NEW COMPONENTS BELOW
// ==========================================

@Composable
fun FullscreenPlayer(
    encryptedVideoPath: String,
    videoTitle: String,
    cryptoManager: com.example.data.crypto.AES256CryptoManager,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(encryptedVideoPath) {
        try {
            isLoading = true

            val player = withContext(Dispatchers.Main) {
                ExoPlayer.Builder(context).build()
            }

            val dataSourceFactory = EncryptedFileDataSourceFactory()
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(
                    MediaItem.fromUri(Uri.fromFile(File(encryptedVideoPath)))
                )

            withContext(Dispatchers.Main) {
                player.setMediaSource(mediaSource)
                player.prepare()
                player.playWhenReady = true

                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("Player", "Hata: ${error.message}", error)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Oynatma hatası: ${error.message}"
                            )
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        super.onVideoSizeChanged(videoSize)
                        var w = videoSize.width
                        var h = videoSize.height
                        if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
                            val temp = w
                            w = h
                            h = temp
                        }
                        if (w > 0 && h > 0) {
                            val activity = context as? Activity
                            activity?.requestedOrientation = if (w > h) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            isLoading = false
                        }
                    }
                })

                exoPlayer = player
            }

        } catch (e: Exception) {
            Log.e("Player", "Başlatma hatası: ${e.message}", e)
            isLoading = false
            scope.launch {
                snackbarHostState.showSnackbar("Video açılamadı: ${e.message}")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
            exoPlayer = null
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
        ) {
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        useController = true
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        controllerShowTimeoutMs = 3000
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFFF6D00))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Video yükleniyor...", color = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color.White
                    )
                }
                Text(
                    text = videoTitle,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsBottomSheet(
    videoTitle: String,
    encryptedVideoPath: String,
    cryptoManager: com.example.data.crypto.AES256CryptoManager,
    onDismiss: () -> Unit,
    onTitleEdit: () -> Unit,
    onTagsEdit: () -> Unit,
    onThumbnailRenew: () -> Unit,
    onPreviewRenew: () -> Unit,
    onDelete: () -> Unit,
    onRestore: (onSuccess: () -> Unit, onFailure: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRestoring by remember { mutableStateOf(false) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = videoTitle,
                color = Color(0xFFFF6D00),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            restoreMessage?.let { msg ->
                Text(
                    text = msg,
                    color = if (msg.startsWith("✓")) Color.Green else Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            SheetItem(Icons.Default.Edit, "Başlığı Düzenle") {
                onTitleEdit()
            }
            SheetItem(Icons.Default.Label, "Etiketleri Düzenle") {
                onTagsEdit()
            }
            SheetItem(Icons.Default.Refresh, "Kapak Fotoğraflarını Yenile") {
                onThumbnailRenew()
            }
            SheetItem(Icons.Default.PlayCircle, "Önizleme Kliplerini Yenile") {
                onPreviewRenew()
            }

            SheetItem(
                icon = if (isRestoring) Icons.Default.HourglassEmpty
                       else Icons.Default.Download,
                text = if (isRestoring) "Geri yükleniyor..."
                       else "Galeriye Geri Yükle",
                enabled = !isRestoring
            ) {
                if (!isRestoring) {
                    isRestoring = true
                    restoreMessage = null
                    onRestore(
                        {
                            restoreMessage = "✓ Video galeriye geri yüklendi"
                            scope.launch {
                                delay(1500)
                                onDismiss() // AppViewModel zaten siliyor, onDelete çağırma
                            }
                        },
                        { errorMsg ->
                            restoreMessage = "✗ Başarısız: $errorMsg"
                            isRestoring = false
                        }
                    )
                }
            }

            HorizontalDivider(
                color = Color(0xFF333333),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SheetItem(Icons.Default.Delete, "Videoyu Güvenli Olarak Sil",
                textColor = Color.Red) {
                onDelete()
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SheetItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    enabled: Boolean = true,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) textColor else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = if (enabled) textColor else Color.Gray,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
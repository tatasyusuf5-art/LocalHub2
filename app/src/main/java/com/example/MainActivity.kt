package com.example
import androidx.compose.foundation.layout.ExperimentalLayoutApi


import androidx.compose.ui.platform.LocalConfiguration
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.PlaybackException
import android.util.Log
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import java.io.FileInputStream
import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.ui.draw.alpha
import android.app.Application
import android.content.Context
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
import com.example.data.db.BackgroundImageEntity
import com.example.data.db.TagEntity
import com.example.data.db.VideoWithTagsAndAssets
import com.example.data.util.SecureStorageHelper
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.CalcState
import com.example.ui.viewmodel.SortType
import com.example.ui.viewmodel.StorageStats
import com.example.ui.AddUserDialog
import com.example.ui.RankingScreen
import com.example.ui.UserProfileScreen
import com.example.ui.UserPickerDialog
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

        // GİZLİLİK: Görev değiştiricide (recent apps) içerik siyah görünür,
        // ekran görüntüsü/kaydı engellenir
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

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

    // OTOMATIK KİLİT: Uygulama arka plana alınınca hesap makinesine (kilit) dön
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentRoute) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // Kilit ekranında değilsek, hesap makinesine geri dön
                if (currentRoute != ROUTE_CALCULATOR) {
                    navController.navigate(ROUTE_CALCULATOR) {
                        popUpTo(ROUTE_CALCULATOR) { inclusive = true }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    .background(Color.Black.copy(alpha = 0.45f))
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
                })
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(viewModel = viewModel, onBack = {
                    navController.popBackStack()
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
                    val rawBytes = file.readBytes()
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
            .background(CardBackground)
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
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current

    // Permission launcher for MANAGE_EXTERNAL_STORAGE
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkInboxFolder()
    }
    
    // Check permission on resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (android.os.Environment.isExternalStorageManager()) {
                        viewModel.checkInboxFolder()
                    }
                } else {
                    viewModel.checkInboxFolder()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Permission dialog
    var showPermissionDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                showPermissionDialog = true
            }
        }
    }
    
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Depolama İzni Gerekli") },
            text = { Text("Videoları otomatik algılamak için 'Tüm dosyalara erişim' iznine ihtiyaç var.") },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:" + context.packageName)
                            manageStorageLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageStorageLauncher.launch(intent)
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)) {
                    Text("İzin Ver", color = TextPrimary)
                }
            },
            containerColor = DarkSurface
        )
    }

    val videos by viewModel.videosList.collectAsStateWithLifecycle()
    val tags by viewModel.allTags.collectAsStateWithLifecycle()
    val selectedFilterTagId by viewModel.selectedFilterTagId.collectAsStateWithLifecycle()
    val activeSortType by viewModel.activeSortType.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val allUsersForSearch by viewModel.allUsers.collectAsStateWithLifecycle()

    // Multi-select state
    val isInSelectionMode by viewModel.isInSelectionMode.collectAsStateWithLifecycle()
    val selectedVideoIds by viewModel.selectedVideoIds.collectAsStateWithLifecycle()

    // Logs for warning failed entries
    val failedAttemptsLog by viewModel.failedAttempts.collectAsStateWithLifecycle()

    var showSortBottomSheet by remember { mutableStateOf(false) }
    var showFilterBottomSheet by remember { mutableStateOf(false) }

    // Import Progress Overlay
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    
    if (isImporting) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { /* Cannot dismiss */ }, properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { importProgress },
                        modifier = Modifier.size(64.dp),
                        color = PrimaryOrange,
                        trackColor = Color.DarkGray
                    )
                    Text(
                        text = importStatus,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    var activeSettingsVideoId by remember { mutableStateOf<String?>(null) }
    var activePlayingVideoId by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Picker for adding new videos
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setPickedVideoUri(context, uri)
        }
    }

    // === KULLANICI SİSTEMİ: profil ve sıralama ekranları ===
    val showRanking by viewModel.showRankingTable.collectAsStateWithLifecycle()
    val activeProfileUserId by viewModel.activeProfileUserId.collectAsStateWithLifecycle()

    if (activeProfileUserId != null) {
        // Telefon geri tuşu: önce ayarlar açıksa kapat, değilse profili kapat
        BackHandler(enabled = true) {
            if (activeSettingsVideoId != null) {
                activeSettingsVideoId = null
            } else {
                viewModel.closeUserProfile()
            }
        }
        UserProfileScreen(
            userId = activeProfileUserId!!,
            viewModel = viewModel,
            onVideoClick = { videoId ->
                viewModel.closeUserProfile()
                activePlayingVideoId = videoId
            },
            onVideoOptions = { videoId ->
                activeSettingsVideoId = videoId
            },
            onBack = { viewModel.closeUserProfile() }
        )

        // Profildeyken de video ayarları menüsü açılabilsin
        if (activeSettingsVideoId != null) {
            VideoSettingsBottomSheetWrapper(
                videoId = activeSettingsVideoId!!,
                viewModel = viewModel,
                onDismiss = { activeSettingsVideoId = null }
            )
        }
        return
    }

    if (showRanking) {
        BackHandler(enabled = true) { viewModel.closeRankingTable() }
        RankingScreen(
            viewModel = viewModel,
            onUserClick = { userId ->
                viewModel.closeRankingTable()
                viewModel.openUserProfile(userId)
            },
            onBack = { viewModel.closeRankingTable() }
        )
        return
    }

    val pickedVideoUri by viewModel.pickedVideoUri.collectAsStateWithLifecycle()
    if (pickedVideoUri != null) {
        com.example.ui.components.VideoImportDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.setPickedVideoUri(context, null) }
        )
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



    val activePreviewId by viewModel.activePreviewId.collectAsStateWithLifecycle()
    val activePreviewRect by viewModel.activePreviewRect.collectAsStateWithLifecycle()

    
    // Inbox auto-detect logic
    val inboxVideos by viewModel.inboxVideos.collectAsStateWithLifecycle()
    var showInboxImportScreen by remember { mutableStateOf(false) }

    if (inboxVideos.isNotEmpty() && !showInboxImportScreen) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissInboxDialog() },
            title = { Text("Yeni Videolar Bulundu") },
            text = { Text("${inboxVideos.size} adet yeni video bulundu, eklemek ister misiniz?") },
            confirmButton = {
                Button(
                    onClick = { showInboxImportScreen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("Evet, Ekle", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissInboxDialog() }) {
                    Text("Hayır", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    if (showInboxImportScreen) {
        InboxImportScreen(
            videos = inboxVideos,
            viewModel = viewModel,
            onClose = {
                showInboxImportScreen = false
                viewModel.dismissInboxDialog()
            }
        )
        return
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isInSelectionMode) {
                // Top bar for multi selection
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.45f)),
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
                            viewModel.deleteSelectedVideos(context)
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
                        .background(CardBackground)
                        .statusBarsPadding()
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
        floatingActionButton = { }
,
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
                // Arama aktifken eşleşen kullanıcıları göster
                if (searchQuery.isNotEmpty()) {
                    val matchedUsers = allUsersForSearch.filter {
                        it.name.contains(searchQuery, ignoreCase = true)
                    }
                    if (matchedUsers.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(matchedUsers) { user ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(CardBackground)
                                        .clickable { viewModel.openUserProfile(user.id) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Person, null, tint = PrimaryOrange, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(user.name, color = TextPrimary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

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
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
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

                // Bottom total video count label + Sıralama butonu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${videos.size} video",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                    Button(
                        onClick = { viewModel.openRankingTable() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Leaderboard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sıralama", fontSize = 13.sp)
                    }
                }
            }
        }

        // Global Overlay Player for Previews
        val isPreviewVisible = activePreviewId != null && activePreviewRect != null && activePreviewRect != androidx.compose.ui.geometry.Rect.Zero
        val rect = activePreviewRect ?: androidx.compose.ui.geometry.Rect.Zero
        val density = androidx.compose.ui.platform.LocalDensity.current
        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { rect.left.toDp() },
                    y = with(density) { rect.top.toDp() }
                )
                .size(
                    width = with(density) { rect.width.toDp() },
                    height = with(density) { rect.height.toDp() }
                )
                .alpha(if (isPreviewVisible) 1f else 0f)
        ) {
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        useController = false
                        player = viewModel.previewPlayer
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        isClickable = false
                        isFocusable = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (view.player != viewModel.previewPlayer) {
                        view.player = viewModel.previewPlayer
                    }
                }
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
    var thumbnailRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

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
                                        delay(300) // 300ms preview delay
                                        isHolding = true
                                        isHoldTriggered = true
                                        val previewPath = item.previews.randomOrNull()?.encryptedPath ?: item.video.encryptedVideoPath
                                        if (previewPath.isNotEmpty() && isVisible && thumbnailRect != androidx.compose.ui.geometry.Rect.Zero) {
                                            viewModel.startPreview(item.video.id, previewPath, thumbnailRect)
                                        }
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
                                    if (isHolding) {
                                        viewModel.stopPreview()
                                    }
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
                    .onGloballyPositioned { coordinates ->
                        thumbnailRect = coordinates.boundsInRoot()
                    }
            ) {
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

                // Kullanıcı adı (varsa) - tıklayınca profil açılır
                item.user?.let { user ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { viewModel.openUserProfile(user.id) }
                    ) {
                        // Küçük profil fotosu (yuvarlak)
                        val userPhoto = rememberEncryptedImage(user.profilePhotoPath)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(CardBackground)
                                .border(1.dp, PrimaryOrange, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (userPhoto != null) {
                                Image(
                                    bitmap = userPhoto,
                                    contentDescription = user.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = PrimaryOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = user.name,
                            color = PrimaryOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}


@Composable

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

// ==========================================
// FULLSCREEN PLAYER AND SETTINGS
// ==========================================

@Composable
fun FullscreenPlayerWrapper(
    videoId: String,
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    val videos by viewModel.videosList.collectAsStateWithLifecycle()
    val videoWithTags = remember(videos, videoId) { videos.find { it.video.id == videoId } }
    
    if (videoWithTags != null) {
        val context = LocalContext.current
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                val file = File(videoWithTags!!.video.encryptedVideoPath)
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
                playWhenReady = true
            }
        }
        
        DisposableEffect(exoPlayer) {
            onDispose {
                exoPlayer.release()
            }
        }
        
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoSettingsBottomSheetWrapper(
    videoId: String,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val videos by viewModel.videosList.collectAsStateWithLifecycle()
    val videoWithTags = remember(videos, videoId) { videos.find { it.video.id == videoId } }
    
    val context = LocalContext.current
    var showTagEditDialog by remember { mutableStateOf(false) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    if (videoWithTags != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = Color.DarkGray,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp)) {
                Text(text = "Seçenekler", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                
                restoreMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = if (msg.startsWith("✓")) Color.Green else Color.Red,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                ListItem(
                    headlineContent = { Text("Etiketleri Düzenle", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.Label, contentDescription = null, tint = Color.White) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        showTagEditDialog = true
                    }
                )
                
                ListItem(
                    headlineContent = { Text("Ön İzlemeleri Yenile", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        viewModel.refreshVideoPreviews(videoWithTags!!.video.id)
                        onDismiss()
                    }
                )
                
                ListItem(
                    headlineContent = { Text("Kapak Fotoğraflarını Yenile", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null, tint = Color.White) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        viewModel.refreshVideoThumbnails(videoWithTags!!.video.id)
                        onDismiss()
                    }
                )
                
                ListItem(
                    headlineContent = { Text("Galeriye Geri Yükle", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.Restore, contentDescription = null, tint = Color.White) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        viewModel.restoreVideoToGallery(
                            context = context,
                            videoId = videoWithTags!!.video.id,
                            onSuccess = {
                                restoreMessage = "✓ Video galeriye geri yüklendi"
                                scope.launch {
                                    kotlinx.coroutines.delay(1500)
                                    onDismiss()
                                }
                            },
                            onFailure = { err ->
                                restoreMessage = "✗ Başarısız: $err"
                            }
                        )
                    }
                )
                
                ListItem(
                    headlineContent = { Text("Videoyu Sil", color = Color(0xFFF44336)) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF44336)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        viewModel.deleteSingleVideo(videoWithTags!!.video.id)
                        onDismiss()
                    }
                )
            }
        }
        
        if (showTagEditDialog) {
            val allTags by viewModel.allTags.collectAsStateWithLifecycle()
            var selectedTags by remember { mutableStateOf(videoWithTags!!.tags.map { it.id }.toSet()) }
            
            AlertDialog(
                onDismissRequest = { showTagEditDialog = false },
                title = { Text("Etiketleri Düzenle") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (allTags.isEmpty()) {
                            Text("Henüz etiket oluşturulmamış.", color = Color.Gray)
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                allTags.forEach { tag ->
                                    val isSelected = selectedTags.contains(tag.id)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedTags = if (isSelected) {
                                                selectedTags - tag.id
                                            } else {
                                                selectedTags + tag.id
                                            }
                                        },
                                        label = { Text(tag.name) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryOrange,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newTagsList = allTags.filter { selectedTags.contains(it.id) }
                            viewModel.updateVideoTags(videoWithTags!!.video.id, newTagsList)
                            showTagEditDialog = false
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                    ) {
                        Text("Kaydet", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTagEditDialog = false }) {
                        Text("İptal", color = Color.Gray)
                    }
                },
                containerColor = Color.DarkGray,
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
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
    var showAddUserDialog by remember { mutableStateOf(false) }
    var storageStats: StorageStats? by remember { mutableStateOf(null) }

    // Kullanıcı ekle dialog
    if (showAddUserDialog) {
        AddUserDialog(
            viewModel = viewModel,
            onDismiss = { showAddUserDialog = false }
        )
    }

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.45f))
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
            // Section 0: Kullanıcı Yönetimi
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kullanıcılar", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryOrange)
                        IconButton(onClick = { showAddUserDialog = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Kullanıcı ekle", tint = PrimaryOrange)
                        }
                    }
                    Text("Kullanıcı ekleyerek videolara profil atayabilirsiniz.", color = TextSecondary, fontSize = 13.sp)
                }
            }

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
                                        onClick = { viewModel.deleteBackgroundImage(context, bg.id, bg.encryptedPath) },
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
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
    }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(encryptedVideoPath) {
        try {
            isLoading = true
            val encFile = File(encryptedVideoPath)
            if (encFile.exists()) {
                exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(encFile)))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                
                exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == androidx.media3.common.Player.STATE_READY) {
                            isLoading = false
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        isLoading = false
                        scope.launch {
                            snackbarHostState.showSnackbar("Video oynatma hatası: ${error.message}")
                        }
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        player = exoPlayer
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = true
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
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



// ==========================================
// INBOX IMPORT SCREEN
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxImportScreen(
    videos: List<java.io.File>,
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gelen Kutusu (${videos.size})") },
                navigationIcon = {
                    if (!isImporting) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "İptal", tint = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.45f))
            )
        },
        containerColor = Color.DarkGray
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isImporting) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        progress = { importProgress },
                        modifier = Modifier.size(64.dp),
                        color = PrimaryOrange,
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = importStatus, color = TextPrimary, fontSize = 16.sp, textAlign = TextAlign.Center)
                }
            } else {
                Text("Eklenecek videoyu seçin:", color = TextSecondary, fontSize = 14.sp)
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(videos) { file ->
                        var thumbBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                        
                        LaunchedEffect(file) {
                            withContext(Dispatchers.IO) {
                                try {
                                    val retriever = MediaMetadataRetriever()
                                    retriever.setDataSource(file.absolutePath)
                                    val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                    retriever.release()
                                    if (bitmap != null) {
                                        thumbBitmap = bitmap.asImageBitmap()
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            modifier = Modifier.fillMaxWidth().height(80.dp).clickable {
                                viewModel.setPickedVideoUri(context, android.net.Uri.fromFile(file))
                            }
                        ) {
                            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                if (thumbBitmap != null) {
                                    Image(
                                        bitmap = thumbBitmap!!,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(80.dp)
                                    )
                                } else {
                                    Box(modifier = Modifier.size(80.dp).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.VideoFile, contentDescription = null, tint = TextSecondary)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.padding(end = 16.dp)) {
                                    Text(
                                        text = file.name,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sizeInMb = file.length() / (1024f * 1024f)
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.2f MB", sizeInMb),
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
    }
}

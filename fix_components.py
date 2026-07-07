import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

components_code = """
// ==========================================
// FULLSCREEN PLAYER AND SETTINGS
// ==========================================

@Composable
fun FullscreenPlayerWrapper(
    videoId: String,
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    val videoFlow = remember(videoId) { viewModel.getVideoById(videoId) }
    val videoWithTags by videoFlow.collectAsStateWithLifecycle(initialValue = null)
    
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsBottomSheetWrapper(
    videoId: String,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val videoFlow = remember(videoId) { viewModel.getVideoById(videoId) }
    val videoWithTags by videoFlow.collectAsStateWithLifecycle(initialValue = null)
    
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
                
                ListItem(
                    headlineContent = { Text("Videoyu Sil", color = Color(0xFFF44336)) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF44336)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        viewModel.deleteVideoAndAssets(videoWithTags!!.video.id)
                        onDismiss()
                    }
                )
            }
        }
    }
}
"""

content = content.replace("// ==========================================\n// 6. GENEL AYARLAR", components_code + "\n\n// ==========================================\n// 6. GENEL AYARLAR")

# Fix BackgroundDark reference
content = content.replace("containerColor = BackgroundDark", "containerColor = Color.DarkGray")

# Fix missing StorageStats import (wait, it's defined in AppViewModel which is in another file, we already imported AppViewModel but StorageStats might need import)
# Let's add the import for StorageStats if needed
if "import com.example.ui.viewmodel.StorageStats" not in content:
    content = content.replace("import com.example.ui.viewmodel.AppViewModel", "import com.example.ui.viewmodel.AppViewModel\nimport com.example.ui.viewmodel.StorageStats")

# Fix generic type error on storageStats
content = content.replace("var storageStats by remember { mutableStateOf<StorageStats?>(null) }", "var storageStats: StorageStats? by remember { mutableStateOf(null) }")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

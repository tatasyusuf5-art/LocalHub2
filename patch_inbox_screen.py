import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

inbox_screen_code = """
// ==========================================
// INBOX IMPORT SCREEN
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxImportScreen(
    videos: List<File>,
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val tags by viewModel.allTags.collectAsStateWithLifecycle()
    var selectedTagsList = remember { mutableStateListOf<TagEntity>() }
    
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Videoları İçe Aktar (${videos.size})") },
                navigationIcon = {
                    if (!isImporting) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "İptal", tint = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.45f))
            )
        },
        containerColor = BackgroundDark,
        floatingActionButton = {
            if (!isImporting) {
                FloatingActionButton(
                    onClick = { viewModel.batchImportInboxVideos(context, videos, selectedTagsList.toList()) },
                    containerColor = PrimaryOrange,
                    contentColor = TextPrimary
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Kaydet")
                }
            }
        }
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
                Text("Etiket Seçin (Tüm videolara uygulanacak)", color = TextSecondary, fontSize = 14.sp)
                if (tags.isEmpty()) {
                    Text("Etiket listeniz boş.", color = TextSecondary, fontSize = 12.sp)
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
                                    selectedLabelColor = TextPrimary
                                )
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = Color.DarkGray)
                
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
                                    retriever.setDataSource(context, Uri.fromFile(file))
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
                            modifier = Modifier.fillMaxWidth().height(80.dp)
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
                                Text(
                                    text = file.name,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
"""

if "fun InboxImportScreen" not in content:
    content = content + "\n\n" + inbox_screen_code

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

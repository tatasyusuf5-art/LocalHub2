import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = r'''@OptIn\(ExperimentalMaterial3Api::class\)
@Composable
fun VideoSettingsBottomSheetWrapper\(.*?// ==========================================
// 6. GENEL AYARLAR'''

new_settings = """@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
// 6. GENEL AYARLAR"""

content = re.sub(target, new_settings, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

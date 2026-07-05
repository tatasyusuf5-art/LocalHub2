const fs = require('fs');
let content = fs.readFileSync('app/src/main/java/com/example/MainActivity.kt', 'utf8');

const startMarker1 = 'fun FullscreenPlayerWrapper(';
const endMarker1 = '// ==========================================\n// 6. GENEL AYARLAR';

let start1 = content.indexOf(startMarker1);
start1 = content.lastIndexOf('@SuppressLint', start1);
let end1 = content.indexOf(endMarker1);

const wrapper1 = `@Composable
fun FullscreenPlayerWrapper(
    videoId: String,
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    var details by remember { mutableStateOf<com.example.data.db.VideoWithTagsAndAssets?>(null) }
    LaunchedEffect(videoId) {
        details = viewModel.videoRepository.getVideoDetailsById(videoId)
    }
    if (details != null) {
        FullscreenPlayer(
            encryptedVideoPath = details!!.video.encryptedVideoPath,
            videoTitle = details!!.video.title,
            cryptoManager = com.example.data.crypto.AES256CryptoManager,
            onClose = onClose
        )
    }
}

`;

content = content.substring(0, start1) + wrapper1 + content.substring(end1);

const startMarker2 = 'fun VideoSettingsBottomSheetWrapper(';
const endMarker2 = '// ==========================================\n// 5. GÖSTERİŞLİ VİDEO OYNATICI';

let start2 = content.indexOf(startMarker2);
start2 = content.lastIndexOf('@OptIn', start2);
let end2 = content.indexOf(endMarker2);

const wrapper2 = `@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsBottomSheetWrapper(
    videoId: String,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var details by remember { mutableStateOf<com.example.data.db.VideoWithTagsAndAssets?>(null) }
    var showEditTitleDialog by remember { mutableStateOf(false) }
    var showEditTagsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(videoId) {
        details = viewModel.videoRepository.getVideoDetailsById(videoId)
    }

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
            },
            onPreviewRenew = { 
                viewModel.refreshVideoPreviews(videoId)
            },
            onDelete = { showDeleteConfirmDialog = true }
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
        val allTagsList by viewModel.settingsRepository.getAllTagsFlow().collectAsState(initial = emptyList())
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

`;

content = content.substring(0, start2) + wrapper2 + content.substring(end2);

fs.writeFileSync('app/src/main/java/com/example/MainActivity.kt', content);

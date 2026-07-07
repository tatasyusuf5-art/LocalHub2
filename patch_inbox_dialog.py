import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

inbox_dialog = """
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
"""

hub_match = re.search(r'// Render Fullscreen Video Player if a video is selected for playback', content)
if hub_match:
    content = content.replace(hub_match.group(0), inbox_dialog + "\n" + hub_match.group(0))

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

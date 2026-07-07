import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# 1. Update launcher to just call autoImportVideo
launcher_target = """    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.preparePickedVideo(context, uri)
            onNavigateToProcessing()
        }
    }"""
    
launcher_replacement = """    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.autoImportVideo(context, uri)
        }
    }"""
content = content.replace(launcher_target, launcher_replacement)

# 2. Add an overlay for importing in HubScreen
hub_scaffold_end_target = """        floatingActionButton = {"""
hub_scaffold_end_replacement = """        floatingActionButton = {"""

importing_overlay = """
    // Import Progress Overlay
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    
    if (isImporting) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { /* Cannot dismiss */ }, properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BackgroundDark)
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
"""

# Let's insert the overlay right before the Scaffold in HubScreen
hub_match = re.search(r'fun HubScreen\(.*?\).*?\{.*?var showFilterBottomSheet by remember \{ mutableStateOf\(false\) \}', content, re.DOTALL)
if hub_match:
    content = content.replace(hub_match.group(0), hub_match.group(0) + "\n" + importing_overlay)

# 3. Remove VideoProcessingScreen entirely from navigation and file
nav_target = """composable("processing") {
                    VideoProcessingScreen(viewModel = viewModel, onComplete = { navController.popBackStack() })
                }"""
content = content.replace(nav_target, "")

processing_screen_target = r'@OptIn\(.*?\)\s*@Composable\s*fun VideoProcessingScreen.*?// ==========================================\n// 3\. SETTINGS SCREEN'
content = re.sub(processing_screen_target, '// ==========================================\n// 3. SETTINGS SCREEN', content, flags=re.DOTALL)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

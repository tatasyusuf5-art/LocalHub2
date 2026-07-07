import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

permissions_logic = """
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
"""

# Insert permission logic at the start of HubScreen
hub_match = re.search(r'fun HubScreen\(.*?\).*?\{.*?val context = LocalContext\.current', content, re.DOTALL)
if hub_match:
    content = content.replace(hub_match.group(0), hub_match.group(0) + "\n" + permissions_logic)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Fix FullscreenPlayerWrapper
fs_target = """    val videoFlow = remember(videoId) { viewModel.getVideoById(videoId) }
    val videoWithTags by videoFlow.collectAsStateWithLifecycle(initialValue = null)"""
fs_replacement = """    val videos by viewModel.videosList.collectAsStateWithLifecycle()
    val videoWithTags = remember(videos, videoId) { videos.find { it.video.id == videoId } }"""

content = content.replace(fs_target, fs_replacement)

# Fix VideoSettingsBottomSheetWrapper
bs_target = """    val videoFlow = remember(videoId) { viewModel.getVideoById(videoId) }
    val videoWithTags by videoFlow.collectAsStateWithLifecycle(initialValue = null)"""
bs_replacement = """    val videos by viewModel.videosList.collectAsStateWithLifecycle()
    val videoWithTags = remember(videos, videoId) { videos.find { it.video.id == videoId } }"""

content = content.replace(bs_target, bs_replacement)

# Fix delete
content = content.replace("viewModel.deleteVideoAndAssets(", "viewModel.deleteSingleVideo(")

# Fix missing onNavigateToProcessing argument when calling HubScreen
hub_call_target = """                HubScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onNavigateToProcessing = { navController.navigate(ROUTE_PROCESSING) }
                )"""
hub_call_replacement = """                HubScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) }
                )"""

content = content.replace(hub_call_target, hub_call_replacement)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

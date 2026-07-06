import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = """    val pickedVideoUri by viewModel.pickedVideoUri.collectAsStateWithLifecycle()
    val isPreparingVideo by viewModel.isPreparingVideo.collectAsStateWithLifecycle()

    var title by remember(pickedVideoUri) { 
        mutableStateOf(
            pickedVideoUri?.let { uri ->"""

replacement = """    val pickedVideoUri by viewModel.pickedVideoUri.collectAsStateWithLifecycle()
    val originalPickedVideoUri by viewModel.originalPickedVideoUri.collectAsStateWithLifecycle()
    val isPreparingVideo by viewModel.isPreparingVideo.collectAsStateWithLifecycle()

    var title by remember(pickedVideoUri) { 
        mutableStateOf(
            originalPickedVideoUri?.let { uri ->"""

content = content.replace(target, replacement)
with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

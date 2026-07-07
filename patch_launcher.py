import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target1 = """    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->"""

replacement1 = """    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->"""

target2 = """onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }"""
replacement2 = """onClick = { galleryLauncher.launch("video/*") }"""

content = content.replace(target1, replacement1)
content = content.replace(target2, replacement2)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

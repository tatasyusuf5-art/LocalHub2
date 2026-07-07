import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

gallery_target = r'    val galleryLauncher = rememberLauncherForActivityResult.*?\}\n'
content = re.sub(gallery_target, '', content, flags=re.DOTALL)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# search for galleryLauncher
match = re.search(r'val galleryLauncher = rememberLauncherForActivityResult.*?\}', content, re.DOTALL)
if match:
    print(match.group(0))

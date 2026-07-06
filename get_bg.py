import re
with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()
match = re.search(r'settingsRepository\.deleteBackgroundImage\(bgId, encryptedPath\)', content)
if match:
    print(content[match.start() - 50:match.start() + 200])

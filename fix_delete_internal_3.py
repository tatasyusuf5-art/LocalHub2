import re
with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

target = """                // Remove video from database and delete its secure/encrypted storage files
                deleteVideoInternal(videoId, details)"""

replacement = """                // Remove video from database and delete its secure/encrypted storage files
                deleteVideoInternal(videoId, details)
                SecureStorageHelper.clearTempFiles(context)"""

content = content.replace(target, replacement)
with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)
print("Added clearTempFiles")

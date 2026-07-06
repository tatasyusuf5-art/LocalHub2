import re
with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

content = content.replace("deleteVideoInternal(id, details)", "deleteVideoInternal(id)")
with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)
print("Fixed deleteVideoInternal calls")

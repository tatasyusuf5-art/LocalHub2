import re

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

# remove duplicate import
content = content.replace("import java.io.File\n", "", 1)

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)

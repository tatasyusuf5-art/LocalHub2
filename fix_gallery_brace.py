import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("    // Picker for adding new videos\n    }\n", "    // Picker for adding new videos\n")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

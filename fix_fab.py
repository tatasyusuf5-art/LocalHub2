import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Replace "floatingActionButton = {\n            }\n        }\n    }\n" or similar
# Wait, let's just make sure floatingActionButton is empty properly.
content = re.sub(r'floatingActionButton = \{\s*\}\s*\}\s*', 'floatingActionButton = { }\n', content)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

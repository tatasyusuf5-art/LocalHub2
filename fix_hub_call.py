import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = r', onNavigateToProcessing = \{\s*navController.navigate\(ROUTE_PROCESSING\)\s*\}'
content = re.sub(target, '', content)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

fab_match = re.search(r'floatingActionButton = \{.*?if \(\!isInSelectionMode\) \{.*?FloatingActionButton.*?Icon\(Icons\.Default\.Add, contentDescription = "Add video"\).*?\}', content, re.DOTALL)
if fab_match:
    content = content.replace(fab_match.group(0), "floatingActionButton = {")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

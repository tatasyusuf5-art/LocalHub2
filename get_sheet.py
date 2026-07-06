import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()
match = re.search(r'VideoSettingsSheet\(.*?\)', content, re.DOTALL)
if match:
    print(match.group(0))

import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

match = re.search(r'fun HubScreen\(.*?\).*?\{', content, re.DOTALL)
if match:
    print(content[match.start():match.start()+1000])

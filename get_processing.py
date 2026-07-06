import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()
match = re.search(r'fun VideoProcessingScreen\(.*?\)', content, re.DOTALL)
if match:
    # Just print the signature and a bit of the body
    print(content[match.start():match.start()+1000])

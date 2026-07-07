import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    lines = f.readlines()
for i, line in enumerate(lines[890:920]):
    print(f"{890+i+1}: {line.rstrip()}")

import sys

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    lines = f.readlines()

count = 0
in_hub = False
hub_start = 0

for i, line in enumerate(lines):
    if "fun HubScreen(" in line and not in_hub:
        in_hub = True
        hub_start = i
        count = 0
    
    if in_hub:
        count += line.count('{')
        count -= line.count('}')
        
        if count == 0 and line.count('}') > 0:
            print(f"HubScreen closed at line {i+1}")
            in_hub = False

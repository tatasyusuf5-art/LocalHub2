import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Remove navigation route
nav_target = r'            composable\(ROUTE_PROCESSING\) \{.*?\n            \}'
content = re.sub(nav_target, '', content, flags=re.DOTALL)

# Find and remove VideoProcessingScreen
start_idx = content.find("fun VideoProcessingScreen(")
if start_idx != -1:
    end_idx = content.find("// ==========================================\n// 3. SETTINGS", start_idx)
    if end_idx != -1:
        # Also need to find the start of the function including annotations
        annotation_idx = content.rfind("@OptIn(", 0, start_idx)
        if annotation_idx != -1:
            content = content[:annotation_idx] + content[end_idx:]

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

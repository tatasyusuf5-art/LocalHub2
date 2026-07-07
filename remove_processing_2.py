import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

start_idx = content.find("fun VideoProcessingScreen(")
if start_idx != -1:
    end_idx = content.find("// ==========================================\n// 6. GENEL AYARLAR", start_idx)
    if end_idx != -1:
        annotation_idx = content.rfind("@OptIn(", 0, start_idx)
        if annotation_idx != -1:
            content = content[:annotation_idx] + content[end_idx:]

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

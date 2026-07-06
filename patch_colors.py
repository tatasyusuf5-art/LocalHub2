import re

with open('app/src/main/java/com/example/ui/theme/Color.kt', 'r') as f:
    content = f.read()

content = content.replace('val CardBackground = Color(0xFF1A1A1A)', 'val CardBackground = Color(0x73222222) // Translucent grey')

with open('app/src/main/java/com/example/ui/theme/Color.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

content = content.replace('containerColor = Black', 'containerColor = Color.Black.copy(alpha = 0.45f)')

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)

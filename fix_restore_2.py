with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

import re
match = re.search(r'(fun restoreVideoToGallery.*?withContext\(Dispatchers\.Main\) \{ onSuccess\(\) \})', content, re.DOTALL)
if match:
    print(match.group(1))

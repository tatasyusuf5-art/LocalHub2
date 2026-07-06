import re
with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()
match = re.search(r'private suspend fun deleteVideoInternal.*?\n    }', content, re.DOTALL)
if match:
    print(match.group(0))

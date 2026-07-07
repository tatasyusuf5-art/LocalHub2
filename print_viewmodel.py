import re
with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

print("preparePickedVideo:")
match = re.search(r'fun preparePickedVideo\(.*?\).*?\{.*?(?=\n    fun |\Z)', content, re.DOTALL)
if match: print(match.group(0))

print("\nimportVideo:")
match2 = re.search(r'fun importVideo\(.*?\).*?\{.*?(?=\n    fun |\Z)', content, re.DOTALL)
if match2: print(match2.group(0))


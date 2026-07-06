import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Remove the edit option from the bottom sheet
sheet_target = """            SheetItem(Icons.Default.Edit, "Başlığı Düzenle") {
                onEditTitle()
                onDismiss()
            }"""
content = content.replace(sheet_target, "")

# Remove the edit title dialog completely (we can just leave the state unused or remove the dialog code)
dialog_target = r'if \(showEditTitleDialog\) \{.*?\n        \}\n    \}'
# Actually let's just make showEditTitleDialog always false and remove the UI for it
content = re.sub(r'if \(showEditTitleDialog\) \{.*?\}\n            \}\n        \)\n    \}', '', content, flags=re.DOTALL)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

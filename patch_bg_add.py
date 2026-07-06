import sys

with open('app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt', 'r') as f:
    content = f.read()

target = """                if (_selectedBackgroundId.value.isEmpty()) {
                    _selectedBackgroundId.value = bgId
                    prefs.edit().putString("selected_bg_id", bgId).apply()
                }"""

replacement = """                if (_selectedBackgroundId.value.isEmpty()) {
                    _selectedBackgroundId.value = bgId
                    prefs.edit().putString("selected_bg_id", bgId).apply()
                }
                
                // Attempt to delete original from gallery
                val sender = MediaProcessingHelper.getDeleteRequestIntentSender(context, uri)
                if (sender != null) {
                    _pendingDeleteSender.value = sender
                }"""

if target in content:
    content = content.replace(target, replacement)
    with open('app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt', 'w') as f:
        f.write(content)
    print("Success")
else:
    print("Target not found")

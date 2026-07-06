import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Replace the title logic to just use the raw name, or "Video" if somehow empty
title_logic_target = """                if (result == null) {
                    result = uri.path
                    val cut = result?.lastIndexOf('/') ?: -1
                    if (cut != -1) {
                        result = result?.substring(cut + 1)
                    }
                }
                val name = result?.substringBeforeLast(".") ?: ""
                if (name.isNotEmpty() && (name.all { it.isDigit() } || name.startsWith("msf:"))) "" else name
            } ?: ""
        ) 
    }"""

title_logic_replacement = """                if (result == null) {
                    result = uri.path
                    val cut = result?.lastIndexOf('/') ?: -1
                    if (cut != -1) {
                        result = result?.substring(cut + 1)
                    }
                }
                val name = result?.substringBeforeLast(".") ?: ""
                if (name.isBlank()) "Video" else name
            } ?: "Video"
        ) 
    }"""

content = content.replace(title_logic_target, title_logic_replacement)

# Remove the OutlinedTextField for title
textfield_target = """            // Title input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Video Başlığı (Zorunlu)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryOrange,
                    unfocusedBorderColor = TextSecondary,
                    focusedLabelColor = PrimaryOrange,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )"""

content = content.replace(textfield_target, "")

# Update the Save button to not require title to be notBlank (though it will be anyway)
button_target = """                    enabled = title.isNotBlank() && pickedVideoUri != null,"""
button_replacement = """                    enabled = pickedVideoUri != null,"""

content = content.replace(button_target, button_replacement)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

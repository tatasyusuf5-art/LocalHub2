import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Replace the initial state declaration for 'title' in VideoProcessingScreen (or similar)
target = "var title by remember { mutableStateOf(\"\") }"
replacement = """var title by remember(pickedVideoUri) { 
        mutableStateOf(
            pickedVideoUri?.let { uri ->
                var result: String? = null
                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index != -1) {
                                result = cursor.getString(index)
                            }
                        }
                    }
                }
                if (result == null) {
                    result = uri.path
                    val cut = result?.lastIndexOf('/') ?: -1
                    if (cut != -1) {
                        result = result?.substring(cut + 1)
                    }
                }
                result?.substringBeforeLast(".") ?: ""
            } ?: ""
        ) 
    }"""

if target in content:
    content = content.replace(target, replacement)
    print("Replaced title successfully.")
else:
    print("Could not find title state.")

# Remove the appended test.kt parts from the bottom
content = re.sub(r'import android\.net\.Uri\s+import android\.content\.Context\s+import android\.provider\.OpenableColumns\s+fun getFileName.*?}$', '', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)

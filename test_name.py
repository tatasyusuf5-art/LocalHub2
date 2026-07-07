import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = """                var result: String? = null
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
                val name = result?.substringBeforeLast(".") ?: ""
                if (name.isBlank()) "Video" else name"""

replacement = """                var result: String? = null
                if (uri.scheme == "content") {
                    // Try MediaStore TITLE first
                    context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Video.Media.TITLE), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.TITLE)
                            if (index != -1) {
                                result = cursor.getString(index)
                            }
                        }
                    }
                    // Fallback to DISPLAY_NAME
                    if (result.isNullOrBlank()) {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (index != -1) {
                                    result = cursor.getString(index)
                                }
                            }
                        }
                    }
                }
                if (result.isNullOrBlank()) {
                    result = uri.path
                    val cut = result?.lastIndexOf('/') ?: -1
                    if (cut != -1) {
                        result = result?.substring(cut + 1)
                    }
                }
                
                var name = result?.substringBeforeLast(".") ?: ""
                if (name.startsWith("msf:")) {
                    name = name.substringAfter("msf:")
                }
                // If the name is still just a number, maybe it's the raw ID. 
                // We could just prefix it, or just use the number if that's what the system gives.
                if (name.isBlank()) "Video" else name"""

content = content.replace(target, replacement)
with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

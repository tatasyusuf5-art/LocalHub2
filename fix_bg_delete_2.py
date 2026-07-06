import re
with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

target = """    fun deleteBackgroundImage(bgId: String, encryptedPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.deleteBackgroundImage(bgId, encryptedPath)
            if (_selectedBackgroundId.value == bgId) {
                _selectedBackgroundId.value = ""
                prefs.edit().putString("selected_bg_id", "").apply()
            }
            updateActiveBackground()
        }
    }"""

# Actually, let's just do a regex replace
import re
new_func = """    fun deleteBackgroundImage(context: Context, bgId: String, encryptedPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(encryptedPath)
                if (file.exists()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val resolver = context.contentResolver
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "Arkaplan_${System.currentTimeMillis()}.jpg")
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Restored")
                            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { out ->
                                java.io.FileInputStream(file).use { input ->
                                    val buffer = ByteArray(8192)
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        out.write(buffer, 0, read)
                                    }
                                }
                            }
                            contentValues.clear()
                            contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(uri, contentValues, null, null)
                        }
                    } else {
                        val dcimDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
                        val restoredDir = java.io.File(dcimDir, "Restored")
                        if (!restoredDir.exists()) {
                            restoredDir.mkdirs()
                        }
                        val outFile = java.io.File(restoredDir, "Arkaplan_${System.currentTimeMillis()}.jpg")
                        java.io.FileOutputStream(outFile).use { out ->
                            java.io.FileInputStream(file).use { input ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    out.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(outFile.absolutePath),
                            arrayOf("image/jpeg"),
                            null
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            settingsRepository.deleteBackgroundImage(bgId, encryptedPath)
            if (_selectedBackgroundId.value == bgId) {
                _selectedBackgroundId.value = ""
                prefs.edit().putString("selected_bg_id", "").apply()
            }
        }
    }"""

content = re.sub(r'fun deleteBackgroundImage\(bgId: String, encryptedPath: String\).*?\n    }', new_func, content, flags=re.DOTALL)

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)
print("Replaced!")

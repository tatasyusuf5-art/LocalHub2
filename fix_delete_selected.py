import re
with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

target = """    fun deleteSelectedVideos() {
        val idsToDelete = _selectedVideoIds.value
        viewModelScope.launch(Dispatchers.IO) {
            idsToDelete.forEach { id ->
                deleteVideoInternal(id)
            }"""

replacement = """    fun deleteSelectedVideos(context: Context) {
        val idsToDelete = _selectedVideoIds.value
        viewModelScope.launch(Dispatchers.IO) {
            idsToDelete.forEach { id ->
                deleteVideoInternal(id)
            }
            SecureStorageHelper.clearTempFiles(context)"""

content = content.replace(target, replacement)
with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)
print("Updated deleteSelectedVideos")

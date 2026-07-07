import re

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "r") as f:
    content = f.read()

imports = """import android.os.Environment
import java.io.File"""

if "import android.os.Environment" not in content:
    content = content.replace("import kotlin.random.Random", "import kotlin.random.Random\n" + imports)

inbox_logic = """
    private val _inboxVideos = MutableStateFlow<List<File>>(emptyList())
    val inboxVideos = _inboxVideos.asStateFlow()

    fun checkInboxFolder() {
        try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val inboxDir = File(docsDir, ".lh/inbox")
            if (!inboxDir.exists()) {
                inboxDir.mkdirs()
                _inboxVideos.value = emptyList()
                return
            }
            val files = inboxDir.listFiles()?.filter { it.extension.lowercase() in listOf("mp4", "ts") } ?: emptyList()
            _inboxVideos.value = files.sortedBy { it.lastModified() }
        } catch (e: Exception) {
            e.printStackTrace()
            _inboxVideos.value = emptyList()
        }
    }
    
    fun dismissInboxDialog() {
        _inboxVideos.value = emptyList()
    }
"""

if "_inboxVideos" not in content:
    content = content.replace("    val logRepository = LogRepository(database.failedAttemptDao())", "    val logRepository = LogRepository(database.failedAttemptDao())\n" + inbox_logic)

with open("app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt", "w") as f:
    f.write(content)

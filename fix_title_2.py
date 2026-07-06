import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = """                if (result == null) {
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

replacement = """                if (result == null) {
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

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(content)
    print("Patched title successfully.")
else:
    print("Could not find target string.")

import re

with open('app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
'''    private val _activeBackgroundPath = MutableStateFlow<String?>(null)
    val activeBackgroundPath: StateFlow<String?> = _activeBackgroundPath.asStateFlow()''',
'''    val activeBackgroundPath: StateFlow<String?> = kotlinx.coroutines.flow.combine(
        settingsRepository.allBackgroundImages,
        _selectedBackgroundId,
        _isRandomBackgroundEnabled
    ) { list, savedId, isRandom ->
        if (list.isEmpty()) return@combine null
        if (isRandom) list.randomOrNull()?.encryptedPath
        else list.firstOrNull { it.id == savedId }?.encryptedPath ?: list.firstOrNull()?.encryptedPath
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)'''
)

content = content.replace('        updateActiveBackground()\n', '')
content = content.replace('    fun updateActiveBackground() {\n        viewModelScope.launch(Dispatchers.IO) {\n            val list = settingsRepository.allBackgroundImages.first()\n            if (list.isEmpty()) {\n                _activeBackgroundPath.value = null\n                return@launch\n            }\n\n            if (_isRandomBackgroundEnabled.value) {\n                // Pick random from pool\n                val randomBg = list.randomOrNull()\n                _activeBackgroundPath.value = randomBg?.encryptedPath\n            } else {\n                val savedId = _selectedBackgroundId.value\n                val matching = list.firstOrNull { it.id == savedId }\n                _activeBackgroundPath.value = matching?.encryptedPath ?: list.firstOrNull()?.encryptedPath\n            }\n        }\n    }', '')

with open('app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt', 'w') as f:
    f.write(content)

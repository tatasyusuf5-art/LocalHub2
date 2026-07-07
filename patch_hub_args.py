import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target1 = """fun HubScreen(
    viewModel: AppViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToProcessing: () -> Unit
)"""
replacement1 = """fun HubScreen(
    viewModel: AppViewModel,
    onNavigateToSettings: () -> Unit
)"""

target2 = """                HubScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onNavigateToProcessing = { navController.navigate(ROUTE_PROCESSING) }
                )"""
replacement2 = """                HubScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) }
                )"""

content = content.replace(target1, replacement1)
content = content.replace(target2, replacement2)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

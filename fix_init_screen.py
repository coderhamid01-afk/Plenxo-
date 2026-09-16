import re

with open("app/src/main/java/com/example/viewmodel/PlenxoViewModel.kt", "r") as f:
    content = f.read()

content = content.replace("private val _currentScreen = MutableStateFlow(PlenxoScreen.LOGIN)", "private val _currentScreen = MutableStateFlow(PlenxoScreen.PLACEHOLDER_ENTRY)")

with open("app/src/main/java/com/example/viewmodel/PlenxoViewModel.kt", "w") as f:
    f.write(content)

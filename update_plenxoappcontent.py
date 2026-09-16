import re

with open("app/src/main/java/com/example/ui/PlenxoAppContent.kt", "r") as f:
    content = f.read()

old_placeholder = """            PlenxoScreen.PLACEHOLDER_ENTRY,
            PlenxoScreen.EMAIL_VERIFICATION_WAIT -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryColor)
                }
            }"""

new_placeholder = """            PlenxoScreen.PLACEHOLDER_ENTRY,
            PlenxoScreen.EMAIL_VERIFICATION_WAIT -> {
                SplashScreen()
            }"""

content = content.replace(old_placeholder, new_placeholder)

with open("app/src/main/java/com/example/ui/PlenxoAppContent.kt", "w") as f:
    f.write(content)

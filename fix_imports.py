import re

with open("app/src/main/java/com/example/ui/components/VoiceNoteBubble.kt", "r") as f:
    content = f.read()

content = content.replace("import androidx.compose.foundation.layout.*", "import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.border\nimport androidx.compose.foundation.clickable")

with open("app/src/main/java/com/example/ui/components/VoiceNoteBubble.kt", "w") as f:
    f.write(content)

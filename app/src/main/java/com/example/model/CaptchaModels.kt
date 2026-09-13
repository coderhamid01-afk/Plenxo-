package com.example.model

import androidx.compose.ui.graphics.Color
import java.security.SecureRandom
import java.util.UUID

/**
 * Shape types supported in dynamic captcha verification.
 */
enum class CaptchaShapeType(val shapeName: String) {
    CIRCLE("Circle"),
    SQUARE("Square"),
    TRIANGLE("Triangle"),
    STAR("Star"),
    DIAMOND("Diamond"),
    HEXAGON("Hexagon"),
    HEART("Heart")
}

/**
 * Palette of high-contrast tint colors for shapes in captcha.
 */
data class CaptchaColorOption(
    val name: String,
    val color: Color
)

val CAPTCHA_PALETTE: List<CaptchaColorOption> = listOf(
    CaptchaColorOption("Yellow", Color(0xFFFBBF24)),
    CaptchaColorOption("Blue", Color(0xFF38BDF8)),
    CaptchaColorOption("Red", Color(0xFFF43F5E)),
    CaptchaColorOption("Green", Color(0xFF10B981)),
    CaptchaColorOption("Purple", Color(0xFFA855F7)),
    CaptchaColorOption("Orange", Color(0xFFF97316)),
    CaptchaColorOption("Pink", Color(0xFFEC4899)),
    CaptchaColorOption("Cyan", Color(0xFF06B6D4))
)

/**
 * Model representing an individual shape item in the dynamic sequence.
 */
data class CaptchaShapeItem(
    val id: String = UUID.randomUUID().toString(),
    val shapeType: CaptchaShapeType,
    val colorName: String,
    val color: Color
) {
    val displayName: String
        get() = "$colorName ${shapeType.shapeName}"
}

/**
 * Secure generator for Step 3 sequence captcha using java.security.SecureRandom.
 */
object SequenceCaptchaGenerator {
    private val secureRandom = SecureRandom()

    fun generateSession(
        minLen: Int = 3,
        maxLen: Int = 5
    ): Pair<List<CaptchaShapeItem>, List<CaptchaShapeItem>> {
        val count = minLen + secureRandom.nextInt(maxLen - minLen + 1)
        val allShapes = CaptchaShapeType.values().toList().shuffled(secureRandom)
        val allColors = CAPTCHA_PALETTE.shuffled(secureRandom)

        // Generate target sequence items
        val targetList = mutableListOf<CaptchaShapeItem>()
        for (i in 0 until count) {
            val shape = allShapes[i % allShapes.size]
            val colorOpt = allColors[i % allColors.size]
            targetList.add(
                CaptchaShapeItem(
                    id = "target_${UUID.randomUUID()}",
                    shapeType = shape,
                    colorName = colorOpt.name,
                    color = colorOpt.color
                )
            )
        }

        // Build grid selection pool with copies of target items + distractors
        val gridPool = mutableListOf<CaptchaShapeItem>()
        targetList.forEach { item ->
            gridPool.add(item.copy())
        }

        // Add distinct distractors to provide a rich 6-8 item grid
        val remainingShapes = CaptchaShapeType.values().filter { shape ->
            targetList.none { it.shapeType == shape }
        }.shuffled(secureRandom)
        val remainingColors = CAPTCHA_PALETTE.filter { col ->
            targetList.none { it.colorName == col.name }
        }.shuffled(secureRandom)

        val desiredTotal = if (count <= 3) 6 else 8
        var distractorIndex = 0
        while (gridPool.size < desiredTotal) {
            val dShape = if (remainingShapes.isNotEmpty() && distractorIndex < remainingShapes.size) {
                remainingShapes[distractorIndex]
            } else {
                allShapes[secureRandom.nextInt(allShapes.size)]
            }
            val dColor = if (remainingColors.isNotEmpty() && distractorIndex < remainingColors.size) {
                remainingColors[distractorIndex]
            } else {
                allColors[secureRandom.nextInt(allColors.size)]
            }
            gridPool.add(
                CaptchaShapeItem(
                    id = "distractor_${UUID.randomUUID()}",
                    shapeType = dShape,
                    colorName = dColor.name,
                    color = dColor.color
                )
            )
            distractorIndex++
        }

        // Shuffle grid items securely so positions are never predictable
        val shuffledGrid = gridPool.shuffled(secureRandom)
        return Pair(targetList, shuffledGrid)
    }
}

package com.jasper.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.jasper.app.data.repository.FaceRepository
import com.jasper.app.data.repository.model.RecognitionResult

private val ColorGreen  = Color(0xFF4CAF50)   // high-confidence known face
private val ColorYellow = Color(0xFFFFEB3B)   // low-confidence known face (near threshold)
private val ColorRed    = Color(0xFFF44336)   // unknown face

/**
 * Transparent canvas overlay that draws bounding boxes and confidence labels for [results].
 *
 * Color scheme:
 *  - Known face, high confidence  → green
 *  - Known face, near threshold   → yellow (gradient between threshold and 1.0)
 *  - Unknown face                 → red
 *
 * Bounding boxes use normalized [0,1] coordinates and are mapped to canvas pixels here.
 */
@Composable
fun FaceOverlay(
    results: List<RecognitionResult>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        for (result in results) {
            val color = overlayColor(result)
            drawFaceBox(result, color, textMeasurer)
        }
    }
}

/**
 * Compute overlay color based on recognition result:
 *  - Unknown → red
 *  - Known, similarity in [threshold, 1] → yellow→green gradient
 */
private fun overlayColor(result: RecognitionResult): Color {
    if (!result.isKnown) return ColorRed

    val threshold = FaceRepository.DEFAULT_THRESHOLD
    // Normalize score from [threshold, 1] → [0, 1]
    val t = ((result.similarityScore - threshold) / (1f - threshold)).coerceIn(0f, 1f)
    return lerp(ColorYellow, ColorGreen, t)
}

private fun DrawScope.drawFaceBox(
    result: RecognitionResult,
    color: Color,
    textMeasurer: TextMeasurer
) {
    val box = result.boundingBox
    val left   = box.left   * size.width
    val top    = box.top    * size.height
    val right  = box.right  * size.width
    val bottom = box.bottom * size.height

    // Bounding box
    drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(width = 3f)
    )

    // Label: "Name  87%" or "Unknown"
    val label = if (result.isKnown) {
        "${result.label}  ${"%.0f".format(result.confidencePercent)}%"
    } else {
        "Unknown"
    }

    val textStyle = TextStyle(
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
    val textLayout = textMeasurer.measure(label, textStyle)

    // Semi-transparent background pill behind text
    val padding = 4f
    drawRect(
        color = color.copy(alpha = 0.65f),
        topLeft = Offset(left, top - textLayout.size.height - padding * 2),
        size = Size(
            textLayout.size.width + padding * 2,
            textLayout.size.height + padding * 2
        )
    )

    drawText(
        textLayoutResult = textLayout,
        topLeft = Offset(left + padding, top - textLayout.size.height - padding)
    )
}

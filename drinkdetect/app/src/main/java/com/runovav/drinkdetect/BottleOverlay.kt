package com.runovav.drinkdetect

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp


@Composable
fun BottleOverlay(
    bottles: List<RectF>,
    originalImageWidth: Int,
    originalImageHeight: Int,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    if (originalImageWidth == 0 || originalImageHeight == 0) {
        return // Avoid division by zero if dimensions aren't ready
    }

    Canvas(modifier = modifier) {
        val scaleX = size.width / originalImageWidth.toFloat()
        val scaleY = size.height / originalImageHeight.toFloat()

        bottles.forEach { boundingBox ->
            val left = if (isFrontCamera) {
                originalImageWidth - boundingBox.right
            } else {
                boundingBox.left
            }
            val right = if (isFrontCamera) {
                originalImageWidth - boundingBox.left
            } else {
                boundingBox.right
            }

            // Apply scaling and offset
            val scaledLeft = left * scaleX
            val scaledTop = boundingBox.top * scaleY
            val scaledRight = right * scaleX
            val scaledBottom = boundingBox.bottom * scaleY

            val composeRect = Rect(scaledLeft, scaledTop, scaledRight, scaledBottom)

            drawRect(
                color = Color.Yellow,
                topLeft = composeRect.topLeft,
                size = composeRect.size,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
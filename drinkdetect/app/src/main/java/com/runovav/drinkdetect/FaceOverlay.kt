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
fun FaceOverlay(
    faces: List<RectF>,
    originalImageWidth: Int,
    originalImageHeight: Int,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    if (originalImageWidth == 0 || originalImageHeight == 0) {
        return
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val widthScaleFactor = canvasWidth / originalImageWidth.toFloat()
        val heightScaleFactor = canvasHeight / originalImageHeight.toFloat()

        val scale = maxOf(widthScaleFactor, heightScaleFactor)

        val offsetX = (canvasWidth - (originalImageWidth * scale)) / 2f
        val offsetY = (canvasHeight - (originalImageHeight * scale)) / 2f


        faces.forEach { boundingBox ->
            val left = if (isFrontCamera) {
                originalImageWidth - boundingBox.right // Flip horizontal
            } else {
                boundingBox.left
            }
            val right = if (isFrontCamera) {
                originalImageWidth - boundingBox.left // Flip horizontal
            } else {
                boundingBox.right
            }

            // Apply scaling and offset
            val scaledLeft = left * scale + offsetX
            val scaledTop = boundingBox.top * scale + offsetY
            val scaledRight = right * scale + offsetX
            val scaledBottom = boundingBox.bottom * scale + offsetY

            val composeRect = Rect(scaledLeft, scaledTop, scaledRight, scaledBottom)

            drawRect(
                color = Color.Blue,
                topLeft = composeRect.topLeft,
                size = composeRect.size,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
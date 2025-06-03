package com.runovav.drinkdetect

import android.graphics.RectF
import android.util.Log

fun RectF.area(): Float = width().coerceAtLeast(0f) * height().coerceAtLeast(0f)

fun RectF.intersection(other: RectF): RectF? {
    val left = maxOf(this.left, other.left)
    val right = minOf(this.right, other.right)
    val top = maxOf(this.top, other.top)
    val bottom = minOf(this.bottom, other.bottom)
    return if (left < right && top < bottom) RectF(left, top, right, bottom) else null
}

fun RectF.overlapPercentage(other: RectF): Float {
    val intersectionRect = intersection(other) ?: return 0f
    val intersectionArea = intersectionRect.area()
    val thisArea = area()
    return if (thisArea > 0f) (intersectionArea / thisArea) * 100f else 0f
}

fun filterFacesWithBottlesInFront(faces: List<RectF>, bottles: List<RectF>, overlapThreshold: Float = 10f): List<RectF> {
    return faces.filter { face ->
        bottles.any { bottle ->
            Log.d("", face.overlapPercentage(bottle).toString())
            face.overlapPercentage(bottle) >= overlapThreshold
        }
    }
}
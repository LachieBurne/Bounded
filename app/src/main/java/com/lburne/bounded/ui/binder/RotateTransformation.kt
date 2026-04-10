package com.lburne.bounded.ui.binder

import android.graphics.Bitmap
import android.graphics.Matrix
import coil.size.Size
import coil.transform.Transformation

class RotateTransformation(private val degrees: Float) : Transformation {
    // Cache key is needed so Coil knows this version is different from the original
    override val cacheKey: String = "rotate-$degrees"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)

        // Create a new bitmap that is rotated
        return Bitmap.createBitmap(
            input, 0, 0, input.width, input.height, matrix, true
        )
    }
}
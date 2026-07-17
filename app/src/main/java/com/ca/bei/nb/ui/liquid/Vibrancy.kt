package com.ca.bei.nb.ui.liquid

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 色彩增强效果 (Vibrancy)
 * 通过调整饱和度使背景更鲜艳，模拟玻璃透出的色彩感
 */
fun Modifier.vibrancy(
    saturation: Float = 1.5f,
    contrast: Float = 1.1f,
    brightness: Float = 0f
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    this.graphicsLayer {
        val matrix = ColorMatrix().apply {
            setSaturation(saturation)
        }
        
        if (contrast != 1f || brightness != 0f) {
            val scale = contrast
            val translate = (-0.5f * scale + 0.5f) * 255f + brightness * 255f
            val contrastMatrix = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(contrastMatrix)
        }
        
        val filter = ColorMatrixColorFilter(matrix)
        renderEffect = RenderEffect.createColorFilterEffect(filter).asComposeRenderEffect()
    }
} else {
    this
}

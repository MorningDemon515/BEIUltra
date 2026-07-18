package com.ca.bei.nb.ui.liquid

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 极简柔边缘液态玻璃
 * 纯透明，无高光，边缘模糊，支持动态缩放
 */

object LiquidGlassShader {

    const val ROUNDED_RECT_SDF = """
        float sdRoundedRect(float2 coord, float2 size, float4 radii) {
            float2 radii2 = (coord.x > 0.0) ? ((coord.y > 0.0) ? radii.zy : radii.wx) : ((coord.y > 0.0) ? radii.xw : radii.yz);
            float r = radii2.x;
            float2 q = abs(coord) - size + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }
    """

    const val COMMON_UNIFORMS = """
        uniform float2 lensSize;
        uniform float2 lensCenter;
        uniform float4 cornerRadii;
        uniform float time;
        uniform float amount;
        uniform float blurRadius;
        uniform shader content;
    """

    val REFRACTION_SHADER = """
        $COMMON_UNIFORMS
        $ROUNDED_RECT_SDF

        half4 main(float2 coord) {
            float2 halfLensSize = lensSize * 0.5;
            float2 centeredCoord = coord - lensCenter;
            float sd = sdRoundedRect(centeredCoord, halfLensSize, cornerRadii);
            
            // 边缘模糊：通过 smoothstep 实现从内到外的柔和透明度过渡
            float alpha = 1.0 - smoothstep(-blurRadius, blurRadius, sd);
            
            if (alpha <= 0.0) {
                return content.eval(coord);
            }
            
            float normAmount = amount / 25.0; 
            
            // 1. 写实平缓水波 (低频叠加)
            float w1 = sin(coord.x * 0.035 + time * 2.5) * 1.8;
            float w2 = cos(coord.y * 0.03 + time * 2.0) * 1.8;
            float totalWave = (w1 + w2) * normAmount * alpha;
            
            // 2. 适度的放大倍率 (降低倍率，仅产生轻微凸起感)
            float magnification = 1.0 + 0.45 * normAmount * alpha;
            
            float2 refractedCoord = lensCenter + (centeredCoord / magnification) + totalWave;
            
            // 3. 纯净透明质感
            half4 color = content.eval(refractedCoord);
            
            // 仅增加极微弱的明度提升 (0.02)，不添加任何颜色或高光
            color.rgb += 0.02 * normAmount * alpha;
            
            return mix(content.eval(coord), color, alpha);
        }
    """
}

/**
 * 极简液态玻璃效果 Modifier
 */
fun Modifier.liquidGlassEffect(
    amount: Float = 0f,
    lensSize: Size = Size.Zero,
    lensCenter: Offset = Offset.Zero,
    blurRadius: Float = 20f,
    cornerRadii: FloatArray = floatArrayOf(40f, 40f, 40f, 40f)
): Modifier = composed {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        var time by remember { mutableStateOf(0f) }
        LaunchedEffect(Unit) {
            while (true) {
                withInfiniteAnimationFrameMillis {
                    time = it / 1000f
                }
            }
        }

        this.graphicsLayer {
            if (amount <= 0.01f) {
                renderEffect = null
                return@graphicsLayer
            }

            val shader = RuntimeShader(LiquidGlassShader.REFRACTION_SHADER)
            val finalLensSize = if (lensSize == Size.Zero) size else lensSize
            
            shader.setFloatUniform("lensSize", finalLensSize.width, finalLensSize.height)
            shader.setFloatUniform("lensCenter", lensCenter.x, lensCenter.y)
            shader.setFloatUniform("cornerRadii", cornerRadii[0], cornerRadii[1], cornerRadii[2], cornerRadii[3])
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("amount", amount)
            shader.setFloatUniform("blurRadius", blurRadius)

            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
    } else {
        this
    }
}

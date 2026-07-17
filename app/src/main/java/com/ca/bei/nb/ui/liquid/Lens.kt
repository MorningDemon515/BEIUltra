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
 * 极致液态玻璃着色器
 * 模拟装有蓝色水的放大气泡效果
 */

object LiquidGlassShader {

    const val ROUNDED_RECT_SDF = """
        float radiusAt(float2 coord, float4 radii) {
            if (coord.x < 0.0 && coord.y < 0.0) return radii.x;
            if (coord.x > 0.0 && coord.y < 0.0) return radii.y;
            if (coord.x > 0.0 && coord.y > 0.0) return radii.z;
            return radii.w;
        }

        float sdRoundedRect(float2 coord, float2 size, float4 radii) {
            float r = radiusAt(coord, radii);
            float2 q = abs(coord) - size + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        float2 gradSdRoundedRect(float2 coord, float2 size, float4 radii) {
            float2 e = float2(1.0, -1.0) * 0.5;
            return normalize(e.xy * sdRoundedRect(coord + e.xy, size, radii) +
                             e.yy * sdRoundedRect(coord + e.yy, size, radii) +
                             e.yx * sdRoundedRect(coord + e.yx, size, radii) +
                             e.xx * sdRoundedRect(coord + e.xx, size, radii));
        }
    """

    const val COMMON_UNIFORMS = """
        uniform float2 lensSize;
        uniform float2 lensCenter;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float depthEffect;
        uniform float time;
        uniform shader content;
    """

    val REFRACTION_SHADER = """
        $COMMON_UNIFORMS
        $ROUNDED_RECT_SDF

        half4 main(float2 coord) {
            float2 halfLensSize = lensSize * 0.5;
            float2 centeredCoord = coord - lensCenter;
            float sd = sdRoundedRect(centeredCoord, halfLensSize, cornerRadii);
            
            if (sd >= 2.0) { // 边缘留一点缓冲
                return content.eval(coord);
            }
            
            float amountScale = clamp(abs(refractionAmount) / 5.0, 0.0, 1.0);
            
            // 1. 基础扭曲逻辑 (放大镜效果)
            float dist = length(centeredCoord / halfLensSize);
            float bulge = pow(1.0 - clamp(dist, 0.0, 1.0), 2.0);
            
            // 2. 水波纹动态
            float wave = sin(coord.x * 0.06 + time * 5.0) * cos(coord.y * 0.06 + time * 3.5) * 4.0 * amountScale;
            
            // 计算折射后的坐标
            float2 offset = centeredCoord * (bulge * 0.3 * amountScale);
            float2 refractedCoord = coord - offset + wave * 0.5;
            
            half4 baseColor = content.eval(refractedCoord);
            
            // 3. 水色填充 (淡淡的蓝色洗礼)
            float waterTint = (1.0 - bulge) * 0.2 + 0.1;
            baseColor.rgb = mix(baseColor.rgb, float3(0.1, 0.4, 0.9), waterTint * amountScale);
            
            // 4. 气泡高光与光泽
            // 顶部斜侧高光
            float2 lightDir = normalize(float2(-1.0, -1.0));
            float spec = pow(max(0.0, dot(normalize(centeredCoord + 0.001), lightDir)), 8.0);
            baseColor.rgb += float3(0.8, 0.9, 1.0) * spec * amountScale;
            
            // 边缘发光
            float rim = 1.0 - smoothstep(-10.0, 0.0, sd);
            baseColor.rgb += float3(0.5, 0.7, 1.0) * rim * 0.4 * amountScale;
            
            // 5. 简单的边缘遮罩 (抗锯齿)
            float alpha = 1.0 - smoothstep(0.0, 2.0, sd);
            return baseColor * alpha + content.eval(coord) * (1.0 - alpha);
        }
    """
}

/**
 * 核心液态玻璃效果 Modifier
 */
fun Modifier.liquidGlassEffect(
    amount: Float = 20f,
    height: Float = 40f,
    chromaticAberration: Float = 0.5f,
    depthEffect: Float = 0.2f,
    cornerRadii: FloatArray = floatArrayOf(40f, 40f, 40f, 40f),
    lensSize: Size = Size.Zero,
    lensCenter: Offset = Offset.Zero
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
            if (amount <= 0.1f) {
                renderEffect = null
                return@graphicsLayer
            }

            val shader = RuntimeShader(LiquidGlassShader.REFRACTION_SHADER)
            val finalLensSize = if (lensSize == Size.Zero) size else lensSize
            
            shader.setFloatUniform("lensSize", finalLensSize.width, finalLensSize.height)
            shader.setFloatUniform("lensCenter", lensCenter.x, lensCenter.y)
            shader.setFloatUniform("cornerRadii", cornerRadii[0], cornerRadii[1], cornerRadii[2], cornerRadii[3])
            shader.setFloatUniform("refractionHeight", height)
            shader.setFloatUniform("refractionAmount", -amount)
            shader.setFloatUniform("depthEffect", depthEffect)
            shader.setFloatUniform("time", time)

            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
    } else {
        this
    }
}

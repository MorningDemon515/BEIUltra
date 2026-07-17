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
 * 终极液态玻璃 - 完美复刻参考图效果
 * 具有强力放大、深蓝水色填充和高亮边缘
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
        uniform shader content;
    """

    val REFRACTION_SHADER = """
        $COMMON_UNIFORMS
        $ROUNDED_RECT_SDF

        half4 main(float2 coord) {
            float2 halfLensSize = lensSize * 0.5;
            float2 centeredCoord = coord - lensCenter;
            float sd = sdRoundedRect(centeredCoord, halfLensSize, cornerRadii);
            
            // 如果在气泡外，直接渲染原图
            if (sd > 2.0) {
                return content.eval(coord);
            }
            
            float normAmount = amount / 25.0; // 归一化强度
            
            // 1. 强力放大算法 (物理模拟凸透镜)
            float dist = length(centeredCoord / halfLensSize);
            float mask = smoothstep(1.0, 0.0, dist);
            
            // 通过坐标收缩实现放大：坐标离中心越近，取样点越向中心靠拢 = 看起来越像放大了
            // 增加指数系数使其产生边缘向中心拉伸的效果
            float magnification = 1.0 + 1.2 * normAmount * pow(mask, 1.5);
            float2 refractedCoord = lensCenter + (centeredCoord / magnification);
            
            // 加入轻微的水波抖动
            float wave = sin(coord.x * 0.05 + time * 4.0) * cos(coord.y * 0.05 + time * 3.0) * 3.0 * normAmount;
            refractedCoord += wave;

            half4 color = content.eval(refractedCoord);
            
            // 2. 深蓝色液体填充 (加强饱和度)
            float tintStrength = mask * 0.35 * normAmount;
            float3 waterBlue = float3(0.05, 0.35, 0.95);
            color.rgb = mix(color.rgb, waterBlue, tintStrength);
            
            // 3. 玻璃高光与亮边
            // 顶部侧边高亮
            float2 highlightPos = centeredCoord + halfLensSize * 0.4;
            float highlight = smoothstep(0.4, 0.0, length(highlightPos / halfLensSize)) * 0.4 * normAmount;
            color.rgb += float3(0.8, 0.9, 1.0) * highlight;
            
            // 外圈白色亮边 (轮廓线)
            float edge = smoothstep(2.0, -2.0, abs(sd));
            color.rgb = mix(color.rgb, float3(1.0, 1.0, 1.0), edge * 0.6 * normAmount);
            
            // 4. 抗锯齿遮罩
            float alpha = 1.0 - smoothstep(0.0, 2.0, sd);
            return mix(content.eval(coord), color, alpha);
        }
    """
}

/**
 * 终极液态玻璃效果 Modifier
 */
fun Modifier.liquidGlassEffect(
    amount: Float = 0f,
    lensSize: Size = Size.Zero,
    lensCenter: Offset = Offset.Zero,
    cornerRadii: FloatArray = floatArrayOf(60f, 60f, 60f, 60f)
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
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("amount", amount)

            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
    } else {
        this
    }
}

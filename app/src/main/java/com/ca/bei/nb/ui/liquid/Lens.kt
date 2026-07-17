package com.ca.bei.nb.ui.liquid

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 液态玻璃着色器封装
 * 适配自 Kyant0/AndroidLiquidGlass
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
        uniform float2 size;
        uniform float2 offset;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float depthEffect;
        uniform shader content;
    """

    const val CIRCLE_MAP = """
        float circleMap(float x) {
            float v = 1.0 - x;
            return sqrt(1.0 - v * v);
        }
    """

    val REFRACTION_SHADER = """
        $COMMON_UNIFORMS
        $ROUNDED_RECT_SDF
        $CIRCLE_MAP

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = (coord + offset) - halfSize;
            float sd = sdRoundedRect(centeredCoord, halfSize, cornerRadii);
            
            if (-sd >= refractionHeight) {
                return content.eval(coord);
            }
            
            float d = circleMap(clamp(-sd / refractionHeight, 0.0, 1.0)) * refractionAmount;
            float2 grad = gradSdRoundedRect(centeredCoord, halfSize, cornerRadii);
            float2 depth = depthEffect * normalize(centeredCoord);
            float2 refractedCoord = coord + d * normalize(grad + depth);
            
            return content.eval(refractedCoord);
        }
    """

    val DISPERSION_SHADER = """
        $COMMON_UNIFORMS
        uniform float chromaticAberration;
        $ROUNDED_RECT_SDF
        $CIRCLE_MAP

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = (coord + offset) - halfSize;
            float sd = sdRoundedRect(centeredCoord, halfSize, cornerRadii);
            
            if (-sd >= refractionHeight) {
                return content.eval(coord);
            }

            float d = circleMap(clamp(-sd / refractionHeight, 0.0, 1.0)) * refractionAmount;
            float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, cornerRadii) + depthEffect * normalize(centeredCoord));
            
            float2 disp = d * grad * chromaticAberration;
            
            float2 rCoord = coord + d * grad + disp * 1.5;
            float2 oCoord = coord + d * grad + disp * 1.0;
            float2 yCoord = coord + d * grad + disp * 0.5;
            float2 gCoord = coord + d * grad;
            float2 cCoord = coord + d * grad - disp * 0.5;
            float2 bCoord = coord + d * grad - disp * 1.0;
            float2 vCoord = coord + d * grad - disp * 1.5;

            half4 cR = content.eval(rCoord);
            half4 cO = content.eval(oCoord);
            half4 cY = content.eval(yCoord);
            half4 cG = content.eval(gCoord);
            half4 cC = content.eval(cCoord);
            half4 cB = content.eval(bCoord);
            half4 cV = content.eval(vCoord);

            half4 finalColor;
            finalColor.r = (cR.r * 0.15 + cO.r * 0.2 + cY.r * 0.2 + cG.r * 0.45);
            finalColor.g = (cY.g * 0.15 + cG.g * 0.5 + cC.g * 0.2 + cB.g * 0.15);
            finalColor.b = (cC.b * 0.15 + cB.b * 0.5 + cV.b * 0.35);
            finalColor.a = cG.a;

            return finalColor;
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
    cornerRadii: FloatArray = floatArrayOf(40f, 40f, 40f, 40f)
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    this.graphicsLayer {
        val shader = if (chromaticAberration > 0) {
            RuntimeShader(LiquidGlassShader.DISPERSION_SHADER)
        } else {
            RuntimeShader(LiquidGlassShader.REFRACTION_SHADER)
        }

        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("offset", 0f, 0f)
        shader.setFloatUniform("cornerRadii", cornerRadii[0], cornerRadii[1], cornerRadii[2], cornerRadii[3])
        shader.setFloatUniform("refractionHeight", height)
        shader.setFloatUniform("refractionAmount", -amount)
        shader.setFloatUniform("depthEffect", depthEffect)
        if (chromaticAberration > 0) {
            shader.setFloatUniform("chromaticAberration", chromaticAberration)
        }

        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
} else {
    this // 低版本系统回退，不显示特效但保证程序运行
}

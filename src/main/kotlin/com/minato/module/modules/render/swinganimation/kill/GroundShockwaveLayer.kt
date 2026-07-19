@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.kill

import com.minato.graphics.mc.RenderBuilder
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * GroundShockwaveLayer — vòng tròn sóng xung kích lan từ điểm kill.
 */
object GroundShockwaveLayer {

    private const val SEGMENTS = 24

    /**
     * Render shockwave ring using RenderBuilder filledQuadGradient.
     */
    fun RenderBuilder.render(
        killPos: Vec3d,
        cameraPos: Vec3d,
        progress: Float,
        alpha: Float,
    ) {
        val radius = progress * 1.2f
        val ringAlpha = (alpha * (1f - progress) * 255).toInt()
        if (ringAlpha <= 0) return

        val color = Color(0xFF, 0xFA, 0xCD, ringAlpha.coerceIn(0, 255))

        val outerVerts = mutableListOf<Vec3d>()
        val innerVerts = mutableListOf<Vec3d>()

        val angleStep = (Math.PI.toFloat() * 2f) / SEGMENTS
        for (i in 0 until SEGMENTS) {
            val angle = i * angleStep
            val x = (cos(angle) * radius)
            val z = (sin(angle) * radius)

            outerVerts.add(Vec3d(killPos.x + x.toDouble(), killPos.y, killPos.z + z.toDouble()))
            innerVerts.add(Vec3d(killPos.x + (x * 0.85).toDouble(), killPos.y + 0.02, killPos.z + (z * 0.85).toDouble()))
        }

        for (i in 0 until SEGMENTS) {
            val next = (i + 1) % SEGMENTS
            filledQuadGradient(
                outerVerts[i].x, outerVerts[i].y, outerVerts[i].z, color,
                outerVerts[next].x, outerVerts[next].y, outerVerts[next].z, color,
                innerVerts[next].x, innerVerts[next].y, innerVerts[next].z, color,
                innerVerts[i].x, innerVerts[i].y, innerVerts[i].z, color,
            )
        }
    }
}

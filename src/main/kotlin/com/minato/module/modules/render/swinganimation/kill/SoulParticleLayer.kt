@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.kill

import com.minato.graphics.mc.RenderBuilder
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * SoulParticleLayer — hạt linh hồn bay lên từ vị trí kill.
 */
object SoulParticleLayer {

    data class ParticleData(
        val positions: List<Vec3d>,
        val speeds: List<Float>,
        val phases: List<Float>,
    ) {
        val count: Int get() = positions.size

        companion object {
            fun generate(seed: Long, count: Int): ParticleData {
                val rng = Random(seed)
                val positions = mutableListOf<Vec3d>()
                val speeds = mutableListOf<Float>()
                val phases = mutableListOf<Float>()

                for (i in 0 until count) {
                    val radius = rng.nextFloat() * 0.4f
                    val angle = rng.nextFloat() * Math.PI.toFloat() * 2f
                    positions.add(Vec3d(
                        (cos(angle) * radius).toDouble(),
                        (rng.nextFloat() * 0.3f).toDouble(),
                        (sin(angle) * radius).toDouble(),
                    ))
                    speeds.add(0.5f + rng.nextFloat() * 0.5f)
                    phases.add(rng.nextFloat() * Math.PI.toFloat() * 2f)
                }

                return ParticleData(positions, speeds, phases)
            }
        }
    }

    /**
     * Render soul particles using RenderBuilder filledQuad.
     */
    fun RenderBuilder.render(
        killPos: Vec3d,
        cameraPos: Vec3d,
        progress: Float,
        particles: ParticleData,
    ) {
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val a = (alpha * 200).toInt().coerceIn(0, 255)
        val color = Color(255, 250, 205, a)

        for (i in 0 until particles.count) {
            val base = particles.positions[i]
            val speed = particles.speeds[i]
            val phase = particles.phases[i]

            val curlX = cos(progress * 4f + phase) * 0.1f
            val curlZ = sin(progress * 4f + phase) * 0.1f
            val rise = progress * speed * 1.5f

            val worldPos = Vec3d(
                killPos.x + base.x + curlX.toDouble(),
                killPos.y + base.y + rise.toDouble(),
                killPos.z + base.z + curlZ.toDouble(),
            )

            val size = (0.08f * (1f - progress * 0.5f)).coerceAtLeast(0.02f)

            filledQuadGradient(
                worldPos.x - size.toDouble(), worldPos.y - size.toDouble(), worldPos.z, color,
                worldPos.x + size.toDouble(), worldPos.y - size.toDouble(), worldPos.z, color,
                worldPos.x + size.toDouble(), worldPos.y + size.toDouble(), worldPos.z, color,
                worldPos.x - size.toDouble(), worldPos.y + size.toDouble(), worldPos.z, color,
            )
        }
    }
}

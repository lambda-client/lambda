@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.sprint

import com.minato.graphics.mc.RenderBuilder
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.random.Random
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * FootstepDustLayer — tạo hiệu ứng bụi đất bay lên khi sprint.
 */
object FootstepDustLayer {

    data class DustParticle(
        var position: Vec3d,
        var velocity: Vec3d,
        var lifetime: Int,
        var maxLifetime: Int,
        var size: Float,
    )

    private val particles = mutableListOf<DustParticle>()
    private val random = Random.create()
    private var burstCounter = 0

    fun reset() {
        particles.clear()
        burstCounter = 0
    }

    fun tick(
        footPos: Vec3d,
        moveDir: Vec3d,
        intensity: Float,
        qualityCount: Int,
        enabled: Boolean,
    ) {
        if (!enabled || intensity < 0.2f) {
            tickParticles()
            return
        }

        burstCounter++

        val interval = (8 - (intensity * 6)).toInt().coerceIn(2, 8)
        if (burstCounter % interval == 0) {
            val count = (qualityCount * intensity).toInt().coerceIn(1, qualityCount)
            spawnBurst(footPos, moveDir, count)
        }

        tickParticles()
    }

    private fun spawnBurst(footPos: Vec3d, moveDir: Vec3d, count: Int) {
        val backDir = Vec3d(-moveDir.x, 0.0, -moveDir.z).normalize()

        for (i in 0 until count) {
            val spreadX = (random.nextFloat() - 0.5f) * 0.4f
            val spreadZ = (random.nextFloat() - 0.5f) * 0.4f
            val pos = Vec3d(
                footPos.x + backDir.x * 0.2 + spreadX.toDouble(),
                footPos.y + 0.05 + (random.nextFloat() * 0.1).toDouble(),
                footPos.z + backDir.z * 0.2 + spreadZ.toDouble(),
            )
            val vel = Vec3d(
                (backDir.x * 0.08 + (random.nextFloat() - 0.5f) * 0.04).toDouble(),
                (0.02 + random.nextFloat() * 0.06).toDouble(),
                (backDir.z * 0.08 + (random.nextFloat() - 0.5f) * 0.04).toDouble(),
            )
            val lifetime = (8 + (random.nextFloat() * 8).toInt()).toInt()
            particles.add(
                DustParticle(
                    position = pos,
                    velocity = vel,
                    lifetime = lifetime,
                    maxLifetime = lifetime,
                    size = 0.02f + random.nextFloat() * 0.03f,
                )
            )
        }
    }

    private fun tickParticles() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.lifetime--
            if (p.lifetime <= 0) {
                iterator.remove()
                continue
            }
            p.position = p.position.add(p.velocity)
            p.velocity = p.velocity.add(0.0, -0.003, 0.0)
        }
    }

    /**
     * Render dust particles using RenderBuilder filledQuad.
     */
    fun RenderBuilder.render(cameraPos: Vec3d, intensity: Float) {
        if (particles.isEmpty()) return

        val alpha = intensity.coerceIn(0f, 1f)

        particles.forEach { p ->
            val progress = 1f - p.lifetime.toFloat() / p.maxLifetime.toFloat()
            val particleAlpha = ((1f - progress) * alpha * 200).toInt().coerceIn(0, 255)
            if (particleAlpha <= 0) return@forEach

            val color = Color(0xD4, 0xB8, 0x96, particleAlpha)
            val sz = p.size

            filledQuadGradient(
                p.position.x - sz.toDouble(), p.position.y - sz.toDouble(), p.position.z, color,
                p.position.x + sz.toDouble(), p.position.y - sz.toDouble(), p.position.z, color,
                p.position.x + sz.toDouble(), p.position.y + sz.toDouble(), p.position.z, color,
                p.position.x - sz.toDouble(), p.position.y + sz.toDouble(), p.position.z, color,
            )
        }
    }
}

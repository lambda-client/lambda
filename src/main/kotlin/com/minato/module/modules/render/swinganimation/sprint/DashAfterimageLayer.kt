@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.sprint

import com.minato.graphics.mc.RenderBuilder
import net.minecraft.util.math.Vec3d
import java.awt.Color

/**
 * DashAfterimageLayer — tạo bản sao mờ của player phía sau khi sprint.
 */
object DashAfterimageLayer {

    data class Ghost(
        val position: Vec3d,
        val rotationYaw: Float,
        val rotationPitch: Float,
        var lifetime: Int,
        val maxLifetime: Int,
    )

    private val ghosts = mutableListOf<Ghost>()
    private var spawnCounter = 0

    fun reset() {
        ghosts.clear()
        spawnCounter = 0
    }

    fun tick(
        position: Vec3d,
        yaw: Float,
        pitch: Float,
        intensity: Float,
        maxGhosts: Int,
        enabled: Boolean,
    ) {
        if (!enabled || intensity < 0.4f) {
            tickGhosts()
            return
        }

        spawnCounter++
        val interval = (6 - (intensity * 3)).toInt().coerceIn(2, 5)
        if (spawnCounter % interval == 0 && ghosts.size < maxGhosts) {
            val life = (10 + (intensity * 5).toInt()).toInt()
            ghosts.add(
                Ghost(
                    position = position,
                    rotationYaw = yaw,
                    rotationPitch = pitch,
                    lifetime = life,
                    maxLifetime = life,
                )
            )
        }

        tickGhosts()
    }

    private fun tickGhosts() {
        val iterator = ghosts.iterator()
        while (iterator.hasNext()) {
            val g = iterator.next()
            g.lifetime--
            if (g.lifetime <= 0) {
                iterator.remove()
            }
        }
    }

    /**
     * Render ghost afterimages using RenderBuilder filledQuad for silhouette.
     *
     * @param accentColor Optional theme-aware color; falls back to default blue if null.
     */
    fun RenderBuilder.render(cameraPos: Vec3d, intensity: Float, accentColor: Color? = null) {
        if (ghosts.isEmpty() || intensity < 0.01f) return

        ghosts.forEach { ghost ->
            val progress = 1f - ghost.lifetime.toFloat() / ghost.maxLifetime.toFloat()
            val alpha = ((1f - progress) * 0.3f * intensity * 255).toInt().coerceIn(0, 255)
            if (alpha <= 0) return@forEach

            val base = accentColor ?: Color(0x60, 0xC0, 0xFF)
            val color = Color(base.red, base.green, base.blue, alpha)

            val hw = 0.3
            val h = 1.8
            val box = net.minecraft.util.math.Box(
                ghost.position.x - hw, ghost.position.y, ghost.position.z - hw,
                ghost.position.x + hw, ghost.position.y + h, ghost.position.z + hw,
            )

            // Front face
            filledQuadGradient(
                box.minX, box.minY, box.minZ, color,
                box.maxX, box.minY, box.minZ, color,
                box.maxX, box.maxY, box.minZ, color,
                box.minX, box.maxY, box.minZ, color,
            )
            // Back face
            filledQuadGradient(
                box.minX, box.minY, box.maxZ, color,
                box.minX, box.maxY, box.maxZ, color,
                box.maxX, box.maxY, box.maxZ, color,
                box.maxX, box.minY, box.maxZ, color,
            )
        }
    }
}

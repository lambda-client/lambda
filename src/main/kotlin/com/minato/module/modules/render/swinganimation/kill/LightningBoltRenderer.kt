@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.kill

import com.minato.graphics.mc.RenderBuilder
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.max
import kotlin.random.Random

/**
 * LightningBoltRenderer — vẽ tia sét zigzag bằng line mesh.
 *
 * ### Nguyên lý
 * - Tạo seed ngẫu nhiên mỗi lần → mỗi tia sét trông khác nhau
 * - Chuỗi đoạn thẳng zigzag từ điểm cao (15 blocks trên kill) → kill position
 * - Core màu trắng-xanh `#DCEFFF` blend Additive
 * - Độ dày giảm dần theo độ cao
 * - Flash 120ms + fade 80ms
 * - KHÔNG spawn LightningEntity thật, KHÔNG gửi packet
 */
object LightningBoltRenderer {

    fun generateBolt(
        start: Vec3d,
        end: Vec3d,
        segments: Int = 9,
        seed: Long = System.nanoTime(),
        maxDeviation: Double = 2.5,
    ): List<Vec3d> {
        val rng = Random(seed)
        val direction = end.subtract(start)
        val points = mutableListOf(start)

        for (i in 1 until segments) {
            val t = i.toDouble() / segments
            val basePos = Vec3d(
                start.x + direction.x * t,
                start.y + direction.y * t,
                start.z + direction.z * t
            )
            val deviation = maxDeviation * (1.0 - t * 0.5) * (rng.nextDouble() - 0.5) * 2.0
            val angle = rng.nextDouble() * Math.PI * 2
            val offsetX = MathHelper.cos(angle) * deviation
            val offsetZ = MathHelper.sin(angle) * deviation
            points.add(basePos.add(offsetX, 0.0, offsetZ))
        }

        points.add(end)
        return points
    }

    fun generateForks(
        mainBolt: List<Vec3d>,
        branches: Int,
        seed: Long = System.nanoTime(),
    ): List<List<Vec3d>> {
        if (mainBolt.size < 3) return listOf(mainBolt)
        val rng = Random(seed)
        val forks = mutableListOf(mainBolt)

        for (b in 0 until branches.coerceAtMost(2)) {
            val forkPointIndex = 1 + rng.nextInt(max(1, mainBolt.size / 2))
            val forkStart = mainBolt[forkPointIndex]
            val forkEnd = mainBolt.last().add(
                (rng.nextDouble() - 0.5) * 2.0,
                0.0,
                (rng.nextDouble() - 0.5) * 2.0,
            )
            val forkSegments = max(3, mainBolt.size / 2)
            val fork = generateBolt(
                start = forkStart,
                end = forkEnd,
                segments = forkSegments,
                seed = seed + b + 1,
                maxDeviation = 1.5,
            )
            forks.add(fork)
        }

        return forks
    }

    /**
     * Render lightning bolt using RenderBuilder lineGradient.
     */
    fun RenderBuilder.renderBolt(
        bolts: List<List<Vec3d>>,
        cameraPos: Vec3d,
        alpha: Float,
        coreColorInt: Int? = null,
        glowColorInt: Int? = null,
    ) {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        fun colorFromIntWithAlpha(intColor: Int, alphaComponent: Int): Color {
            val c = Color(intColor, true)
            return Color(c.red, c.green, c.blue, alphaComponent.coerceIn(0, 255))
        }

        val coreColor = if (coreColorInt != null) colorFromIntWithAlpha(coreColorInt, a) else Color(0xDC, 0xEF, 0xFF, a)
        val glowColor = if (glowColorInt != null) colorFromIntWithAlpha(glowColorInt, (a * 0.5f).toInt()) else Color(0xDC, 0xEF, 0xFF, (a * 0.5f).toInt())

        bolts.forEach { bolt ->
            for (i in 0 until bolt.size - 1) {
                val p1 = bolt[i]
                val p2 = bolt[i + 1]

                val heightRatio = (p1.y / 15.0).coerceIn(0.0, 1.0)
                val thickness = (0.15 * (1.0 - heightRatio * 0.7)).coerceAtLeast(0.03).toFloat()

                // Core line
                val fadeA = (a * (1f - heightRatio.toFloat() * 0.5f)).toInt()
                lineGradient(
                    p1, coreColor,
                    p2, Color(0xDC, 0xEF, 0xFF, fadeA),
                    width = thickness,
                )

                // Glow line (wider, more transparent)
                if (a > 50) {
                    lineGradient(
                        p1, glowColor,
                        p2, Color(0xDC, 0xEF, 0xFF, (a * 0.3f).toInt()),
                        width = thickness * 2.5f,
                    )
                }
            }
        }
    }
}

@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.trail

import com.minato.graphics.mc.RenderBuilder
import com.minato.module.modules.render.swinganimation.ActiveSwingEffect
import com.minato.module.modules.render.swinganimation.WeaponType
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tạo ribbon mesh từ spline points cho weapon trail.
 * Mỗi điểm trên spline → 2 vertex (trái/phải) dọc theo tangent.
 *
 * Mesh được build mỗi frame từ [ActiveSwingEffect.tipHistory].
 *
 * ### Các layer (từ dưới lên)
 * 1. Glow (bloom giả lập) — 0-3 lớp phóng to dần
 * 2. Core trail — vệt chính
 * 3. Rim light — dải sáng 2 mép
 * 4. Spark particles — hạt bắn
 */
object TrailRibbonMesh {

    /**
     * Render ribbon trail với đầy đủ hiệu ứng (glow → core → rim → spark → chroma).
     */
    fun RenderBuilder.renderTrail(
        effect: ActiveSwingEffect,
        weaponType: WeaponType,
        cameraPos: Vec3d,
        width: Float = 0.15f,
        subdivisions: Int = 7,
        glowLayers: Int = 2,
        sparkCount: Int = 4,
        hasRimLight: Boolean = true,
        hasChromaPulse: Boolean = false,
    ) {
        val points = effect.tipHistory
        if (points.size < 2) return

        // LOD: skip nếu quá xa camera
        val cameraDist = points.first().distanceTo(cameraPos)
        if (cameraDist > 32.0) return
        val lodScale = (cameraDist / 32.0).coerceIn(0.0, 1.0)
        val actualSubdiv = maxOf(1, (subdivisions * (1.0 - lodScale * 0.5)).toInt())

        val splinePoints = CatmullRomSpline.buildSpline(points, actualSubdiv)
        if (splinePoints.size < 2) return

        val alpha = effect.alpha
        val colors = weaponType.defaultColors
        val baseColor = Color(colors.core, true)
        val glowColor = Color(colors.glow, true)

        // ── 1. Glow layers (bloom giả lập, render trước để nằm dưới) ──
        for (layer in 0 until glowLayers) {
            val glowScale = 1.3f + layer * 0.3f  // 1.3x, 1.6x, 1.9x
            val glowAlpha = (alpha * (0.25f - layer * 0.06f)).coerceAtLeast(0.02f)
            val gA = (glowAlpha * 255).toInt().coerceIn(0, 255)
            val gColor = Color(
                glowColor.red, glowColor.green, glowColor.blue, gA
            )

            for (i in 0 until splinePoints.size - 1) {
                val p1 = splinePoints[i]
                val p2 = splinePoints[i + 1]
                val t = i.toFloat() / splinePoints.size
                val segWidth = (1f - t) * width * glowScale * alpha

                val fadeA = ((1f - t) * gA).toInt().coerceIn(0, 255)
                val fadeColor = Color(glowColor.red, glowColor.green, glowColor.blue, fadeA)

                lineGradient(
                    p1, gColor,
                    p2, fadeColor,
                    width = segWidth.coerceAtLeast(0.002f),
                )
            }
        }

        // ── 2. Core trail ──
        for (i in 0 until splinePoints.size - 1) {
            val p1 = splinePoints[i]
            val p2 = splinePoints[i + 1]
            val t = i.toFloat() / splinePoints.size

            val segWidth = (1f - t) * width * alpha
            val startA = (alpha * 255).toInt().coerceIn(0, 255)
            val endA = ((1f - t) * alpha * 255).toInt().coerceAtLeast(0).coerceAtMost(255)

            val c1 = Color(baseColor.red, baseColor.green, baseColor.blue, startA)
            val c2 = Color(
                (baseColor.red * (1f - t * 0.3f)).toInt().coerceIn(0, 255),
                (baseColor.green * (1f - t * 0.3f)).toInt().coerceIn(0, 255),
                (baseColor.blue * (1f - t * 0.2f)).toInt().coerceIn(0, 255),
                endA,
            )

            lineGradient(
                p1, c1,
                p2, c2,
                width = segWidth.coerceAtLeast(0.001f),
            )
        }

        // ── 3. Rim light (dải sáng mép) ──
        if (hasRimLight && alpha > 0.3f) {
            val rimA = (alpha * 255 * 1.4f).toInt().coerceIn(0, 255)
            val rimColor = Color(
                minOf(255, (baseColor.red * 1.4f).toInt()),
                minOf(255, (baseColor.green * 1.4f).toInt()),
                minOf(255, (baseColor.blue * 1.4f).toInt()),
                rimA,
            )

            for (i in 0 until splinePoints.size - 1) {
                val p1 = splinePoints[i]
                val p2 = splinePoints[i + 1]
                val t = i.toFloat() / splinePoints.size
                val rimWidth = (1f - t) * width * alpha * 0.12f
                val fadeRimA = ((1f - t) * rimA).toInt().coerceIn(0, 255)
                val rimFade = Color(rimColor.red, rimColor.green, rimColor.blue, fadeRimA)

                lineGradient(
                    p1, rimColor,
                    p2, rimFade,
                    width = rimWidth.coerceAtLeast(0.001f),
                )
            }
        }

        // ── 4. Spark particles ──
        if (sparkCount > 0 && alpha > 0.5f) {
            val sparkA = (alpha * 255).toInt().coerceIn(0, 255)
            val sparkColor = Color(colors.spark, true)
            val sparkCol = Color(sparkColor.red, sparkColor.green, sparkColor.blue, sparkA)

            val sparkEnd = minOf(splinePoints.size, 3)
            for (i in 0 until sparkEnd) {
                val base = splinePoints[i]
                val sz = 0.025f

                filledQuadGradient(
                    base.x - sz, base.y - sz, base.z, sparkCol,
                    base.x + sz, base.y - sz, base.z, sparkCol,
                    base.x + sz, base.y + sz, base.z, sparkCol,
                    base.x - sz, base.y + sz, base.z, sparkCol,
                )
            }
        }

        // ── 5. Chroma Pulse (critical hit) — pulse vàng-trắng 60ms ──
        if (hasChromaPulse && alpha > 0.5f) {
            val progress = effect.progress
            // Pulse position sweeps from trail tip (0) to base (1) over the swing duration
            val pulsePos = progress.coerceIn(0f, 1f)
            val pulseWidth = width * 2.5f
            val pulseAlpha = (alpha * 255 * (1f - pulsePos * 0.5f)).toInt().coerceIn(0, 255)
            val pulseColor = Color(0xFF, 0xDD, 0x44, pulseAlpha)

            if (splinePoints.size >= 2) {
                val idx = (pulsePos * (splinePoints.size - 2)).toInt().coerceIn(0, splinePoints.size - 2)
                val p1 = splinePoints[idx]
                val p2 = splinePoints[(idx + 1).coerceAtMost(splinePoints.size - 1)]

                // Vòng sáng vàng tại vị trí pulse
                filledQuadGradient(
                    p1.x - pulseWidth.toDouble(), p1.y - pulseWidth.toDouble(), p1.z, pulseColor,
                    p1.x + pulseWidth.toDouble(), p1.y - pulseWidth.toDouble(), p1.z, pulseColor,
                    p1.x + pulseWidth.toDouble(), p1.y + pulseWidth.toDouble(), p1.z, pulseColor,
                    p1.x - pulseWidth.toDouble(), p1.y + pulseWidth.toDouble(), p1.z, pulseColor,
                )
            }
        }
    }

    /**
     * Render ring-wave (dành cho Mace smash) — tối ưu allocation.
     * Dùng reusable pre-allocated arrays thay vì tạo list mới mỗi frame.
     */
    fun RenderBuilder.renderRingWave(
        center: Vec3d,
        cameraPos: Vec3d,
        progress: Float,
        alpha: Float,
        segments: Int = 24,
        isFallSmash: Boolean = false,
    ) {
        if (alpha <= 0f) return

        val maxRadius = if (isFallSmash) 2.5f else 1.0f
        val radius = progress * maxRadius
        val ringAlpha = (1f - progress) * alpha

        val angleStep = (Math.PI.toFloat() * 2f) / segments
        // Pre-allocate arrays instead of mutable lists
        val ox = DoubleArray(segments)
        val oz = DoubleArray(segments)

        for (i in 0 until segments) {
            val angle = i * angleStep
            ox[i] = cos(angle).toDouble()
            oz[i] = sin(angle).toDouble()
        }

        val glowA = (ringAlpha * 0.4f * 255).toInt().coerceIn(0, 255)
        val coreA = (ringAlpha * 255).toInt().coerceIn(0, 255)
        val glowColor = Color(0xD4, 0xB8, 0xFF, glowA)
        val coreColor = Color(0xB9, 0x8C, 0xFF, coreA)

        val glowRadius = radius * 1.3f
        val innerRadius = radius * 0.85f
        val centerY = center.y

        // Outer glow ring (từ outer → core)
        for (i in 0 until segments) {
            val next = (i + 1) % segments
            filledQuadGradient(
                center.x + ox[i] * glowRadius, centerY - 0.02 + (progress * 0.04).toDouble(), center.z + oz[i] * glowRadius, glowColor,
                center.x + ox[next] * glowRadius, centerY - 0.02 + (progress * 0.04).toDouble(), center.z + oz[next] * glowRadius, glowColor,
                center.x + ox[next] * radius, centerY, center.z + oz[next] * radius, glowColor,
                center.x + ox[i] * radius, centerY, center.z + oz[i] * radius, glowColor,
            )
        }

        // Core ring (từ core → inner)
        for (i in 0 until segments) {
            val next = (i + 1) % segments
            filledQuadGradient(
                center.x + ox[i] * radius, centerY, center.z + oz[i] * radius, coreColor,
                center.x + ox[next] * radius, centerY, center.z + oz[next] * radius, coreColor,
                center.x + ox[next] * innerRadius, centerY + 0.02, center.z + oz[next] * innerRadius, coreColor,
                center.x + ox[i] * innerRadius, centerY + 0.02, center.z + oz[i] * innerRadius, coreColor,
            )
        }

        // Fall Smash: extra impact ring
        if (isFallSmash && progress < 0.8f) {
            val impactA = ((1f - progress) * ringAlpha * 0.6f * 255).toInt().coerceIn(0, 255)
            val impactColor = Color(0xFF, 0xFF, 0xFF, impactA)
            val impactRadius = radius * 1.6f
            val outerImpactColor = Color(0xFF, 0xFF, 0xFF, impactA / 2)

            for (i in 0 until segments) {
                val next = (i + 1) % segments
                filledQuadGradient(
                    center.x + ox[i] * glowRadius, centerY - 0.02 + (progress * 0.04).toDouble(), center.z + oz[i] * glowRadius, outerImpactColor,
                    center.x + ox[next] * glowRadius, centerY - 0.02 + (progress * 0.04).toDouble(), center.z + oz[next] * glowRadius, outerImpactColor,
                    center.x + ox[next] * impactRadius, centerY - 0.05, center.z + oz[next] * impactRadius, impactColor,
                    center.x + ox[i] * impactRadius, centerY - 0.05, center.z + oz[i] * impactRadius, impactColor,
                )
            }
        }

        // Spark particles for Fall Smash (sử dụng inline calculation, không tạo Vec3d)
        if (isFallSmash && progress < 0.6f) {
            val sparkCount = (6 * (1f - progress)).toInt().coerceIn(2, 6)
            for (i in 0 until sparkCount) {
                val sparkAngle = (i.toFloat() / sparkCount) * Math.PI.toFloat() * 2f
                val sparkDist = radius * (0.8f + (i % 3) * 0.1f)
                val sx = center.x + (cos(sparkAngle) * sparkDist).toDouble()
                val sy = center.y + 0.1 + (progress * 0.3f).toDouble()
                val sz = center.z + (sin(sparkAngle) * sparkDist).toDouble()
                val sA = ((1f - progress) * ringAlpha * 255).toInt().coerceIn(0, 255)
                val sColor = Color(0xFF, 0xFF, 0xB0, sA)
                val s = 0.03
                filledQuadGradient(
                    sx - s, sy - s, sz, sColor,
                    sx + s, sy - s, sz, sColor,
                    sx + s, sy + s, sz, sColor,
                    sx - s, sy + s, sz, sColor,
                )
            }
        }
    }

    /**
     * Render trident dual trail (2 trail song song) + glow.
     */
    fun RenderBuilder.renderTridentTrail(
        effect: ActiveSwingEffect,
        cameraPos: Vec3d,
        subdivisions: Int = 7,
    ) {
        val points = effect.tipHistory
        if (points.size < 2) return

        val baseTrail = CatmullRomSpline.buildSpline(points, subdivisions)
        val size = baseTrail.size
        if (size < 2) return

        val alpha = effect.alpha
        val ox = 0.2
        val oz = 0.2

        // Glow pass (wider, more transparent)
        for (i in 0 until size - 1) {
            val p1 = baseTrail[i]
            val p2 = baseTrail[i + 1]
            val t = i.toFloat() / size
            val segAlpha = ((1f - t) * alpha * 80).toInt().coerceIn(0, 255)
            if (segAlpha <= 0) continue
            val glowColor = Color(0x1E, 0x90, 0xFF, segAlpha / 3)

            lineGradient(
                p1.x - ox, p1.y, p1.z - oz, glowColor,
                p2.x - ox, p2.y, p2.z - oz, glowColor,
                width = 0.12f,
            )
            lineGradient(
                p1.x + ox, p1.y, p1.z + oz, glowColor,
                p2.x + ox, p2.y, p2.z + oz, glowColor,
                width = 0.12f,
            )
        }

        // Core trails
        for (i in 0 until size - 1) {
            val p1 = baseTrail[i]
            val p2 = baseTrail[i + 1]
            val t = i.toFloat() / size
            val segAlpha = ((1f - t) * alpha * 200).toInt().coerceIn(0, 255)
            val color = Color(0x1E, 0x90, 0xFF, segAlpha)
            val fadeColor = Color(0x1E, 0x90, 0xFF, (segAlpha * 0.5f).toInt())

            lineGradient(
                p1.x - ox, p1.y, p1.z - oz, color,
                p2.x - ox, p2.y, p2.z - oz, fadeColor,
                width = 0.04f,
            )
            lineGradient(
                p1.x + ox, p1.y, p1.z + oz, color,
                p2.x + ox, p2.y, p2.z + oz, fadeColor,
                width = 0.04f,
            )
        }
    }

    /**
     * Render Spear Spin Slash (vòng tròn khép kín).
     */
    fun RenderBuilder.renderSpearSpinSlash(
        center: Vec3d,
        cameraPos: Vec3d,
        progress: Float,
        radius: Float = 1.6f,
        segments: Int = 36,
    ) {
        val startAngle = progress * 2.0 * Math.PI
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val a = (alpha * 200).toInt().coerceIn(0, 255)
        val color = Color(0x40, 0xB0, 0x70, a)

        val angleStep = (Math.PI.toFloat() * 2f) / segments
        val outerVerts = mutableListOf<Vec3d>()
        val innerVerts = mutableListOf<Vec3d>()

        for (i in 0 until segments) {
            val angle = startAngle.toFloat() + i * angleStep
            val cosA = cos(angle)
            val sinA = sin(angle)
            val rise = sin(-startAngle.toFloat()) * 0.3f * progress

            outerVerts.add(Vec3d(
                center.x + (cosA * radius).toDouble(),
                center.y + rise.toDouble(),
                center.z + (sinA * radius).toDouble()
            ))
            innerVerts.add(Vec3d(
                center.x + (cosA * radius * 0.85f).toDouble(),
                center.y + 0.05 + rise.toDouble(),
                center.z + (sinA * radius * 0.85f).toDouble()
            ))
        }

        for (i in 0 until segments) {
            val next = (i + 1) % segments
            filledQuadGradient(
                outerVerts[i].x, outerVerts[i].y, outerVerts[i].z, color,
                outerVerts[next].x, outerVerts[next].y, outerVerts[next].z, color,
                innerVerts[next].x, innerVerts[next].y, innerVerts[next].z, color,
                innerVerts[i].x, innerVerts[i].y, innerVerts[i].z, color,
            )
        }
    }
}

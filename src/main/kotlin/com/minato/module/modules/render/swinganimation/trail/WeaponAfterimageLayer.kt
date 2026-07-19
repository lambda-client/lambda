@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.trail

import com.minato.graphics.mc.RenderBuilder
import com.minato.module.modules.render.swinganimation.ActiveSwingEffect
import com.minato.module.modules.render.swinganimation.WeaponType
import net.minecraft.util.math.Vec3d
import java.awt.Color

/**
 * WeaponAfterimageLayer — tạo bản sao mờ của model vũ khí dọc theo trail.
 *
 * Render box silhouette nhỏ tại các điểm gần nhất trong [ActiveSwingEffect.tipHistory],
 * tạo hiệu ứng "ghost weapon" phía sau đòn đánh.
 *
 * ### Kích thước box theo vũ khí
 * - Sword: 0.3×0.8×0.1 (dài, hẹp)
 * - Axe: 0.35×0.7×0.15 (rộng hơn)
 * - Trident: 0.25×1.0×0.25 (dài)
 * - Spear: 0.2×0.9×0.2 (mảnh, dài)
 * - Fist: 0.2×0.3×0.2 (nhỏ)
 */
object WeaponAfterimageLayer {

    data class AfterimageConfig(
        val halfWidth: Double = 0.3,
        val height: Double = 0.8,
        val halfDepth: Double = 0.1,
    )

    private val weaponConfigs = mapOf(
        WeaponType.SWORD to AfterimageConfig(0.3, 0.8, 0.1),
        WeaponType.AXE to AfterimageConfig(0.35, 0.7, 0.15),
        WeaponType.TRIDENT to AfterimageConfig(0.25, 1.0, 0.25),
        WeaponType.SPEAR to AfterimageConfig(0.2, 0.9, 0.2),
        WeaponType.FIST to AfterimageConfig(0.2, 0.3, 0.2),
    )

    private fun getConfig(type: WeaponType): AfterimageConfig =
        weaponConfigs[type] ?: AfterimageConfig(0.3, 0.7, 0.15)

    /**
     * Render afterimage ghosts dọc theo trail của effect.
     *
     * @param effect Swing effect đang active
     * @param afterimageCount Số lượng ghost tối đa (theo Quality Preset)
     * @param width Trail width scale
     */
    fun RenderBuilder.render(
        effect: ActiveSwingEffect,
        cameraPos: Vec3d,
        afterimageCount: Int,
        width: Float,
    ) {
        val points = effect.tipHistory
        if (points.size < 3 || afterimageCount <= 0) return

        val alpha = effect.alpha
        if (alpha < 0.3f) return

        val config = getConfig(effect.context.weaponType)
        val sampleCount = minOf(afterimageCount, points.size - 1)

        for (i in 0 until sampleCount) {
            // Lấy điểm cách đều nhau dọc trail, ưu tiên điểm mới nhất
            val idx = points.size - 1 - i
            if (idx < 0) break

            val pos = points[idx]
            val ghostProgress = i.toFloat() / sampleCount.coerceAtLeast(1)
            val ghostAlpha = (alpha * (1f - ghostProgress) * 0.25f * 255).toInt().coerceIn(0, 255)
            if (ghostAlpha <= 0) continue

            val color = Color(0x60, 0xC0, 0xFF, ghostAlpha)
            val hw = config.halfWidth * width * 3
            val hd = config.halfDepth * width * 3
            val h = config.height * width * 3

            val minX = pos.x - hw
            val maxX = pos.x + hw
            val minY = pos.y - h * 0.3
            val maxY = minY + h
            val minZ = pos.z - hd
            val maxZ = pos.z + hd

            // Front face
            filledQuadGradient(
                minX, minY, minZ, color,
                maxX, minY, minZ, color,
                maxX, maxY, minZ, color,
                minX, maxY, minZ, color,
            )
            // Back face
            filledQuadGradient(
                minX, minY, maxZ, color,
                minX, maxY, maxZ, color,
                maxX, maxY, maxZ, color,
                maxX, minY, maxZ, color,
            )
        }
    }
}

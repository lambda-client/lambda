@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation

/**
 * Quality preset cho Swing Animation Module.
 * Điều chỉnh số lượng detail vs performance.
 *
 * | Thông số | LOW | MEDIUM | HIGH | ULTRA |
 * |---|---|---|---|---|
 * | Điểm trail history | 6 | 8 | 12 | 16 |
 * | Sub-segment/đoạn spline | 3 | 5 | 7 | 10 |
 * | Lớp Outer Glow | 1 | 2 | 3 | 3 |
 * | Spark/vung | 4 | 10 | 18 | 28 |
 * | Afterimage bản sao | 0 | 3 | 5 | 8 |
 * | Rim Light | tắt | bật | bật | bật (2 lớp) |
 * | Chroma Pulse | tắt | tắt | bật | bật |
 * | Lightning bolt segments (Kill Effect) | 4 | 6 | 9 | 12 |
 * | Soul particles/kill | 6 | 12 | 20 | 30 |
 * | Max effects đồng thời | 8 | 14 | 20 | 30 |
 */
enum class QualityPreset(
    val historyPoints: Int,
    val subdivisions: Int,
    val glowLayers: Int,
    val sparkCount: Int,
    val afterimageCount: Int,
    val hasRimLight: Boolean,
    val hasChromaPulse: Boolean,
    val lightningSegments: Int,
    val soulParticles: Int,
    val maxConcurrentEffects: Int,
) {
    LOW(4, 2, 0, 2, 0, false, false, 3, 4, 5),
    MEDIUM(8, 5, 2, 10, 3, true, false, 6, 12, 14),
    HIGH(12, 7, 3, 18, 5, true, true, 9, 20, 20),
    ULTRA(16, 10, 3, 28, 8, true, true, 12, 30, 30);
}

@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation

/**
 * Phân loại vũ khí cho hệ thống Swing Animation.
 * Mỗi loại có cách render trail và màu sắc riêng.
 */
enum class WeaponType {
    SWORD,
    AXE,
    MACE,
    TRIDENT,
    SPEAR,
    BOW,
    CROSSBOW,
    FIST;

    val isMelee get() = this in listOf(SWORD, AXE, MACE, TRIDENT, SPEAR, FIST)
    val isRanged get() = this in listOf(BOW, CROSSBOW)
    val hasTrail get() = this in listOf(SWORD, AXE, TRIDENT, SPEAR, FIST)
    val usesRingWave get() = this == MACE

    /**
     * Bảng màu mặc định cho trail theo từng vũ khí.
     * @see WeaponColorScheme
     */
    val defaultColors: WeaponColorScheme
        get() = when (this) {
            SWORD -> WeaponColorScheme(0xFFFFFFFF.toInt(), 0xFFBEEFFF.toInt(), 0xFFDFF7FF.toInt())
            AXE -> WeaponColorScheme(0xFFFFE3C4.toInt(), 0xFFFF7A29.toInt(), 0xFFFFB066.toInt())
            MACE -> WeaponColorScheme(0xFFFFFFFF.toInt(), 0xFFB98CFF.toInt(), 0xFF8A8D91.toInt())
            TRIDENT -> WeaponColorScheme(0xFFEAF6FF.toInt(), 0xFF1E90FF.toInt(), 0xFF7FD4FF.toInt())
            SPEAR -> WeaponColorScheme(0xFFEAFFF2.toInt(), 0xFF145A32.toInt(), 0xFF8CF5B0.toInt())
            BOW -> WeaponColorScheme(0xFFFFFFFF.toInt(), 0xFF00FFFF.toInt(), 0xFFB3FFFF.toInt())
            CROSSBOW -> WeaponColorScheme(0xFFFFF6D9.toInt(), 0xFFD4AF37.toInt(), 0xFFFFE58A.toInt())
            FIST -> WeaponColorScheme(0x66FFFFFF.toInt(), 0x22FFFFFF.toInt(), 0x00FFFFFF.toInt())
        }
}

/**
 * Color scheme cho trail effect.
 * @param core Màu chính (đầu gradient, sáng nhất)
 * @param glow Màu glow/bloom
 * @param spark Màu spark particles
 */
data class WeaponColorScheme(
    val core: Int,
    val glow: Int,
    val spark: Int,
)

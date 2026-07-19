package com.minato.module.hud

import java.awt.Color

/**
 * HudTheme — theme-aware color palette cho HUD system.
 *
 * Inspired by PVPUtils theme system:
 * - LIGHT theme: dark text on light background
 * - DARK theme: light text on dark background
 *
 * ### Usage
 * ```kotlin
 * val theme = HudTheme.current
 * val textColor = theme.primaryTextColor
 * ```
 */
enum class HudTheme(
    /** Display name */
    val displayName: String,
) {
    DARK("Dark"),
    LIGHT("Light");

    companion object {
        /** Global theme instance — can be set via config */
        var current: HudTheme = DARK
            private set

        /** Set theme and return new value */
        fun set(theme: HudTheme): HudTheme {
            current = theme
            return current
        }

        /** Toggle between LIGHT and DARK */
        fun toggle(): HudTheme {
            current = if (current == DARK) LIGHT else DARK
            return current
        }
    }

    // ── Text Colors ──

    /**
     * Primary text color (highest emphasis).
     * - LIGHT: near-black (#111827)
     * - DARK: white (#FFFFFF)
     */
    val primaryTextColor: Color
        get() = when (this) {
            DARK -> Color(0xFF, 0xFF, 0xFF)
            LIGHT -> Color(0x11, 0x18, 0x27)
        }

    /**
     * Primary text color as ARGB int.
     */
    val primaryTextColorInt: Int
        get() = when (this) {
            DARK -> 0xFFFFFFFF.toInt()
            LIGHT -> 0xFF111827.toInt()
        }

    /**
     * Secondary text color (medium emphasis).
     * - LIGHT: 67% black (#AA111827)
     * - DARK: 80% white (#CCFFFFFF)
     */
    val secondaryTextColor: Color
        get() = when (this) {
            DARK -> Color(255, 255, 255, 204)  // 80% white
            LIGHT -> Color(17, 24, 39, 170)     // 67% black
        }

    /**
     * Secondary text color as ARGB int.
     */
    val secondaryTextColorInt: Int
        get() = when (this) {
            DARK -> 0xCCFFFFFF.toInt()
            LIGHT -> 0xAA111827.toInt()
        }

    /**
     * Muted text color (low emphasis, descriptions).
     * - LIGHT: 67% muted purple-gray (#AA5C5870)
     * - DARK: 75% white (#BFFFFFFF)
     */
    val mutedTextColor: Color
        get() = when (this) {
            DARK -> Color(255, 255, 255, 191)  // 75% white
            LIGHT -> Color(92, 88, 112, 170)    // 67% muted
        }

    /**
     * Muted text color as ARGB int.
     */
    val mutedTextColorInt: Int
        get() = when (this) {
            DARK -> 0xBFFFFFFF.toInt()
            LIGHT -> 0xAA5C5870.toInt()
        }

    // ── Background Colors ──

    /**
     * Background color for HUD elements (semi-transparent).
     * - LIGHT: white with 20% opacity (#33FFFFFF)
     * - DARK: near-black with 40% opacity (#66111827)
     */
    val backgroundColor: Color
        get() = when (this) {
            DARK -> Color(17, 24, 39, 102)    // 40% dark
            LIGHT -> Color(255, 255, 255, 51)  // 20% white
        }

    /**
     * Stronger background for HUD elements.
     * - LIGHT: white (#F8FAFC) with 40% opacity
     * - DARK: dark blue (#111827) with 40% opacity
     */
    val hudBackgroundColor: Color
        get() = when (this) {
            DARK -> Color(17, 24, 39, 102)
            LIGHT -> Color(248, 250, 252, 102)
        }

    /**
     * Background color as ARGB int.
     */
    val hudBackgroundColorInt: Int
        get() = when (this) {
            DARK -> 0x66111827.toInt()
            LIGHT -> 0x66F8FAFC.toInt()
        }

    // ── Border Colors ──

    /**
     * Border color for HUD elements.
     * - LIGHT: black 33% (#55111827)
     * - DARK: white 33% (#55FFFFFF)
     */
    val borderColor: Color
        get() = when (this) {
            DARK -> Color(255, 255, 255, 85)   // 33% white
            LIGHT -> Color(17, 24, 39, 85)     // 33% black
        }

    /**
     * Border color as ARGB int.
     */
    val borderColorInt: Int
        get() = when (this) {
            DARK -> 0x55FFFFFF.toInt()
            LIGHT -> 0x55111827.toInt()
        }

    // ── Utility Colors ──

    /**
     * Accent/highlight color (themed).
     * - LIGHT: blue (#1E90FF)
     * - DARK: light blue (#60C0FF)
     */
    val accentColor: Color
        get() = when (this) {
            DARK -> Color(0x60, 0xC0, 0xFF)
            LIGHT -> Color(0x1E, 0x90, 0xFF)
        }

    /**
     * Success/positive color.
     */
    val successColor: Color
        get() = when (this) {
            DARK -> Color(0x40, 0xD0, 0x70)
            LIGHT -> Color(0x20, 0x90, 0x50)
        }

    /**
     * Warning color.
     */
    val warningColor: Color
        get() = when (this) {
            DARK -> Color(0xFF, 0xD7, 0x00)
            LIGHT -> Color(0xCC, 0xAA, 0x00)
        }

    /**
     * Danger/error color.
     */
    val dangerColor: Color
        get() = when (this) {
            DARK -> Color(0xFF, 0x44, 0x44)
            LIGHT -> Color(0xDD, 0x33, 0x33)
        }

    // ── Health Bar Colors ──

    /**
     * Health bar color based on ratio (0.0 = dead, 1.0 = full).
     */
    fun healthBarColor(ratio: Float): Color = when {
        ratio >= 0.75f -> Color(0, 200, 0)        // Green
        ratio >= 0.4f -> Color(240, 200, 0)       // Yellow
        else -> Color(220, 40, 40)                 // Red
    }

    /**
     * Health bar color as ARGB int based on ratio.
     */
    fun healthBarColorInt(ratio: Float): Int = when {
        ratio >= 0.75f -> 0xFF00C800.toInt()
        ratio >= 0.4f -> 0xFFF0C800.toInt()
        else -> 0xFFDC2828.toInt()
    }
}

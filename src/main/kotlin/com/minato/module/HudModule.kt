
package com.minato.module

import com.minato.config.settings.complex.Bind
import com.minato.gui.Layout
import com.minato.module.hud.HudTheme
import com.minato.module.tag.ModuleTag
import java.awt.Color

/**
 * Base class for all HUD modules with theme-aware color support and position/scale configuration.
 *
 * Each HUD module inherits:
 * - [hudX], [hudY]: Position on screen (normalized 0..1 or pixel offset)
 * - [hudScale]: Scale multiplier for the HUD element
 * - [theme]: HudTheme setting (LIGHT/DARK) — syncs with global [HudTheme.current]
 * - [backgroundColor]: Custom background color (per-module)
 * - [useThemeColors]: Toggle to use theme colors vs custom colors
 * - [useThemeBackground]: Toggle to use theme background vs custom background
 */
abstract class HudModule(
    name: String,
    description: String = "",
    tag: ModuleTag,
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: Bind = Bind.EMPTY,
) : Module(name, description, tag, alwaysListening, enabledByDefault, defaultKeybind = defaultKeybind), Layout {

    // ── Position & Scale ──

    /** Horizontal position (pixel offset from left). Updated by drag. */
    val hudX = setting("X Position", 0f, -9999f..9999f, 1f)

    /** Vertical position (pixel offset from top). Updated by drag. */
    val hudY = setting("Y Position", 0f, -9999f..9999f, 1f)

    /** Scale multiplier (0.5x - 3.0x). */
    val hudScale = setting("Scale", 1.0f, 0.25f..3.0f, 0.05f)

    /** Background color cho HUD element */
    val backgroundColor = setting("Background Color", Color(0, 0, 0, 0))

    /**
     * Theme selection — syncs with global HudTheme.current.
     * Changing this setting updates the global theme.
     */
    val hudTheme = setting("HUD Theme", HudTheme.DARK)

    /**
     * Use theme-aware colors instead of hardcoded colors.
     * When enabled, text/background colors follow the selected [hudTheme].
     */
    val useThemeColors = setting("Use Theme Colors", true)

    /**
     * Use theme background (semi-transparent) instead of solid background.
     */
    val useThemeBackground = setting("Use Theme Background", false)

    /**
     * Effective theme — syncs `hudTheme` setting with global HudTheme.
     * Call this in buildLayout() to get the active theme colors.
     */
    val effectiveTheme: HudTheme
        get() {
            val theme = hudTheme.value
            // Sync with global HudTheme
            HudTheme.set(theme)
            return theme
        }

    /**
     * Get primary text color based on current theme settings.
     * Falls back to light gray (#DCDCDC) when theme colors are disabled.
     */
    val primaryTextColor: Color
        get() = if (useThemeColors.value) effectiveTheme.primaryTextColor else Color(220, 220, 220)

    /**
     * Get secondary text color based on current theme settings.
     * Falls back to medium gray (#C8C8C8) when theme colors are disabled.
     */
    val secondaryTextColor: Color
        get() = if (useThemeColors.value) effectiveTheme.secondaryTextColor else Color(200, 200, 200, 230)

    // When setting hudTheme, update global HudTheme.current
    init {
        // Sync theme on load via listener
        onEnable {
            HudTheme.set(hudTheme.value)
        }
    }
}

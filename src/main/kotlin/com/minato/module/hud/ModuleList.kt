
package com.minato.module.hud

import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.flag.ImGuiCol
import com.minato.module.HudModule
import com.minato.module.ModuleRegistry
import com.minato.module.tag.ModuleTag
import java.awt.Color

@Suppress("unused")
object ModuleList : HudModule(
    name = "ModuleList",
    tag = ModuleTag.HUD,
) {
	val onlyBound by setting("Only Bound", false, "Only displays modules with a keybind")
	val showKeybind by setting("Show Keybind", true, "Display keybind next to a module")

    init {
        drawSetting.value = false
    }

    override fun ImGuiBuilder.buildLayout() {
        val enabled = ModuleRegistry.modules.filter { it.isEnabled && it.draw }

        val theme = effectiveTheme
        val useThemeCol = useThemeColors.value
        val useThemeBg = useThemeBackground.value
        val textColor = if (useThemeCol) theme.primaryTextColor else Color(220, 220, 220)
        val bg = if (useThemeBg) theme.backgroundColor else backgroundColor.value
        val fallbackBorder = if (useThemeBg) theme.borderColor else Color(0, 0, 0, 60)

        val lineHeight = frameHeightWithSpacing
        val maxLines = enabled.size.coerceAtMost(30)
        val width = 160f
        val height = maxOf(20f, lineHeight * maxLines + style.framePadding.y * 2)

        hudBackground(width, height, bg, fallbackBorder) {
            enabled.forEach {
                val bound = it.keybind.key != 0 || it.keybind.mouse != -1
                if (onlyBound && !bound) return@forEach

                textColored(it.name, textColor)

                if (showKeybind) {
                    val keyColor = if (!bound) Color.RED else Color.GREEN
                    sameLine()
                    withStyleColor(ImGuiCol.Text, keyColor) { text(" [${it.keybind.name}]") }
                }
            }

            cursorPosY += height
        }
    }
}

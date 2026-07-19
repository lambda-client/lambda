
package com.minato.module.hud

import com.minato.gui.dsl.ImGuiBuilder
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.task.RootTask
import java.awt.Color

@Suppress("unused")
object TaskFlowHud : HudModule(
    name = "TaskFlowHud",
    tag = ModuleTag.HUD,
) {
    override fun ImGuiBuilder.buildLayout() {
        val theme = effectiveTheme
        val useThemeCol = useThemeColors.value
        val useThemeBg = useThemeBackground.value
        val textColor = if (useThemeCol) theme.primaryTextColor else Color(220, 220, 220)
        val bg = if (useThemeBg) theme.backgroundColor else backgroundColor.value
        val fallbackBorder = if (useThemeBg) theme.borderColor else Color(0, 0, 0, 60)

        val width = 300f
        val height = frameHeightWithSpacing + style.framePadding.y * 2

        hudBackground(width, height, bg, fallbackBorder) {
            textColored(RootTask.toString(), textColor)
            cursorPosY += height
        }
    }
}

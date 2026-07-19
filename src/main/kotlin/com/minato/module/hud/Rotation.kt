
package com.minato.module.hud

import com.minato.config.ConfigEditor.editSetting
import com.minato.config.blocks.FormatterSettings
import com.minato.config.withEdits
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.FormattingUtils.format
import java.awt.Color

@Suppress("unused")
object Rotation : HudModule(
	name = "Rotation",
	description = "Show your rotation",
	tag = ModuleTag.HUD,
) {
	private val formatter by configBlock(FormatterSettings(this))
		.withEdits {
			::timeFormat.editSetting { hide() }
		}

	override fun ImGuiBuilder.buildLayout() {
		runSafe {
			val rotation = player.rotationClient.format(formatter)

			val theme = effectiveTheme
			val useThemeCol = useThemeColors.value
			val useThemeBg = useThemeBackground.value
			val textColor = if (useThemeCol) theme.primaryTextColor else Color(220, 220, 220)
			val bg = if (useThemeBg) theme.backgroundColor else backgroundColor.value
			val fallbackBorder = if (useThemeBg) theme.borderColor else Color(0, 0, 0, 60)

			val width = 160f
			val height = frameHeightWithSpacing + style.framePadding.y * 2

			hudBackground(width, height, bg, fallbackBorder) {
				textColored(rotation, textColor)
				cursorPosY += height
			}
		}
	}
}

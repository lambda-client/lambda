
package com.minato.module.hud

import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImVec2
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.util.FormattingUtils.format
import com.minato.util.ServerTPSUtils
import com.minato.util.ServerTPSUtils.recentData
import java.awt.Color

@Suppress("unused")
object Tps : HudModule(
	name = "TPS",
	description = "Display the server's tick rate",
	tag = ModuleTag.HUD,
) {
	private val format by setting("Tick format", ServerTPSUtils.TickFormat.Tps)
	private val showGraph by setting("Show TPS Graph", false)
	private val graphHeight by setting("Graph Height", 40f, 10f..200f, 1f)
	private val graphWidth by setting("Graph Width", 200f, 10f..500f, 1f)
	private val graphStride by setting("Graph Stride", 1, 1..20, 1)

	override fun ImGuiBuilder.buildLayout() {
		val theme = effectiveTheme
		val useThemeCol = useThemeColors.value
		val useThemeBg = useThemeBackground.value
		val textColor = if (useThemeCol) theme.primaryTextColor else Color(220, 220, 220)
		val bg = if (useThemeBg) theme.backgroundColor else backgroundColor.value
		val fallbackBorder = if (useThemeBg) theme.borderColor else Color(0, 0, 0, 60)

		val data = recentData(format)
		if (data.isEmpty()) {
			val width = 160f
			val height = frameHeightWithSpacing + style.framePadding.y * 2
			hudBackground(width, height, bg, fallbackBorder) {
				textColored("No ${format.displayName} data yet", textColor)
				cursorPosY += height
			}
			return
		}
		val current = data.last()
		val avg = data.average().toFloat()
		if (!showGraph) {
			val display = "${format.displayName}: ${avg.format()}${format.unit}"
			val width = 180f
			val height = frameHeightWithSpacing + style.framePadding.y * 2
			hudBackground(width, height, bg, fallbackBorder) {
				textColored(display, textColor)
				cursorPosY += height
			}
			return
		}
		val overlay = "cur ${current.format()}${format.unit} | avg ${avg.format()}${format.unit}"

		val width = graphWidth + 20f
		val height = graphHeight + 20f
		hudBackground(width, height, bg, fallbackBorder) {
			textColored(overlay, textColor)
			plotLines(
				label = "##TPSPlot",
				values = data,
				overlayText = overlay,
				graphSize = ImVec2(graphWidth, graphHeight),
				stride = graphStride
			)
			cursorPosY += height
		}
	}
}

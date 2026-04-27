/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.hud

import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImVec2
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Formatting.format
import com.lambda.util.ServerTPS
import com.lambda.util.ServerTPS.recentData

object TPS : HudModule(
	name = "TPS",
	description = "Display the server's tick rate",
	tag = ModuleTag.HUD,
) {
	private val format by setting("Tick format", ServerTPS.TickFormat.Tps)
	private val showGraph by setting("Show TPS Graph", false)
	private val graphHeight by setting("Graph Height", 40f, 10f..200f, 1f)
	private val graphWidth by setting("Graph Width", 200f, 10f..500f, 1f)
	private val graphStride by setting("Graph Stride", 1, 1..20, 1)

	override fun ImGuiBuilder.buildLayout() {
		val data = recentData(format)
		if (data.isEmpty()) {
			text("No ${format.displayName} data yet")
			return
		}
		val current = data.last()
		val avg = data.average().toFloat()
		if (!showGraph) {
			text("${format.displayName}: ${avg.format()}${format.unit}")
			return
		}
		val overlay = "cur ${current.format()}${format.unit} | avg ${avg.format()}${format.unit}"

		plotLines(
			label = "##TPSPlot",
			values = data,
			overlayText = overlay,
			graphSize = ImVec2(graphWidth, graphHeight),
			stride = graphStride
		)
	}
}

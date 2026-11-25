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

package com.lambda.module.modules.hud

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.lambda.config.settings.comparable.boolSetting
import com.lambda.config.settings.comparable.enumSetting
import com.lambda.config.settings.numeric.floatSetting
import com.lambda.config.settings.numeric.intSetting
import com.lambda.gui.core.observe
import com.lambda.gui.hud.HudConfig
import com.lambda.gui.hud.HudText
import com.lambda.module.HudModule
import com.lambda.module.ModuleTag
import com.lambda.util.FormattingUtils.format
import com.lambda.util.ServerTPSUtils
import com.lambda.util.ServerTPSUtils.recentData
import com.lambda.util.math.toCompose

@Suppress("unused")
object Tps : HudModule(
	name = "TPS",
	description = "Display the server's tick rate",
	tag = ModuleTag.HUD,
) {
	private val format by enumSetting("Tick format", ServerTPSUtils.TickFormat.Tps)
	private val showGraph by boolSetting("Show TPS Graph", false)
	private val graphHeight by floatSetting("Graph Height", 40f, 10f..200f, 1f)
	private val graphWidth by floatSetting("Graph Width", 200f, 10f..500f, 1f)
	private val graphStride by intSetting("Graph Stride", 1, 1..20, 1)

	@Composable
	override fun Content() {
		val format by observe { format }
		val data by observe { recentData(format).toList() }
		if (data.isEmpty()) {
			HudText("No ${format.displayName} data yet")
			return
		}
		val average = data.average().toFloat()
		val showGraph by observe { showGraph }
		if (!showGraph) {
			HudText("${format.displayName}: ${average.format()}${format.unit}")
			return
		}

		val size by observe { DpSize(graphWidth.dp, graphHeight.dp) }
		val stride by observe { graphStride }
		val lineColor by observe { HudConfig.textColor.toCompose() }
		Box(
			Modifier.size(size).drawBehind { drawGraph(data, stride, lineColor) },
			Alignment.TopCenter
		) {
			HudText("cur ${data.last().format()}${format.unit} | avg ${average.format()}${format.unit}")
		}
	}

	private fun DrawScope.drawGraph(values: List<Float>, stride: Int, color: Color) {
		drawRect(Color.Black.copy(alpha = 0.35f))
		val points = values.filterIndexed { index, _ -> index % stride == 0 }
		if (points.size < 2) return
		val min = points.min()
		val range = (points.max() - min).takeIf { it > 0f } ?: 1f
		val path = Path()
		points.forEachIndexed { index, value ->
			val x = index * size.width / (points.size - 1)
			val y = size.height - (value - min) / range * size.height
			if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
		}
		drawPath(path, color, style = Stroke(1.5.dp.toPx()))
	}
}

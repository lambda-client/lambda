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

package com.lambda.module

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lambda.config.entries.onValueChangeUnsafe
import com.lambda.config.settings.comparable.enumSetting
import com.lambda.config.settings.complex.Bind
import com.lambda.config.settings.complex.colorSetting
import com.lambda.config.settings.numeric.floatSetting
import com.lambda.util.NamedEnum
import java.awt.Color
import kotlin.math.roundToInt

@Suppress("unused")
enum class HudAnchor(override val displayName: String, val x: Float, val y: Float) : NamedEnum {
	TopLeft("Top Left", 0f, 0f),
	Top("Top", 0.5f, 0f),
	TopRight("Top Right", 1f, 0f),
	Left("Left", 0f, 0.5f),
	Center("Center", 0.5f, 0.5f),
	Right("Right", 1f, 0.5f),
	BottomLeft("Bottom Left", 0f, 1f),
	Bottom("Bottom", 0.5f, 1f),
	BottomRight("Bottom Right", 1f, 1f);

	fun offset(size: IntSize) = IntOffset((size.width * x).roundToInt(), (size.height * y).roundToInt())
}

abstract class HudModule(
    name: String,
    description: String = "",
    tag: ModuleTag,
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: Bind = Bind.EMPTY,
	defaultScale: Float = 1f,
	defaultAnchorX: Float = DEFAULT_ANCHOR,
	defaultAnchorY: Float? = null,
) : Module(
	name,
	description,
	tag,
	alwaysListening,
	enabledByDefault,
	defaultKeybind = defaultKeybind
) {
    val backgroundColor = colorSetting("Background Color", Color(0, 0, 0, 0))
	val scale by floatSetting("Scale", defaultScale, 0.25f..5f, 0.05f, "Size of this element, on top of the Scale in HUD Settings")

	val anchor by enumSetting("Anchor", HudAnchor.TopLeft, "The corner or side of this element that stays exactly where it is on the screen. When the element grows or shrinks it does so away from that point. Changing it does not move the element")
		.onValueChangeUnsafe { from, to -> reanchor(from, to) }

	var shownSize = IntSize.Zero
	var shownScreen = IntSize.Zero

	var anchorX by floatSetting("HUD X", defaultAnchorX, 0f..1f, 0.001f) { false }
	var anchorY by floatSetting("HUD Y", defaultAnchorY ?: (DEFAULT_ANCHOR + (DEFAULT_SPACING * created++)).coerceAtMost(1f), 0f..1f, 0.001f) { false }

	@Composable
	abstract fun Content()

	fun place(topLeft: IntOffset) = pin(topLeft + anchor.offset(shownSize))

	private fun reanchor(from: HudAnchor, to: HudAnchor) {
		val screen = shownScreen
		val pinned = IntOffset((screen.width * anchorX).roundToInt(), (screen.height * anchorY).roundToInt())
		pin(pinned - from.offset(shownSize) + to.offset(shownSize))
	}

	private fun pin(point: IntOffset) {
		val screen = shownScreen
		if (screen.width <= 0 || screen.height <= 0) return
		anchorX = (point.x / screen.width.toFloat()).coerceIn(0f, 1f)
		anchorY = (point.y / screen.height.toFloat()).coerceIn(0f, 1f)
	}

	private companion object {
		const val DEFAULT_ANCHOR = 0.01f

		const val DEFAULT_SPACING = 0.06f
		var created = 0
	}
}

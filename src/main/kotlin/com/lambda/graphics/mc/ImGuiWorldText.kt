/*
 * Copyright 2025 Lambda
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

package com.lambda.graphics.mc

import com.lambda.graphics.RenderMain
import imgui.ImGui
import imgui.ImVec2
import net.minecraft.util.math.Vec3d
import java.awt.Color

/**
 * ImGUI-based world text renderer.
 * Projects world coordinates to screen space and draws text using ImGUI.
 *
 * Usage:
 * ```kotlin
 * // In a GuiEvent.NewFrame listener
 * ImGuiWorldText.drawText(entity.pos, "Label", Color.WHITE)
 * ```
 */
object ImGuiWorldText {

	/**
	 * Draw text at a world position using ImGUI.
	 *
	 * @param worldPos World position for the text
	 * @param text The text to render
	 * @param color Text color
	 * @param centered Whether to center the text horizontally
	 * @param offsetY Vertical offset in screen pixels (negative = up)
	 */
	fun drawText(
		worldPos: Vec3d,
		text: String,
		color: Color = Color.WHITE,
		centered: Boolean = true,
		offsetY: Float = 0f
	) {
		val screen = RenderMain.worldToScreen(worldPos) ?: return

		val drawList = ImGui.getBackgroundDrawList()
		val colorInt = colorToImGui(color)

		val x = if (centered) {
			val textSize = ImVec2()
			ImGui.calcTextSize(textSize, text)
			screen.x - textSize.x / 2f
		} else {
			screen.x
		}

		drawList.addText(x, screen.y + offsetY, colorInt, text)
	}

	/**
	 * Draw text with a shadow/outline effect.
	 */
	fun drawTextWithShadow(
		worldPos: Vec3d,
		text: String,
		color: Color = Color.WHITE,
		shadowColor: Color = Color.BLACK,
		centered: Boolean = true,
		offsetY: Float = 0f
	) {
		val screen = RenderMain.worldToScreen(worldPos) ?: return

		val drawList = ImGui.getBackgroundDrawList()
		val textSize = ImVec2()
		ImGui.calcTextSize(textSize, text)

		val x = if (centered) screen.x - textSize.x / 2f else screen.x
		val y = screen.y + offsetY

		// Draw shadow (offset by 1 pixel)
		val shadowInt = colorToImGui(shadowColor)
		drawList.addText(x + 1f, y + 1f, shadowInt, text)

		// Draw main text
		val colorInt = colorToImGui(color)
		drawList.addText(x, y, colorInt, text)
	}

	/**
	 * Draw multiple lines of text stacked vertically.
	 */
	fun drawMultilineText(
		worldPos: Vec3d,
		lines: List<String>,
		color: Color = Color.WHITE,
		centered: Boolean = true,
		lineSpacing: Float = 12f,
		offsetY: Float = 0f
	) {
		val screen = RenderMain.worldToScreen(worldPos) ?: return

		val drawList = ImGui.getBackgroundDrawList()
		val colorInt = colorToImGui(color)

		lines.forEachIndexed { index, line ->
			val textSize = ImVec2()
			ImGui.calcTextSize(textSize, line)

			val x = if (centered) screen.x - textSize.x / 2f else screen.x
			val y = screen.y + offsetY + (index * lineSpacing)

			drawList.addText(x, y, colorInt, line)
		}
	}

	/**
	 * Draw text with a background box.
	 */
	fun drawTextWithBackground(
		worldPos: Vec3d,
		text: String,
		textColor: Color = Color.WHITE,
		backgroundColor: Color = Color(0, 0, 0, 128),
		centered: Boolean = true,
		padding: Float = 4f,
		offsetY: Float = 0f
	) {
		val screen = RenderMain.worldToScreen(worldPos) ?: return

		val drawList = ImGui.getBackgroundDrawList()
		val textSize = ImVec2()
		ImGui.calcTextSize(textSize, text)

		val x = if (centered) screen.x - textSize.x / 2f else screen.x
		val y = screen.y + offsetY

		// Draw background
		val bgInt = colorToImGui(backgroundColor)
		drawList.addRectFilled(
			x - padding,
			y - padding,
			x + textSize.x + padding,
			y + textSize.y + padding,
			bgInt,
			2f // corner rounding
		)

		// Draw text
		val colorInt = colorToImGui(textColor)
		drawList.addText(x, y, colorInt, text)
	}

	/**
	 * Convert java.awt.Color to ImGui color format (ABGR)
	 */
	private fun colorToImGui(color: Color): Int {
		return (color.alpha shl 24) or
				(color.blue shl 16) or
				(color.green shl 8) or
				color.red
	}
}

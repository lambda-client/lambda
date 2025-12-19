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

import com.lambda.Lambda.mc
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import java.awt.Color

/**
 * Utility for rendering text in 3D world space.
 *
 * Uses Minecraft's TextRenderer to draw text that faces the camera (billboard style) at any world
 * position. Handles Unicode, formatting codes, and integrates with MC's rendering system.
 *
 * Usage:
 * ```kotlin
 * // In your render event
 * WorldTextRenderer.drawText(
 *     pos = entity.pos.add(0.0, entity.height + 0.5, 0.0),
 *     text = entity.name,
 *     color = Color.WHITE,
 *     scale = 0.025f
 * )
 * ```
 */
object WorldTextRenderer {

	/** Default scale for world text (MC uses 0.025f for name tags) */
	const val DEFAULT_SCALE = 0.025f

	/** Maximum light level for full brightness */
	private const val FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE

	/**
	 * Draw text at a world position, facing the camera.
	 *
	 * @param pos World position for the text
	 * @param text The text to render
	 * @param color Text color (ARGB)
	 * @param scale Text scale (0.025f is default name tag size)
	 * @param shadow Whether to draw drop shadow
	 * @param seeThrough Whether text should be visible through blocks
	 * @param centered Whether to center the text horizontally
	 * @param backgroundColor Background color (0 for no background)
	 * @param light Light level (uses full bright by default)
	 */
	fun drawText(
		pos: Vec3d,
		text: Text,
		color: Color = Color.WHITE,
		scale: Float = DEFAULT_SCALE,
		shadow: Boolean = true,
		seeThrough: Boolean = false,
		centered: Boolean = true,
		backgroundColor: Int = 0,
		light: Int = FULL_BRIGHT
	) {
		val client = mc
		val camera = client.gameRenderer?.camera ?: return
		val textRenderer = client.textRenderer ?: return
		val immediate = client.bufferBuilders?.entityVertexConsumers ?: return

		val cameraPos = camera.pos

		val matrices = MatrixStack()
		matrices.push()

		// Translate to world position relative to camera
		matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z)

		// Billboard - face camera using camera rotation directly (same as MC's LabelCommandRenderer)
		matrices.multiply(camera.rotation)

		// Scale with negative Y to flip text vertically (matches MC's 0.025, -0.025, 0.025)
		matrices.scale(scale, -scale, scale)

		// Calculate text position
		val textWidth = textRenderer.getWidth(text)
		val x = if (centered) -textWidth / 2f else 0f

		val layerType =
			if (seeThrough) TextRenderer.TextLayerType.SEE_THROUGH
			else TextRenderer.TextLayerType.NORMAL

		// Draw text
		textRenderer.draw(
			text,
			x,
			0f,
			color.rgb,
			shadow,
			matrices.peek().positionMatrix,
			immediate,
			layerType,
			backgroundColor,
			light
		)

		matrices.pop()

		// Flush immediately for world rendering
		immediate.draw()
	}

	/**
	 * Draw text at a world position with an outline effect.
	 *
	 * @param pos World position for the text
	 * @param text The text to render
	 * @param color Text color
	 * @param outlineColor Outline color
	 * @param scale Text scale
	 * @param centered Whether to center the text horizontally
	 * @param light Light level
	 */
	fun drawTextWithOutline(
		pos: Vec3d,
		text: Text,
		color: Color = Color.WHITE,
		outlineColor: Color = Color.BLACK,
		scale: Float = DEFAULT_SCALE,
		centered: Boolean = true,
		light: Int = FULL_BRIGHT
	) {
		val client = mc
		val camera = client.gameRenderer?.camera ?: return
		val textRenderer = client.textRenderer ?: return
		val immediate = client.bufferBuilders?.entityVertexConsumers ?: return

		val cameraPos = camera.pos

		val matrices = MatrixStack()
		matrices.push()

		matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z)

		// Billboard - face camera using camera rotation directly (same as MC's LabelCommandRenderer)
		matrices.multiply(camera.rotation)
		matrices.scale(scale, -scale, scale)

		val textWidth = textRenderer.getWidth(text)
		val x = if (centered) -textWidth / 2f else 0f

		textRenderer.drawWithOutline(
			text.asOrderedText(),
			x,
			0f,
			color.rgb,
			outlineColor.rgb,
			matrices.peek().positionMatrix,
			immediate,
			light
		)

		matrices.pop()
		immediate.draw()
	}

	/** Draw a simple string at a world position. */
	fun drawString(
		pos: Vec3d,
		text: String,
		color: Color = Color.WHITE,
		scale: Float = DEFAULT_SCALE,
		shadow: Boolean = true,
		seeThrough: Boolean = false,
		centered: Boolean = true
	) {
		drawText(pos, Text.literal(text), color, scale, shadow, seeThrough, centered)
	}

	/**
	 * Draw multiple lines of text stacked vertically.
	 *
	 * @param pos World position for the top line
	 * @param lines List of text lines to render
	 * @param color Text color
	 * @param scale Text scale
	 * @param lineSpacing Spacing between lines in scaled units (default 10)
	 */
	fun drawMultilineText(
		pos: Vec3d,
		lines: List<Text>,
		color: Color = Color.WHITE,
		scale: Float = DEFAULT_SCALE,
		lineSpacing: Float = 10f,
		shadow: Boolean = true,
		seeThrough: Boolean = false,
		centered: Boolean = true
	) {
		val client = mc
		val camera = client.gameRenderer?.camera ?: return
		val textRenderer = client.textRenderer ?: return
		val immediate = client.bufferBuilders?.entityVertexConsumers ?: return

		val cameraPos = camera.pos

		val matrices = MatrixStack()
		matrices.push()

		matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z)

		// Billboard - face camera using camera rotation directly (same as MC's LabelCommandRenderer)
		matrices.multiply(camera.rotation)
		matrices.scale(scale, -scale, scale)

		val layerType =
			if (seeThrough) TextRenderer.TextLayerType.SEE_THROUGH
			else TextRenderer.TextLayerType.NORMAL

		lines.forEachIndexed { index, text ->
			val textWidth = textRenderer.getWidth(text)
			val x = if (centered) -textWidth / 2f else 0f
			val y = index * lineSpacing

			textRenderer.draw(
				text,
				x,
				y,
				color.rgb,
				shadow,
				matrices.peek().positionMatrix,
				immediate,
				layerType,
				0,
				FULL_BRIGHT
			)
		}

		matrices.pop()
		immediate.draw()
	}

	/**
	 * Draw text with a background box.
	 *
	 * @param pos World position
	 * @param text Text to render
	 * @param textColor Text color
	 * @param backgroundColor Background color (with alpha)
	 * @param scale Text scale
	 * @param padding Padding around text in pixels
	 */
	fun drawTextWithBackground(
		pos: Vec3d,
		text: Text,
		textColor: Color = Color.WHITE,
		backgroundColor: Color = Color(0, 0, 0, 128),
		scale: Float = DEFAULT_SCALE,
		padding: Int = 2,
		shadow: Boolean = false,
		seeThrough: Boolean = false,
		centered: Boolean = true
	) {
		val client = mc
		client.textRenderer ?: return

		// Calculate background color as ARGB int
		val bgColorInt =
			(backgroundColor.alpha shl 24) or
					(backgroundColor.red shl 16) or
					(backgroundColor.green shl 8) or
					backgroundColor.blue

		drawText(
			pos = pos,
			text = text,
			color = textColor,
			scale = scale,
			shadow = shadow,
			seeThrough = seeThrough,
			centered = centered,
			backgroundColor = bgColorInt
		)
	}

	/** Calculate the width of text in world units at a given scale. */
	fun getTextWidth(text: Text, scale: Float = DEFAULT_SCALE): Float {
		val textRenderer = mc.textRenderer ?: return 0f
		return textRenderer.getWidth(text) * scale
	}

	/** Calculate the height of text in world units at a given scale. */
	fun getTextHeight(scale: Float = DEFAULT_SCALE): Float {
		val textRenderer = mc.textRenderer ?: return 0f
		return textRenderer.fontHeight * scale
	}
}

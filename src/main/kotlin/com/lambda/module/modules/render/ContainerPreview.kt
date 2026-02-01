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

package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.config.settings.complex.Bind
import com.lambda.interaction.material.container.containers.EnderChestContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode
import com.lambda.util.item.ItemStackUtils.shulkerBoxContents
import com.lambda.util.item.ItemUtils.shulkerBoxes
import net.minecraft.block.ShulkerBoxBlock
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.tooltip.TooltipComponent
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.tooltip.TooltipData
import net.minecraft.util.DyeColor
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

object ContainerPreview : Module(
	name = "ContainerPreview",
	description = "Renders shulker box contents visually in tooltips",
	tag = ModuleTag.RENDER,
) {
	private val lockKey by setting("Lock Key", Bind(KeyCode.LeftShift.code, 0, -1), "Key to lock the tooltip in place for item interaction")
	private val colorTint by setting("Color Tint", true, "Tint the background with the shulker box color")

	private val background = Identifier.ofVanilla("textures/gui/container/shulker_box.png")

	private var lockedStack: ItemStack? = null
	private var lockedX: Int = 0
	private var lockedY: Int = 0

	@JvmStatic
	var isRenderingSubTooltip: Boolean = false
		private set

	private const val ROWS = 3
	private const val COLS = 9
	private const val SLOT_SIZE = 18
	private const val PADDING = 7
	private const val TITLE_HEIGHT = 14

	@JvmStatic
	val isLocked: Boolean
		get() = lockedStack != null

	@JvmStatic
	fun isLockKeyPressed(): Boolean {
		if (!isEnabled) return false
		val handle = mc.window.handle
		return GLFW.glfwGetKey(handle, lockKey.key) == GLFW.GLFW_PRESS
	}

	private fun getTooltipWidth() = PADDING + COLS * SLOT_SIZE + PADDING
	private fun getTooltipHeight() = TITLE_HEIGHT + ROWS * SLOT_SIZE + PADDING

	/**
	 * Check if mouse is over the locked tooltip area (for click blocking)
	 */
	@JvmStatic
	fun isMouseOverLockedTooltip(mouseX: Int, mouseY: Int): Boolean {
		if (!isLocked) return false
		val width = getTooltipWidth()
		val height = getTooltipHeight()
		return mouseX >= lockedX && mouseX < lockedX + width &&
				mouseY >= lockedY && mouseY < lockedY + height
	}

	/**
	 * Calculate text color based on background luminance.
	 * Returns dark text for light backgrounds and white text for dark backgrounds.
	 */
	private fun getTextColor(tintColor: Int): Int {
		val r = ((tintColor shr 16) and 0xFF) / 255f
		val g = ((tintColor shr 8) and 0xFF) / 255f
		val b = (tintColor and 0xFF) / 255f
		val luminance = 0.299f * r + 0.587f * g + 0.114f * b
		return if (luminance > 0.5f) 0x404040 else 0xFFFFFF
	}

	@JvmStatic
	fun renderShulkerTooltip(
		context: DrawContext,
		textRenderer: TextRenderer,
		component: ContainerComponent,
		mouseX: Int,
		mouseY: Int
	) {
		val width = getTooltipWidth()
		val height = getTooltipHeight()

		val lockKeyPressed = isLockKeyPressed()

		if (lockKeyPressed && lockedStack == null) {
			lockedStack = component.stack.copy()
			lockedX = calculateTooltipX(mouseX, width)
			lockedY = calculateTooltipY(mouseY, height)
		} else if (!lockKeyPressed && lockedStack != null) {
			lockedStack = null
		}

		if (isLocked) {
			renderLockedTooltipInternal(context, textRenderer)
			return
		}

		renderTooltipForStack(context, textRenderer, component.stack, calculateTooltipX(mouseX, width), calculateTooltipY(mouseY, height), false)
	}

	/**
	 * Render the locked tooltip - called from mixin when we're locked
	 */
	@JvmStatic
	fun renderLockedTooltip(context: DrawContext, textRenderer: TextRenderer) {
		if (!isLockKeyPressed()) {
			lockedStack = null
			return
		}
		renderLockedTooltipInternal(context, textRenderer)
	}

	private fun renderLockedTooltipInternal(context: DrawContext, textRenderer: TextRenderer) {
		val stack = lockedStack ?: return
		renderTooltipForStack(context, textRenderer, stack, lockedX, lockedY, true)
	}

	private fun renderTooltipForStack(context: DrawContext, textRenderer: TextRenderer, stack: ItemStack, x: Int, y: Int, allowHover: Boolean) {
		val contents = getContainerContents(stack)
		val width = getTooltipWidth()
		val height = getTooltipHeight()

		val matrices = context.matrices
		matrices.pushMatrix()

		val tintColor = getContainerTintColor(stack)

		drawBackground(context, x, y, width, height, tintColor)
		val name = stack.name
		val textColor = getTextColor(tintColor)
		context.drawText(textRenderer, name, x + PADDING, y + 4, textColor, false)

		val slotsStartX = x + PADDING
		val slotsStartY = y + TITLE_HEIGHT

		val actualMouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
		val actualMouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()

		var hoveredStack: ItemStack? = null
		var hoveredSlotX = 0
		var hoveredSlotY = 0

		for ((index, item) in contents.withIndex()) {
			if (index >= COLS * ROWS) break

			val slotCol = index % COLS
			val slotRow = index / COLS

			val slotX = slotsStartX + slotCol * SLOT_SIZE
			val slotY = slotsStartY + slotRow * SLOT_SIZE
			val itemX = slotX + 1
			val itemY = slotY + 1

			if (allowHover) {
				val isHovered = actualMouseX >= slotX && actualMouseX < slotX + SLOT_SIZE &&
						actualMouseY >= slotY && actualMouseY < slotY + SLOT_SIZE

				if (isHovered && !item.isEmpty) {
					context.fill(itemX, itemY, itemX + 16, itemY + 16, 0x80FFFFFF.toInt())
					hoveredStack = item
					hoveredSlotX = actualMouseX
					hoveredSlotY = actualMouseY
				}
			}

			if (!item.isEmpty) {
				context.drawItem(item, itemX, itemY)
				context.drawStackOverlay(textRenderer, item, itemX, itemY)
			}
		}

		matrices.popMatrix()

		hoveredStack?.let { stack ->
			matrices.pushMatrix()

			if (isPreviewableContainer(stack)) {
				val nestedWidth = getTooltipWidth()
				val nestedHeight = getTooltipHeight()
				val nestedX = calculateTooltipX(hoveredSlotX, nestedWidth)
				val nestedY = calculateTooltipY(hoveredSlotY, nestedHeight)
				renderTooltipForStack(context, textRenderer, stack, nestedX, nestedY, false)
			} else {
				isRenderingSubTooltip = true
				try {
					context.drawItemTooltip(textRenderer, stack, hoveredSlotX, hoveredSlotY)
				} finally {
					isRenderingSubTooltip = false
				}
			}
			matrices.popMatrix()
		}
	}

	private fun getContainerContents(stack: ItemStack): List<ItemStack> {
		return when {
			isShulkerBox(stack) -> stack.shulkerBoxContents
			isEnderChest(stack) -> EnderChestContainer.stacks
			else -> emptyList()
		}
	}

	private fun getContainerTintColor(stack: ItemStack): Int {
		if (!colorTint) return 0xFFFFFFFF.toInt()

		return when {
			isShulkerBox(stack) -> {
				val color = getShulkerColor(stack)
				color?.entityColor ?: 0xFFFFFFFF.toInt()
			}
			isEnderChest(stack) -> 0xFF1E1E2E.toInt()
			else -> 0xFFFFFFFF.toInt()
		}
	}

	private fun calculateTooltipX(mouseX: Int, width: Int): Int {
		val screenWidth = mc.window.scaledWidth
		var x = mouseX + 12
		if (x + width > screenWidth) {
			x = mouseX - width - 12
		}
		if (x < 0) x = 0
		return x
	}

	private fun calculateTooltipY(mouseY: Int, height: Int): Int {
		val screenHeight = mc.window.scaledHeight
		var y = mouseY - 12
		if (y + height > screenHeight) {
			y = screenHeight - height
		}
		if (y < 0) y = 0
		return y
	}

	private fun drawBackground(context: DrawContext, x: Int, y: Int, width: Int, height: Int, tintColor: Int) {
		// Draw the shulker box texture background with tint
		// The shulker_box.png texture is 176x166
		// Top part (title area): y=0 to y=17
		// Slot area: y=17 to y=89 (3 rows of 18px each + borders)
		// Bottom: y=160 onwards

		context.drawTexture(
			RenderPipelines.GUI_TEXTURED,
			background,
			x, y,
			0f, 0f,
			width, TITLE_HEIGHT,
			width, TITLE_HEIGHT,
			256, 256,
			tintColor
		)

		// Middle rows
		(0 until ROWS).forEach { row ->
			context.drawTexture(
				RenderPipelines.GUI_TEXTURED,
				background,
				x, y + TITLE_HEIGHT + row * SLOT_SIZE,
				0f, 17f,
				width, SLOT_SIZE,
				width, SLOT_SIZE,
				256, 256,
				tintColor
			)
		}

		// Bottom
		context.drawTexture(
			RenderPipelines.GUI_TEXTURED,
			background,
			x, y + TITLE_HEIGHT + ROWS * SLOT_SIZE,
			0f, 160f,
			width, PADDING,
			width, PADDING,
			256, 256,
			tintColor
		)
	}

	private fun getShulkerColor(stack: ItemStack): DyeColor? {
		val item = stack.item
		if (item is BlockItem) {
			val block = item.block
			if (block is ShulkerBoxBlock) {
				return block.color
			}
		}
		return null
	}

	@JvmStatic
	fun isShulkerBox(stack: ItemStack) = stack.item in shulkerBoxes

	@JvmStatic
	fun isEnderChest(stack: ItemStack) = stack.item == Items.ENDER_CHEST && EnderChestContainer.stacks.isNotEmpty()

	@JvmStatic
	fun isPreviewableContainer(stack: ItemStack) = isShulkerBox(stack) || isEnderChest(stack)

	open class ContainerComponent(val stack: ItemStack) : TooltipData, TooltipComponent {
		override fun drawItems(textRenderer: TextRenderer, x: Int, y: Int, width: Int, height: Int, context: DrawContext) {}
		override fun getHeight(textRenderer: TextRenderer): Int = 0
		override fun getWidth(textRenderer: TextRenderer): Int = 0
	}
}

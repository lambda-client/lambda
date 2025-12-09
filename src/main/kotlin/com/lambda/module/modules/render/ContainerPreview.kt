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
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.TooltipComponent
import net.minecraft.client.render.RenderLayer
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
    private val useShift by setting("Use Shift", true, "Use shift key to lock tooltip (overrides lock key)")
    private val colorTint by setting("Color Tint", true, "Tint the background with the shulker box color")

    private val background = Identifier.ofVanilla("textures/gui/container/shulker_box.png")

    // Locked tooltip state - store the stack instead of component for persistence
    private var lockedStack: ItemStack? = null
    private var lockedX: Int = 0
    private var lockedY: Int = 0

    // Flag to prevent recursive tooltip rendering
    @JvmStatic
    var isRenderingSubTooltip: Boolean = false
        private set

    // Tooltip dimensions (3 rows x 9 columns like shulker box)
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
        if (useShift) return Screen.hasShiftDown()
        val handle = mc.window.handle
        return GLFW.glfwGetKey(handle, lockKey.key) == GLFW.GLFW_PRESS
    }

    private fun getTooltipWidth(): Int = PADDING + COLS * SLOT_SIZE + PADDING
    private fun getTooltipHeight(): Int = TITLE_HEIGHT + ROWS * SLOT_SIZE + PADDING

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

        // Calculate relative luminance using standard formula
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b

        // Use dark text on light backgrounds, white text on dark backgrounds
        return if (luminance > 0.5f) 0x404040 else 0xFFFFFF
    }

    @JvmStatic
    fun renderShulkerTooltip(context: DrawContext, textRenderer: TextRenderer, component: ContainerComponent, mouseX: Int, mouseY: Int) {
        // Calculate tooltip position
        val width = getTooltipWidth()
        val height = getTooltipHeight()

        // Handle locking - lock when key is pressed while hovering a shulker
        val lockKeyPressed = isLockKeyPressed()

        if (lockKeyPressed && lockedStack == null) {
            // Lock when key is pressed while hovering
            lockedStack = component.stack.copy()  // Copy the stack to preserve it
            lockedX = calculateTooltipX(mouseX, width)
            lockedY = calculateTooltipY(mouseY, height)
        } else if (!lockKeyPressed && lockedStack != null) {
            // Unlock when key is released
            lockedStack = null
        }

        // If locked, delegate to renderLockedTooltip
        if (isLocked) {
            renderLockedTooltipInternal(context, textRenderer)
            return
        }

        // Not locked - render normal tooltip following mouse
        renderTooltipForStack(context, textRenderer, component.stack, calculateTooltipX(mouseX, width), calculateTooltipY(mouseY, height), false)
    }

    /**
     * Render the locked tooltip - called from mixin when we're locked
     */
    @JvmStatic
    fun renderLockedTooltip(context: DrawContext, textRenderer: TextRenderer) {
        // Update lock state - unlock if key released
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
        matrices.push()
        matrices.translate(0f, 0f, 400f)

        // Get container color (shulker box color or purple for ender chest)
        val tintColor = getContainerTintColor(stack)

        // Draw background with color tint
        drawBackground(context, x, y, width, height, tintColor)

        // Draw title (container name) with appropriate text color
        val name = stack.name
        val textColor = getTextColor(tintColor)
        context.drawText(textRenderer, name, x + PADDING, y + 4, textColor, false)

        // Slots start at padding offset, items are centered in 18px slots (1px padding on each side)
        val slotsStartX = x + PADDING
        val slotsStartY = y + TITLE_HEIGHT

        // Get actual mouse position for hover detection
        val actualMouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val actualMouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()

        var hoveredStack: ItemStack? = null
        var hoveredSlotX = 0
        var hoveredSlotY = 0

        for ((index, item) in contents.withIndex()) {
            if (index >= COLS * ROWS) break

            val slotCol = index % COLS
            val slotRow = index / COLS

            // Slot bounds (full 18x18 area for hover detection)
            val slotX = slotsStartX + slotCol * SLOT_SIZE
            val slotY = slotsStartY + slotRow * SLOT_SIZE

            // Item position (centered in slot with 1px offset)
            val itemX = slotX + 1
            val itemY = slotY + 1

            // Check if this slot is hovered (only when locked/allowHover)
            // Use full slot size (18x18) for hover detection to avoid dead zones
            if (allowHover) {
                val isHovered = actualMouseX >= slotX && actualMouseX < slotX + SLOT_SIZE &&
                        actualMouseY >= slotY && actualMouseY < slotY + SLOT_SIZE

                if (isHovered && !item.isEmpty) {
                    // Draw highlight on the item area (16x16)
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

        matrices.pop()

        // Draw sub-tooltip for hovered item at higher Z level
        if (hoveredStack != null && allowHover) {
            matrices.push()
            matrices.translate(0f, 0f, 500f)  // Higher Z than the main tooltip

            // Check if hovered item is also a previewable container (shulker in echest)
            if (isPreviewableContainer(hoveredStack)) {
                // Render nested container preview recursively
                val nestedWidth = getTooltipWidth()
                val nestedHeight = getTooltipHeight()
                val nestedX = calculateTooltipX(hoveredSlotX, nestedWidth)
                val nestedY = calculateTooltipY(hoveredSlotY, nestedHeight)
                renderTooltipForStack(context, textRenderer, hoveredStack, nestedX, nestedY, false)
            } else {
                // Regular item tooltip - use mixin bypass flag
                isRenderingSubTooltip = true
                try {
                    context.drawItemTooltip(textRenderer, hoveredStack, hoveredSlotX, hoveredSlotY)
                } finally {
                    isRenderingSubTooltip = false
                }
            }
            matrices.pop()
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
            isEnderChest(stack) -> {
                // Ender chest purple/dark tint
                0xFF1E1E2E.toInt()
            }
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
        // Extract RGB components for tinting
        val r = ((tintColor shr 16) and 0xFF) / 255f
        val g = ((tintColor shr 8) and 0xFF) / 255f
        val b = (tintColor and 0xFF) / 255f

        // Draw the shulker box texture background with tint
        // The shulker_box.png texture is 176x166
        // Top part (title area): y=0 to y=17
        // Slot area: y=17 to y=89 (3 rows of 18px each + borders)
        // Bottom: y=160 onwards

        context.drawTexture(
            RenderLayer::getGuiTextured,
            background,
            x, y,
            0f, 0f,
            width, TITLE_HEIGHT,
            256, 256,
            tintColor
        )

        // Middle rows
        for (row in 0 until ROWS) {
            context.drawTexture(
                RenderLayer::getGuiTextured,
                background,
                x, y + TITLE_HEIGHT + row * SLOT_SIZE,
                0f, 17f,
                width, SLOT_SIZE,
                256, 256,
                tintColor
            )
        }

        // Bottom
        context.drawTexture(
            RenderLayer::getGuiTextured,
            background,
            x, y + TITLE_HEIGHT + ROWS * SLOT_SIZE,
            0f, 160f,
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
    fun isShulkerBox(stack: ItemStack): Boolean {
        return stack.item in shulkerBoxes
    }

    @JvmStatic
    fun isEnderChest(stack: ItemStack): Boolean {
        return stack.item == Items.ENDER_CHEST && EnderChestContainer.stacks.isNotEmpty()
    }

    @JvmStatic
    fun isPreviewableContainer(stack: ItemStack): Boolean {
        return isShulkerBox(stack) || isEnderChest(stack)
    }

    open class ContainerComponent(val stack: ItemStack) : TooltipData, TooltipComponent {
        val contents: List<ItemStack>
            get() = when {
                isShulkerBox(stack) -> stack.shulkerBoxContents
                isEnderChest(stack) -> EnderChestContainer.stacks
                else -> emptyList()
            }

        // These methods are not used since we render the tooltip ourselves
        override fun drawItems(textRenderer: TextRenderer, x: Int, y: Int, width: Int, height: Int, context: DrawContext) {}
        override fun getHeight(textRenderer: TextRenderer): Int = 0
        override fun getWidth(textRenderer: TextRenderer): Int = 0
    }

    // Keep for backwards compatibility
    @Deprecated("Use ContainerComponent instead", ReplaceWith("ContainerComponent"))
    class ShulkerComponent(stack: ItemStack) : ContainerComponent(stack)
}

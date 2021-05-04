package com.lambda.client.module.modules.render

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.RenderUtils2D
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.threads.safeListener
import com.lambda.commons.extension.ceilToInt
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.inventory.ContainerChest
import net.minecraft.inventory.IInventory
import net.minecraft.inventory.InventoryEnderChest
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.play.server.SPacketWindowItems
import net.minecraft.util.NonNullList

/**
 * @see MixinGuiScreen.renderToolTip
 */
object ContainerPreview : Module(
    name = "ContainerPreview",
    category = Category.RENDER,
    description = "Previews shulkers and ender chests in the game GUI"
) {

    private val useCustomFont by setting("Use Custom Font", false)
    private val backgroundColorSetting by setting("Background Color", ColorHolder(16, 0, 16, 190))
    private val borderTopColor by setting("Top Border Color", ColorHolder(144, 101, 237, 54))
    private val borderBottomColor by setting("Bottom Border Color", ColorHolder(40, 0, 127, 80))
    var enderChestItems: IInventory? = null

    fun renderContainerAndItems(stack: ItemStack, originalX: Int, originalY: Int, items: NonNullList<ItemStack>) {
        GlStateManager.pushMatrix()
        GlStateManager.translate(0.0, 0.0, 500.0)

        renderContainer(stack, originalX, originalY)
        renderContainerItems(items, originalX, originalY)

        GlStateManager.popMatrix()
    }

    fun getShulkerData(stack: ItemStack): NBTTagCompound? {
        val tagCompound = if (stack.item is ItemShulkerBox) stack.tagCompound else return null

        if (tagCompound != null && tagCompound.hasKey("BlockEntityTag", 10)) {
            val blockEntityTag = tagCompound.getCompoundTag("BlockEntityTag")
            if (blockEntityTag.hasKey("Items", 9)) {
                return blockEntityTag
            }
        }

        return null
    }

    private fun renderContainer(stack: ItemStack, originalX: Int, originalY: Int) {
        val width = 144.coerceAtLeast(FontRenderAdapter.getStringWidth(stack.displayName).ceilToInt() + 3)
        val vertexHelper = VertexHelper(GlStateUtils.useVbo())

        val x = (originalX + 12).toDouble()
        val y = (originalY - 12).toDouble()
        val height = FontRenderAdapter.getFontHeight() + 48

        RenderUtils2D.drawRoundedRectFilled(
            vertexHelper,
            Vec2d(x - 4, y - 4),
            Vec2d(x + width + 4, y + height + 4),
            1.0,
            color = backgroundColorSetting
        )

        val points = arrayOf(
            Vec2d(x - 3, y - 3) to borderTopColor,
            Vec2d(x - 3, y + height + 3) to borderBottomColor,
            Vec2d(x + width + 3, y + height + 3) to borderBottomColor,
            Vec2d(x + width + 3, y - 3) to borderTopColor,
            Vec2d(x - 3, y - 3) to borderTopColor
        )

        RenderUtils2D.drawLineWithColorPoints(vertexHelper, points, 5.0f)

        FontRenderAdapter.drawString(stack.displayName, x.toFloat(), y.toFloat() - 2.0f, customFont = useCustomFont)
    }

    private fun renderContainerItems(shulkerInventory: NonNullList<ItemStack>, originalX: Int, originalY: Int) {
        for (i in 0 until shulkerInventory.size) {
            val x = originalX + (i % 9) * 16 + 11
            val y = originalY + (i / 9) * 16 - 2
            RenderUtils2D.drawItem(shulkerInventory[i], x, y)
        }
    }
}

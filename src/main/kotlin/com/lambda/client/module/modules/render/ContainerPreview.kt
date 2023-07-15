package com.lambda.client.module.modules.render

import com.lambda.client.commons.extension.ceilToInt
import com.lambda.client.commons.extension.floorToInt
import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.manager.managers.CachedContainerManager
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.*
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.items.block
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.init.Blocks
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemShulkerBox
import net.minecraft.item.ItemStack
import net.minecraft.nbt.*
import net.minecraft.tileentity.TileEntity
import net.minecraft.tileentity.TileEntityLockableLoot
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import org.lwjgl.opengl.GL11.GL_LINE_LOOP
import org.lwjgl.opengl.GL11.glLineWidth
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

object ContainerPreview : Module(
    name = "ContainerPreview",
    description = "Previews shulkers and ender chests in the game GUI",
    category = Category.RENDER
) {
    val cacheContainers by setting("Cache Containers", true)
    private val renderCachedContainers by setting("Render Cached Containers", true, { cacheContainers })
    private val useCustomFont by setting("Use Custom Font", false)
    private val backgroundColor by setting("Background Color", ColorHolder(16, 0, 16, 255))
    private val borderTopColor by setting("Top Border Color", ColorHolder(144, 101, 237, 54))
    private val borderBottomColor by setting("Bottom Border Color", ColorHolder(40, 0, 127, 80))

    var enderChest: IInventory? = null

    init {
        safeListener<RenderOverlayEvent> {
            if (!renderCachedContainers) return@safeListener

            var indexH = 0

            // Preprocessing needs to be done in the manager to reduce strain on the render thread
            CachedContainerManager.getAllContainers()?.forEach { tag ->
                CachedContainerManager.getInventoryOfContainer(tag)?.let { container ->
                    val thisPos = BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"))
                    val type = (TileEntity.create(world, tag) as? TileEntityLockableLoot) ?: return@safeListener
                    var matrix = CachedContainerManager.getContainerMatrix(type)

                    var renderPos = thisPos.toVec3dCenter()

                    (tag.getTag("adjacentChest") as? NBTTagByte)?.byte?.toInt()?.let { index ->
                        renderPos = getHitVec(thisPos, EnumFacing.byIndex(index))
                        matrix = Pair(9, 6)
                    }

                    val screenPos = ProjectionUtils.toScaledScreenPos(renderPos)

                    val width = matrix.first * 16
                    val height = matrix.second * 16

                    val vertexHelper = VertexHelper(GlStateUtils.useVbo())

                    val color = backgroundColor.clone().apply { a = 50 }

                    val newX = screenPos.x - width / 2
                    val newY = screenPos.y - height / 2

                    RenderUtils2D.drawRoundedRectFilled(
                        vertexHelper,
                        Vec2d(newX, newY),
                        Vec2d(newX + width, newY + height),
                        1.0,
                        color = color
                    )

                    container.forEachIndexed { index, itemStack ->
                        val x = newX + (index % matrix.first) * 16
                        val y = newY + (index / matrix.first) * 16
                        RenderUtils2D.drawItem(itemStack, x.floorToInt(), y.floorToInt())
                    }
                }
                indexH += 60
            }
        }
    }

    fun renderTooltips(itemStack: ItemStack, x: Int, y: Int, ci: CallbackInfo) {
        val item = itemStack.item

        if (item is ItemShulkerBox) {
            renderShulkerBoxTooltips(itemStack, x, y, ci)
        } else if (item.block == Blocks.ENDER_CHEST) {
            renderEnderChestTooltips(itemStack, x, y, ci)
        }
    }

    private fun renderShulkerBoxTooltips(itemStack: ItemStack, x: Int, y: Int, ci: CallbackInfo) {
        getShulkerData(itemStack)?.let {
            val itemStacks = Array(27) { ItemStack.EMPTY }
            val nbtTagList = it.getTagList("Items", 10)

            for (i in 0 until nbtTagList.tagCount()) {
                val itemStackNBTTag = nbtTagList.getCompoundTagAt(i)
                val slot = itemStackNBTTag.getInteger("Slot") and 255
                if (slot in itemStacks.indices) {
                    itemStacks[slot] = ItemStack(itemStackNBTTag)
                }
            }

            ci.cancel()
            renderContainerAndItems(itemStack, x, y, itemStacks)
        }
    }

    private fun getShulkerData(stack: ItemStack): NBTTagCompound? {
        val tagCompound = if (stack.item is ItemShulkerBox) stack.tagCompound else return null

        tagCompound?.let {
            val blockEntityTag = it.getCompoundTag("BlockEntityTag")
            if (blockEntityTag.hasKey("Items", 9)) {
                return blockEntityTag
            }
        }
        return null
    }

    private fun renderEnderChestTooltips(itemStack: ItemStack, x: Int, y: Int, ci: CallbackInfo) {
        val itemStacks = Array(27) { ItemStack.EMPTY }
        enderChest?.let {
            for (i in itemStacks.indices) {
                itemStacks[i] = it.getStackInSlot(i)
            }
        }

        ci.cancel()
        renderContainerAndItems(itemStack, x, y, itemStacks)
    }

    private fun renderContainerAndItems(stack: ItemStack, originalX: Int, originalY: Int, items: Array<ItemStack>) {
        GlStateManager.pushMatrix()
        GlStateManager.translate(0.0, 0.0, 500.0)

        renderContainer(stack, originalX, originalY)
        renderContainerItems(items, originalX, originalY)

        GlStateManager.popMatrix()
    }

    private fun renderContainer(stack: ItemStack, originalX: Int, originalY: Int) {
        val width = 144.coerceAtLeast(FontRenderAdapter.getStringWidth(stack.displayName).ceilToInt() + 3)
        val vertexHelper = VertexHelper(GlStateUtils.useVbo())

        val x = (originalX + 12).toDouble()
        val y = (originalY - 12).toDouble()
        val height = FontRenderAdapter.getFontHeight(customFont = useCustomFont) + 48

        RenderUtils2D.drawRoundedRectFilled(
            vertexHelper,
            Vec2d(x - 4, y - 4),
            Vec2d(x + width + 4, y + height + 4),
            1.0,
            color = backgroundColor
        )

        drawRectOutline(vertexHelper, x, y, width, height.floorToInt())

        FontRenderAdapter.drawString(stack.displayName, x.toFloat(), y.toFloat() - 2.0f, customFont = useCustomFont)
    }

    private fun drawRectOutline(vertexHelper: VertexHelper, x: Double, y: Double, width: Int, height: Int) {
        RenderUtils2D.prepareGl()
        glLineWidth(5.0f)

        vertexHelper.begin(GL_LINE_LOOP)
        vertexHelper.put(Vec2d(x - 3, y - 3), borderTopColor)
        vertexHelper.put(Vec2d(x - 3, y + height + 3), borderBottomColor)
        vertexHelper.put(Vec2d(x + width + 3, y + height + 3), borderBottomColor)
        vertexHelper.put(Vec2d(x + width + 3, y - 3), borderTopColor)
        vertexHelper.end()

        RenderUtils2D.releaseGl()
        glLineWidth(1.0f)
    }

    private fun renderContainerItems(itemStacks: Array<ItemStack>, originalX: Int, originalY: Int) {
        for (i in itemStacks.indices) {
            val x = originalX + (i % 9) * 16 + 11
            val y = originalY + (i / 9) * 16 - 2
            RenderUtils2D.drawItem(itemStacks[i], x, y)
        }
    }
}

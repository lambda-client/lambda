package com.lambda.client.gui.hudgui.elements.player

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.HudElement
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.RenderUtils2D
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.items.craftingSlots
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.threads.runSafe
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

internal object XCarryViewer : HudElement(
    name = "XcarryViewer",
    category = Category.PLAYER,
    description = "Show items in your inventory crafting grid"
) {
    private val mcTexture by setting("Minecraft Texture", false)
    private val border by setting("Border", true, { !mcTexture })
    private val borderColor by setting("Border Color", ColorHolder(111, 166, 222, 255), true, { !mcTexture && border })
    private val background by setting("Background", true, { !mcTexture })
    private val backgroundColor by setting("Background Color", ColorHolder(30, 36, 48, 127), true, { !mcTexture && background })

    override val hudWidth: Float = 36.0f
    override val hudHeight: Float = 36.0f

    private val containerTexture = ResourceLocation("textures/gui/container/inventory.png")

    override fun renderHud(vertexHelper: VertexHelper) {
        super.renderHud(vertexHelper)
        runSafe {
            drawFrame(vertexHelper)
            drawFrameTexture()
            drawItems()
        }
    }

    private fun drawFrame(vertexHelper: VertexHelper) {
        if (!mcTexture) {
            if (background) {
                RenderUtils2D.drawRectFilled(vertexHelper, posEnd = Vec2d(36.0, 36.0), color = backgroundColor)
            }
            if (border) {
                RenderUtils2D.drawRectOutline(vertexHelper, posEnd = Vec2d(36.0, 36.0), lineWidth = 2.0f, color = borderColor)
            }
        }
    }

    private fun drawFrameTexture() {
        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        GlStateUtils.texture2d(true)

        if (mcTexture) {
            mc.renderEngine.bindTexture(containerTexture)
            buffer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_TEX)
            buffer.pos(0.0, 0.0, 0.0).tex(0.02734375, 0.32421875).endVertex()
            buffer.pos(0.0, 36.0, 0.0).tex(0.02734375, 0.4609375).endVertex() // multiply the u/v values by 256
            buffer.pos(36.0, 0.0, 0.0).tex(0.1640625, 0.32421875).endVertex() // i'm too lazy to do it for you
            buffer.pos(36.0, 36.0, 0.0).tex(0.1640625, 0.4609375).endVertex() // ~Aven
            tessellator.draw()
        }
    }

    private fun SafeClientEvent.drawItems() {
        for ((index, slot) in player.craftingSlots.withIndex()) {
            val itemStack = slot.stack
            if (itemStack.isEmpty) continue

            val slotX = index % 2 * 18 + 1
            val slotY = index / 2 * 18 + 1

            RenderUtils2D.drawItem(itemStack, slotX, slotY)
        }
    }


}
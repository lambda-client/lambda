package com.lambda.client.gui.hudgui.elements.misc

import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.ResourceLocation
import com.lambda.client.gui.hudgui.HudElement
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.threads.runSafe
import com.lambda.commons.utils.MathUtils
import org.lwjgl.opengl.GL11.*

internal object Popcat : HudElement(
    name = "Popcat",
    category = Category.MISC,
    description = "Ez pop!"
) {

    override val hudWidth: Float = 122.0f
    override val hudHeight: Float = 122.0f
    private const val popScale = 1.0f
    var popcount = 0
    val alternate = TickTimer(TimeUnit.MILLISECONDS)

    private val type by setting("Mode", Mode.NORMAL)
    private val pop2 by setting("Speed", 100, 75..300, 5)

    override fun renderHud(vertexHelper: VertexHelper) {
        super.renderHud(vertexHelper)
        runSafe {
            popTimer()
        }
    }

    private fun popTimer() {
        if (popcount == 100000) popcount = 0
        if (alternate.tick(pop2)) popcount += 1
        if (MathUtils.isNumberEven(popcount)) drawPop(getLocationOne()) else drawPop(getLocationTwo())
    }

    private fun getLocationOne(): ResourceLocation {
        if (type == Mode.NORMAL) return ResourceLocation("lambda/pop1.png")
        if (type == Mode.FELIX) return ResourceLocation("lambda/felix1.png")
        if (type == Mode.WALTER) return ResourceLocation("lambda/walter1.png")
        return ResourceLocation("lambda/pop1.png")
    }

    private fun getLocationTwo(): ResourceLocation {
        if (type == Mode.NORMAL) return ResourceLocation("lambda/pop2.png")
        if (type == Mode.FELIX) return ResourceLocation("lambda/felix2.png")
        if (type == Mode.WALTER) return ResourceLocation("lambda/walter2.png")
        return ResourceLocation("lambda/pop1.png")
    }

    private fun drawPop(location: ResourceLocation) {
        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer
        GlStateUtils.texture2d(true)

        mc.renderEngine.bindTexture(location)
        GlStateManager.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)

        val center = Vec2d(61.0, 61.0)
        val halfWidth = popScale * 61.0
        val halfHeight = popScale * 61.0

        buffer.begin(GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_TEX)
        buffer.pos(center.x - halfWidth, center.y - halfHeight, 0.0).tex(0.0, 0.0).endVertex()
        buffer.pos(center.x - halfWidth, center.y + halfHeight, 0.0).tex(0.0, 1.0).endVertex()
        buffer.pos(center.x + halfWidth, center.y - halfHeight, 0.0).tex(1.0, 0.0).endVertex()
        buffer.pos(center.x + halfWidth, center.y + halfHeight, 0.0).tex(1.0, 1.0).endVertex()
        tessellator.draw()

        GlStateManager.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
    }

    enum class Mode {
        NORMAL, FELIX, WALTER
    }
}
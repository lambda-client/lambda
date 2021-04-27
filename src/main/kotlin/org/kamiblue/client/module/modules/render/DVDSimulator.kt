package org.kamiblue.client.module.modules.render

import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.ResourceLocation
import org.kamiblue.client.event.events.RenderOverlayEvent
import org.kamiblue.client.module.Category
import org.kamiblue.client.module.Module
import org.kamiblue.event.listener.listener
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.util.*

/*
    Originally by Ciruu (Abyss Client, Used with Permission)
    Adapted for Kami Blue & kotlin by ToxicAven
*/

internal object DVDSimulator : Module(
    name = "DVD Simulator",
    category = Category.RENDER,
    description = "Will it hit the corner?"
) {

    private var x = 0.0
    private var y = 0.0

    private var dx = 0.0
    private var dy = 0.0

    private var r = 255
    private var g = 255
    private var b = 0

    private const val originalWidth = 800
    private const val originalHeight = 408

    private val rand = Random()

    private val dvdLocation = ResourceLocation("kamiblue/dvd.png")
    
    private val scale by setting("Scale", 0.25f, 0.1f..1.0f, 0.05f)
    private val onlyGUI by setting("Only in GUI", true)

    init {
        onEnable {
            val res = ScaledResolution(mc)
            val w = (originalWidth * (scale / 2)).toInt()
            val h = (originalHeight * (scale / 2)).toInt()

            x = rand.nextInt(res.scaledWidth - w).toDouble()
            y = rand.nextInt(res.scaledHeight - h).toDouble()

            dx = if (rand.nextBoolean()) (-1).toDouble() else 1.toDouble()
            dy = if (rand.nextBoolean()) (-1).toDouble() else 1.toDouble()
        }

        listener<RenderOverlayEvent> {
            if (onlyGUI && mc.currentScreen == null)
                return@listener

            val res = ScaledResolution(mc)

            val w = originalWidth * (scale / 2)
            val h = originalHeight * (scale / 2)

            x += dx
            y += dy

            if(x + w >= res.scaledWidth) {
                x -= dx
                dx *= -1
                changeColor()
            }

            if(y + h >= res.scaledHeight) {
                y -= dy
                dy *= -1
                changeColor()
            }

            if(x <= 0) {
                x -= dx
                dx *= -1
                changeColor()
            }

            if(y <= 0) {
                y -= dy
                dy *= -1
                changeColor()
            }

            drawImage(x, y, w, h, Color(r, g, b, 255))
        }
    }

    private fun changeColor() {
        r = rand.nextInt(255)
        g = rand.nextInt(255)
        b = rand.nextInt(255)
    }

    private fun drawModalRectWithCustomSizedTexture(x: Double, y: Double, u: Float, v: Float, width: Int, height: Int, textureWidth: Float, textureHeight: Float, color: Color) {
        val f = 1.0f / textureWidth
        val f1 = 1.0f / textureHeight
        val tessellator = Tessellator.getInstance()
        val bufferbuilder = tessellator.buffer
        bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR)
        bufferbuilder.pos(x, (y + height), 0.0).tex((u * f).toDouble(), ((v + height) * f1).toDouble()).color(color.red, color.green, color.blue, color.alpha).endVertex()
        bufferbuilder.pos((x + width), (y + height), 0.0).tex(((u + width) * f).toDouble(), ((v + height) * f1).toDouble()).color(color.red, color.green, color.blue, color.alpha).endVertex()
        bufferbuilder.pos((x + width), y, 0.0).tex(((u + width) * f).toDouble(), (v * f1).toDouble()).color(color.red, color.green, color.blue, color.alpha).endVertex()
        bufferbuilder.pos(x, y, 0.0).tex((u * f).toDouble(), (v * f1).toDouble()).color(color.red, color.green, color.blue, color.alpha).endVertex()
        tessellator.draw()
    }

    private fun drawImage(posX: Double, posY: Double, width: Float, height: Float, color: Color) {
        GlStateManager.pushMatrix()
        mc.textureManager.bindTexture(dvdLocation)
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GlStateManager.enableTexture2D()
        val i = (width.toDouble() * scale).toInt()
        val j = (height.toDouble() * scale).toInt()
        drawModalRectWithCustomSizedTexture(posX, posY, i.toFloat(), j.toFloat(), i, j, i.toFloat(), j.toFloat(), color)
        GlStateManager.popMatrix()
    }
}
package com.lambda.client.module.modules.render

import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.opengl.GL11
import org.lwjgl.util.glu.Cylinder
import org.lwjgl.util.glu.GLU

/**
 *@author FadeRainbow
 *@date 2023/6/23
 *@time 21:39
 */
object ChinaHat :Module(
    name = "ChinaHat",
    description = "跟随毛泽东主席建设美好社会主义",
    category = Category.RENDER
) {
    private val mode by setting("Mode", Mode.ChinaHat)
    private val color by setting("Color", ColorHolder(11, 45, 14))
    private val customHatMode by setting("HatMode",CustomHatMode.Filled)
    private val height by setting("Height",0.3f,0f..1f,0.1f,{ mode==Mode.CustomHat})
    private val radius by setting("Radius",0.7f,0f..1.5f,0.1f,{ mode==Mode.CustomHat})

    init {
        safeListener<RenderWorldEvent> {
            if (mc.world == null || mc.player == null) return@safeListener
            GL11.glPushMatrix()
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glShadeModel(GL11.GL_SMOOTH)
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_LINE_SMOOTH)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glDepthMask(false)
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST)
            GL11.glDisable(GL11.GL_CULL_FACE)
            GL11.glColor4f(color.r / 255f, color.g / 255f, color.b / 255f, color.a / 255f)
            GL11.glTranslatef(0f, mc.player.height + 0.4f, 0f)
            GL11.glRotatef(90f, 1f, 0f, 0f)

            val shaft = Cylinder()
            when(mode) {
                Mode.ChinaHat -> {
                    shaft.drawStyle = GLU.GLU_FILL
                    shaft.draw(0f, 0.7f, 0.3f, 60, 1)
                }

                Mode.CustomHat ->{
                    when(customHatMode){
                       CustomHatMode.Line -> shaft.drawStyle = GLU.GLU_LINE
                       CustomHatMode.Filled -> shaft.drawStyle = GLU.GLU_FILL
                    }
                    shaft.draw(0f, radius, height, 60, 1)
                }
            }

            GlStateManager.resetColor()
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GL11.glDisable(GL11.GL_LINE_SMOOTH)
            GL11.glDepthMask(true)
            GL11.glDisable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_CULL_FACE)
            GL11.glPopMatrix()
        }
    }


    enum class Mode {
     ChinaHat,CustomHat
    }
    enum class CustomHatMode {
        Filled,Line
    }
}


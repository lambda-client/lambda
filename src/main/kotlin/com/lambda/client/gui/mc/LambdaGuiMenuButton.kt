package com.lambda.client.gui.mc

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation

class LambdaGuiMenuButton(x: Int, y: Int) : GuiButton(9001, x, y, 20, 20, "L") {
//    override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
//        if (visible) {
//            mc.textureManager.bindTexture(ResourceLocation("lambda/lambda_ico.png"))
//            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
//            val flag = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height
//            var i = 106
//            if (flag) {
//                i += height
//            }
//            drawTexturedModalRect(x, y, 0, i, width, height)
//        }
//    }
}
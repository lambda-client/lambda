package com.lambda.client.gui.hudgui.elements.client

import com.lambda.client.LambdaMod
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.module.modules.client.Capes
import com.lambda.client.util.graphics.VertexHelper
import org.lwjgl.opengl.GL11.glScalef

internal object WaterMark : LabelHud(
    name = "Watermark",
    category = Category.CLIENT,
    description = "Lambda watermark",
    enabledByDefault = true
) {

    override val hudWidth: Float get() = (displayText.getWidth() + 2.0f) / scale
    override val hudHeight: Float get() = displayText.getHeight(2) / scale

    override val closeable: Boolean get() = !Capes.updated || Capes.isPremium

    override fun SafeClientEvent.updateText() {
        displayText.add(LambdaMod.NAME, primaryColor)
        displayText.add(LambdaMod.VERSION_SIMPLE, secondaryColor)
    }

    override fun renderHud(vertexHelper: VertexHelper) {
        val reversedScale = 1.0f / scale
        glScalef(reversedScale, reversedScale, reversedScale)
        super.renderHud(vertexHelper)
    }

    init {
        posX = 0.0f
        posY = 0.0f
    }
}
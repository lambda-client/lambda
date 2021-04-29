package com.lambda.client.plugin.api

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.threads.safeAsyncListener
import net.minecraftforge.fml.common.gameevent.TickEvent

abstract class PluginLabelHud(
    pluginMain: Plugin,
    name: String,
    alias: Array<String> = emptyArray(),
    category: Category,
    description: String,
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false
) : PluginHudElement(pluginMain, name, alias, category, description, alwaysListening, enabledByDefault) {

    override val hudWidth: Float get() = displayText.getWidth() + 2.0f
    override val hudHeight: Float get() = displayText.getHeight(2)

    protected val displayText = TextComponent()

    init {
        safeAsyncListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeAsyncListener
            displayText.clear()
            updateText()
        }
    }

    abstract fun SafeClientEvent.updateText()

    override fun renderHud(vertexHelper: VertexHelper) {
        super.renderHud(vertexHelper)
        displayText.draw(
            Vec2d((width * dockingH.multiplier).toDouble(), (height * dockingV.multiplier).toDouble()),
            horizontalAlign = dockingH,
            verticalAlign = dockingV
        )
    }

}

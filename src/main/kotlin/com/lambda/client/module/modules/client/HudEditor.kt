package com.lambda.client.module.modules.client

import com.lambda.client.event.events.ShutdownEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.gui.hudgui.LambdaHudGui
import com.lambda.client.module.Category
import com.lambda.client.module.Module

object HudEditor : Module(
    name = "HudEditor",
    description = "Edits the Hud",
    category = Category.CLIENT,
    showOnArray = false
) {
    init {
        onEnable {
            if (mc.currentScreen !is LambdaHudGui) {
                ClickGUI.disable()
                mc.displayGuiScreen(LambdaHudGui)
                LambdaHudGui.onDisplayed()
            }
        }

        onDisable {
            if (mc.currentScreen is LambdaHudGui) {
                mc.displayGuiScreen(null)
            }
        }

        listener<ShutdownEvent> {
            disable()
        }
    }
}

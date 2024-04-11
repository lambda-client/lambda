package com.lambda.module.modules.client

import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.mainThread
import java.awt.Color

object ClickGui : Module(
    name = "ClickGui",
    description = "Sexy",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    // General
    val windowRadius by setting("Window Radius", 2.0, 0.0..10.0, 0.1)
    val windowPadding by setting("Window Padding", 2.0, 0.0..10.0, 0.1)
    val buttonHeight by setting("Button Height", 11.0, 8.0..20.0, 0.1)
    val buttonStep by setting("Button Step", 1.0, 0.0..5.0, 0.1)

    init {
        onEnable {
            if (mc.currentScreen != LambdaClickGui) {
                LambdaClickGui.show()
            }
        }

        onDisable {
            if (mc.currentScreen == LambdaClickGui) {
                LambdaClickGui.close()
            }
        }

        unsafeListener<ClientEvent.Shutdown> {
            disable()
        }
    }
}
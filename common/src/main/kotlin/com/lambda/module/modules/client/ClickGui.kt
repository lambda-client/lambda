package com.lambda.module.modules.client

import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode

object ClickGui : Module(
    name = "ClickGui",
    description = "Sexy",
    tag = ModuleTag.CLIENT,
    defaultKeybind = KeyCode.RIGHT_SHIFT
) {
    // General
    val windowRadius by setting("Window Radius", 2.0, 0.0..10.0, 0.1)
    val windowPadding by setting("Window Padding", 2.0, 0.0..10.0, 0.1)
    val buttonHeight by setting("Button Height", 11.0, 8.0..20.0, 0.1)
    val buttonStep by setting("Button Step", 1.0, 0.0..5.0, 0.1)
    val windowBlur by setting("Window Blur", 30, 0..100, 1)

    // Animation
    val openSpeed by setting("Open Speed", 0.6, 0.1..1.0, 0.01)
    val closeSpeed by setting("Close Speed", 0.7, 0.1..1.0, 0.01)

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
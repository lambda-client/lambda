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
    private val page by setting("Page", Page.Colors)

    // General
    val windowRadius by setting("Window Radius", 2.0, 0.0..10.0, 0.1, visibility = { page == Page.General })
    val windowPadding by setting("Window Padding", 2.0, 0.0..10.0, 0.1, visibility = { page == Page.General })
    val buttonHeight by setting("Button Height", 11.0, 8.0..20.0, 0.1, visibility = { page == Page.General })
    val buttonStep by setting("Button Step", 1.0, 0.0..5.0, 0.1, visibility = { page == Page.General })

    // Colors
    val mainColor by setting("Main Color", Color(100, 215, 255), visibility = { page == Page.Colors })
    val backgroundColor by setting("Background Color", Color(0, 0, 0, 80), visibility = { page == Page.Colors })
    val glow by setting("Glow (experimental)", true, visibility = { page == Page.Colors })

    enum class Page {
        General,
        Colors
    }

    private val gui by mainThread {
        LambdaClickGui()
    }

    init {
        onEnable {
            if (mc.currentScreen != gui) {
                gui.show()
            }
        }

        onDisable {
            if (mc.currentScreen == gui) {
                gui.close()
            }
        }

        unsafeListener<ClientEvent.Shutdown> {
            disable()
        }
    }
}
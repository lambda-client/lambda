package com.lambda.module.modules.client

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
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
    val windowRadius by setting("Window Radius", 2.0, 0.0..10.0, 0.1)
    val padding by setting("Padding", 2.0, 0.0..10.0, 0.1)

    // Colors
    val mainColor by setting("Main Color", Color(110, 0, 40), visibility = { page == Page.Colors })
    val backgroundColor by setting("Background Color", Color(35, 15, 20), visibility = { page == Page.Colors })

    enum class Page {
        General,
        Colors
    }

    private val gui by mainThread {
        LambdaClickGui()
    }

    init {
        onEnable {
            mc.currentScreen?.close()
            gui.show()
        }

        listener<TickEvent.Pre> {
            if (mc.currentScreen?.title?.literalString != gui.name) disable()
        }
    }
}
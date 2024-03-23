package com.lambda.module.modules.client

import com.lambda.event.events.KeyPressEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.font.LambdaFont
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode
import com.lambda.util.math.Vec2d
import java.awt.Color

object HUD : Module(
    name = "HUD",
    description = "Visual behaviour configuration",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    val scale by setting("Scale", 2.0, 0.5..4.0, 0.01, description = "UI Scale factor")

    private var y = 30.0

    init {
        val renderer = FontRenderer(LambdaFont.FiraSansRegular).asRenderer

        listener<KeyPressEvent>(alwaysListen = true) {
            if (it.key != KeyCode.K.key) return@listener

            val yLevel = y

            renderer.build {
                position = Vec2d(30.0, yLevel)
                text = "I Love Lambda <3"
                color = Color(130, 130, 250)
            }

            y += 30
        }

        listener<TickEvent.Pre>(alwaysListen = true) {
            renderer.update()
        }

        listener<RenderEvent.GUI.Scaled>(alwaysListen = true) {
            renderer.render()
        }
    }
}
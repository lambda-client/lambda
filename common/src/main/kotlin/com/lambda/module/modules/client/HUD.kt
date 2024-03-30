package com.lambda.module.modules.client

import com.lambda.event.events.KeyPressEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.font.LambdaFont
import com.lambda.graphics.shader.Shader
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

        val vao = VAO(VertexMode.TRIANGLES, VertexAttrib.Group.FONT)
        val shader = Shader("renderer/font")

        listener<RenderEvent.GUI.Scaled>(alwaysListen = true) {
            //renderer.render()
            LambdaFont.FiraSansRegular.bind()
            shader.use()

            val color = Color.WHITE
            val pos1 = Vec2d(10.0, 10.0)
            val pos2 = Vec2d(400.0, 400.0)

            vao.use {
                putQuad(
                    vec2(pos1.x, pos1.y).vec2(0.0, 0.0).color(color).end(),
                    vec2(pos1.x, pos2.y).vec2(0.0, 1.0).color(color).end(),
                    vec2(pos2.x, pos2.y).vec2(1.0, 1.0).color(color).end(),
                    vec2(pos2.x, pos1.y).vec2(1.0, 0.0).color(color).end()
                )

                upload()
                render()
                clear()
            }
        }
    }
}
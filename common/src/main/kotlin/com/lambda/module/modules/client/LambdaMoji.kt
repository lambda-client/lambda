package com.lambda.module.modules.client

import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.gui.api.RenderLayer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.Vec2d

object LambdaMoji : Module(
    name = "LambdaMoji",
    description = "",
    defaultTags = setOf(ModuleTag.CLIENT, ModuleTag.RENDER),
    enabledByDefault = true,
) {
    private val scale by setting("Emoji Scale", 1.0, 0.5..2.0, 0.1)

    private val renderer = RenderLayer()
    private val renderQueue = hashMapOf<List<String>, List<Vec2d>>()

    init {
        listener<TickEvent.Pre> {
            var index = 0
            renderQueue.forEach { (emojis, positions) ->
                emojis.forEachIndexed { emojiIndex, emoji ->
                    val pos = positions[emojiIndex]

                    renderer.font.build(
                        text = emoji,
                        position = Vec2d(pos.x, pos.y * (index.toDouble() + 1)),
                        scale = scale,
                    )
                }

                index++
            }
        }

        listener<RenderEvent.GUI.Fixed> {
            renderer.render()
        }
    }

    fun add(emojis: List<String>, positions: List<Vec2d>) {
        renderQueue[emojis] = positions
    }
}

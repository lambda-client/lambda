/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.client

import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
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
        listen<TickEvent.Pre> {
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

        listen<RenderEvent.GUI.Fixed> {
            renderer.render()
        }
    }

    fun add(emojis: List<String>, positions: List<Vec2d>) {
        renderQueue[emojis] = positions
    }
}

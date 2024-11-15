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
    val scale by setting("Emoji Scale", 1.0, 0.5..1.5, 0.1)
    val suggestions by setting("Chat Suggestions", true)

    private val renderer = RenderLayer()
    private val renderQueue = mutableListOf<Pair<String, Vec2d>>()

    init {
        listener<RenderEvent.GUI.Scaled> {
            renderQueue.forEach { (text, position) ->
                renderer.font.build(text, position, scale = scale)
            }

            renderer.render()
            renderQueue.clear()
        }
    }

    fun push(text: String, position: Vec2d) = renderQueue.add(Pair(text, position))
}

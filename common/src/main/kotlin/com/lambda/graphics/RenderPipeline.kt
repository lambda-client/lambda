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

package com.lambda.graphics

import com.lambda.core.Loadable
import com.lambda.event.EventFlow.post
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.impl.DynamicESPRenderer
import com.lambda.graphics.renderer.esp.impl.StaticESPRenderer

object RenderPipeline : Loadable {
    // Updates once a tick, stays fixed, uses less memory
    val STATIC_ESP = StaticESPRenderer()

    // Updates once a tick, interpolates within frames
    val DYNAMIC_ESP = DynamicESPRenderer()

    init {
        // Ticked 3d renderers update
        listener<TickEvent.Post> {
            STATIC_ESP.clear()
            RenderEvent.StaticESP().post()
            STATIC_ESP.upload()

            DYNAMIC_ESP.clear()
            RenderEvent.DynamicESP().post()
            DYNAMIC_ESP.upload()
        }

        // 3d renderers drawcall
        listener<RenderEvent.World> {
            STATIC_ESP.render()
            DYNAMIC_ESP.render()
        }
    }
}
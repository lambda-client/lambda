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

package com.lambda.event.events

import com.lambda.Lambda.mc
import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.graphics.RenderPipeline
import com.lambda.util.math.Vec2d

sealed class RenderEvent {
    class World : Event

    class StaticESP : Event {
        val renderer = RenderPipeline.STATIC_ESP
    }

    class DynamicESP : Event {
        val renderer = RenderPipeline.DYNAMIC_ESP
    }

    sealed class GUI(val scale: Double) : Event {
        class Scaled(scaleFactor: Double) : GUI(scaleFactor)
        class HUD(scaleFactor: Double) : GUI(scaleFactor)
        class Fixed : GUI(1.0)

        val screenSize = Vec2d(mc.window.framebufferWidth, mc.window.framebufferHeight) / scale
    }

    class UpdateTarget : ICancellable by Cancellable()
}

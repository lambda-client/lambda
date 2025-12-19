/*
 * Copyright 2025 Lambda
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

import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.RenderMain
import com.lambda.graphics.renderer.esp.ShapeBuilder

fun Any.onStaticRender(block: SafeContext.(ShapeBuilder) -> Unit) =
	listen<RenderEvent.Upload> { block(ShapeBuilder(RenderMain.StaticESP)) }

fun Any.onDynamicRender(block: SafeContext.(ShapeBuilder) -> Unit) =
	listen<RenderEvent.Upload> { block(ShapeBuilder(RenderMain.DynamicESP)) }

sealed class RenderEvent {
    object Upload : Event
    object Render : Event

    class UpdateTarget : ICancellable by Cancellable()
}

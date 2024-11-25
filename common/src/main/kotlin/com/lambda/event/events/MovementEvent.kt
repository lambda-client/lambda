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

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.client.input.Input

sealed class MovementEvent {
    class Pre : Event
    class Post : Event

    sealed class Travel {
        class Pre : ICancellable by Cancellable()
        class Post : Event
    }

    data class InputUpdate(
        val input: Input,
        var slowDown: Boolean,
        var slowDownFactor: Float,
    ) : Event

    data class Sprint(var sprint: Boolean) : Event
    data class Sneak(var sneak: Boolean) : Event

    data class ClipAtLedge(
        var clip: Boolean,
    ) : Event

    data class Jump(var height: Double) : ICancellable by Cancellable()
    class SlowDown : ICancellable by Cancellable()
}

/*
 * Copyright 2026 Lambda
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
import net.minecraft.client.sound.SoundInstance
import java.util.*

sealed class ClientEvent {
    /**
     * Triggered upon client initialization
     */
    class Startup : Event

    /**
     * Triggered upon client shutdown
     */
    class Shutdown : Event

    /**
     * Triggered upon game logic tick
     *
     * @property speed The speed of the timer.
     */
    data class TimerUpdate(var speed: Double) : Event

    /**
     * Triggered before playing a sound
     */
    data class Sound(val sound: SoundInstance) : ICancellable by Cancellable()

    /**
     * Represents a fixed tick event in the application.
     *
     * A fixed tick can be used to execute a specific task consistently at regular intervals, based on the provided
     * timer task. This event is part of the event system and can be subscribed to for handling the specified timer task.
     *
     * @property timerTask The task that is executed during this fixed tick event.
     */
    data class FixedTick(val timerTask: TimerTask) : Event
}

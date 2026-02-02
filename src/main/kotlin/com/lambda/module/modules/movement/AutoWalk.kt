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

package com.lambda.module.modules.movement

import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Timer
import com.lambda.util.player.MovementUtils.update
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class AutoWalk : Module(
	name = "AutoWalk",
	description = "Automatically makes your character walk forward",
	tag = ModuleTag.MOVEMENT,
) {
	val pause by setting("Enable Pause", false)
	val pauseTime by setting("Pause Time", 0, 0..200, 5, unit = "ms") { pause }
	val pauseInterval by setting("Pause Interval", 0, 0..5000, 100, unit = "ms") { pause }

	var pauseTimer = Timer()
	val pauseIntervalTimer = Timer()

	var state = State.Walking

	init {
		listen<TickEvent.Pre> {
			if (!pause) return@listen
			when (state) {
				State.Walking -> {
					if (!pauseIntervalTimer.timePassed(pauseInterval.toDuration(DurationUnit.MILLISECONDS))) return@listen
					pauseIntervalTimer.reset()
					pauseTimer.reset()
					state = State.Pausing
				}
				State.Pausing -> {
					if (!pauseTimer.timePassed(pauseTime.toDuration(DurationUnit.MILLISECONDS))) return@listen
					pauseTimer.reset()
					pauseIntervalTimer.reset()
					state = State.Walking
				}
			}
		}

		onEnable {
			pauseTimer.reset()
			pauseIntervalTimer.reset()
			state = State.Walking
		}

		listen<MovementEvent.InputUpdate> { event ->
			if (pause && state == State.Pausing) return@listen
			event.input.update(forward = 1.0)
		}
	}

	enum class State {
		Walking,
		Pausing
	}
}
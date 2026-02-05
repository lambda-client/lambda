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
import com.lambda.util.player.MovementUtils.forward
import com.lambda.util.player.MovementUtils.strafe
import com.lambda.util.player.MovementUtils.update
import net.minecraft.util.math.Vec2f
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class AutoWalk : Module(
	name = "AutoWalk",
	description = "Automatically makes your character walk forward",
	tag = ModuleTag.MOVEMENT,
) {
	val pause by setting("Enable Pause", false)

	val limitSpeed by setting("Limit Speed", false)
	val speed by setting("Speed", 0.5, 0.1..1.0, 0.05) { limitSpeed }

	init {
		listen<MovementEvent.InputUpdate> { event ->
			event.input.update(forward = 1.0)
			if (limitSpeed) event.input.movementVector = Vec2f(event.input.strafe, event.input.forward).normalize().multiply(speed.toFloat())
		}
	}
}
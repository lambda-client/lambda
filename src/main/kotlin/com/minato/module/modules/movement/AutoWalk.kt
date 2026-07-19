
package com.minato.module.modules.movement

import com.minato.event.events.MovementEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.player.MovementUtils.forward
import com.minato.util.player.MovementUtils.strafe
import com.minato.util.player.MovementUtils.update
import net.minecraft.util.math.Vec2f

@Suppress("unused")
object AutoWalk : Module(
	name = "AutoWalk",
	description = "Automatically makes your character walk forward",
	tag = ModuleTag.MOVEMENT,
) {
	val limitSpeed by setting("Limit Speed", false)
	val speed by setting("Speed", 0.5, 0.1..1.0, 0.05) { limitSpeed }

	init {
		listen<MovementEvent.InputUpdate> { event ->
			event.input.update(forward = 1.0)
			if (limitSpeed) event.input.movementVector = Vec2f(event.input.strafe, event.input.forward).normalize().multiply(speed.toFloat())
		}
	}
}
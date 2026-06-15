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

package com.lambda.module.modules.movement.elytrafly.modes

import com.lambda.config.Config
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.module.modules.movement.BetterFirework.startFirework
import com.lambda.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.lambda.module.modules.movement.elytrafly.ElytraFlyMode
import com.lambda.util.Timer
import com.lambda.util.player.SlotUtils.hotbarStacks
import com.lambda.util.player.SlotUtils.inventoryStacks
import com.lambda.util.player.hasFirework
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.Vec3d
import kotlin.time.Duration.Companion.seconds

class GrimControlElytraFly(override val c: Config) : ElytraFlyMode(FlyMode.GrimControl) {
	private val inventory by c.setting("Inventory", true, "Allow using fireworks from the players inventory")

	private var flipFlop = false
	private var lastDuration = 1.0
	private val fireworkTimer = Timer()

	init {
		listen<TickEvent.Pre> {
			if (!player.isGliding) return@listen
			if (fireworkTimer.timePassed(lastDuration.seconds)) {
				findFirework()?.let {
					lastDuration = (it.get(DataComponentTypes.FIREWORKS)?.flightDuration ?: 1) * 0.5 + 0.5
					startFirework(inventory)
					fireworkTimer.reset()
				}
			}

			var vec = Vec3d.ZERO
			val yaw = player.yaw
			if (mc.options.forwardKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw))
			if (mc.options.backKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw + 180f))
			if (mc.options.leftKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw - 90f))
			if (mc.options.rightKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw + 90f))
			if (mc.options.jumpKey.isPressed) vec = vec.add(Vec3d(0.0, 1.0, 0.0))
			if (mc.options.sneakKey.isPressed) vec = vec.add(Vec3d(0.0, -1.0, 0.0))
			if (vec.lengthSquared() < 1e-4 && player.hasFirework) {
				if (flipFlop) {
					flipFlop = false
					rotationRequest { rotation(0f, 0f) }
				} else {
					flipFlop = true
					rotationRequest { rotation(180f, 0f) }
				}
			} else {
				val rot = vec.yawAndPitch
				rotationRequest { rotation(rot.y, rot.x) }
			}.submit()
		}
	}

	private fun SafeContext.findFirework(): ItemStack? {
		val stack = Items.FIREWORK_ROCKET.select()
		return stack.bestItemMatch(player.hotbarStacks) ?: if (inventory) stack.bestItemMatch(player.inventoryStacks) else null
	}
}
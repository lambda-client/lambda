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

package com.lambda.module.modules.player

import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.hideAllExcept
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.manager.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.manager.managers.rotating.RotationRequestBuilder.Companion.rotationRequest
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.util.math.distSq
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.mob.EndermanEntity
import net.minecraft.item.Items
import net.minecraft.util.math.Vec3d

@Suppress("unused")
object EndermanLook : Module(
	name = "EndermanLook",
	description = "Either stares at every enderman or stops you from staring at them",
	tag = ModuleTag.PLAYER,
) {
	private val mode by setting("Mode", Mode.Away, "Whether to stare down endermen or avoid their gaze")
	private val stunHostiles by setting("Stun Hostiles", true, "Stare back at already provoked endermen to freeze them") { mode == Mode.Away }
	private val disableWhileGliding by setting("Disable While Gliding", true, "Disables when gliding with an elytra")

	private const val STARE_CONE = 0.025 // vanilla to aggro endermen

	init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::rotationConfig)
			}

		listen<TickEvent.Pre> {
			if (player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.CARVED_PUMPKIN) ||
				player.abilities.creativeMode ||
				(disableWhileGliding && player.isGliding)) return@listen

			val endermen = world.entities
				.filterIsInstance<EndermanEntity>()
				.filter { it.isAlive && player.canSee(it) }

			when (mode) {
				Mode.At -> closest(endermen.filter { !it.isAngry })
					?.let { stareAt(it) }

				Mode.Away -> {
					val (stunnable, rest) = endermen.partition { it.isAngry && stunHostiles }

					closest(stunnable)?.let { stareAt(it) }
						?: closest(rest.filter { isStaringAt(it) })?.let { lookAway() }
				}
			}
		}
	}

	private fun SafeContext.stareAt(enderman: EndermanEntity) =
		rotationRequest { rotation(player.eyePos.rotationTo(enderman.eyePos)) }.submit()

	private fun lookAway() =
		rotationRequest { pitch(90f) }.submit()

	private fun SafeContext.closest(endermen: List<EndermanEntity>) =
		endermen.minByOrNull { it.eyePos distSq player.eyePos }

	private fun SafeContext.isStaringAt(enderman: EndermanEntity): Boolean {
		val diff = Vec3d(
			enderman.x - player.x,
			enderman.eyeY - player.eyeY,
			enderman.z - player.z
		)

		return player.getRotationVec(1f).normalize()
			.dotProduct(diff.normalize()) > 1.0 - STARE_CONE / diff.length()
	}

	enum class Mode {
		At,
		Away
	}
}

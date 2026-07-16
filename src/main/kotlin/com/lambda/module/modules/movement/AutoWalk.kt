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

import com.lambda.config.Group
import com.lambda.context.SafeContext
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.breaking.BreakManager
import com.lambda.interaction.managers.interacting.InteractManager
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.MathUtils.toDouble
import com.lambda.util.player.MovementUtils.forward
import com.lambda.util.player.MovementUtils.sneaking
import com.lambda.util.player.MovementUtils.sprinting
import com.lambda.util.player.MovementUtils.strafe
import com.lambda.util.player.MovementUtils.update
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.util.math.Vec2f

@Suppress("unused")
object AutoWalk : Module(
	name = "AutoWalk",
	description = "Automatically walks in the configured direction when certain conditions are met",
	tag = ModuleTag.MOVEMENT,
) {
	@Group("Movement") private val walkForward by setting("Forward", true, "Automatically walks forward")
	@Group("Movement") private val walkBackward by setting("Backward", false, "Automatically walks backward")
	@Group("Movement") private val strafeLeft by setting("Strafe Left", false, "Automatically strafes left")
	@Group("Movement") private val strafeRight by setting("Strafe Right", false, "Automatically strafes right")
	@Group("Movement") private val sneak by setting("Sneak", false, "Automatically sneaks")
	@Group("Movement") private val sprint by setting("Sprint", false, "Automatically sprints")

	@Group("Speed") private val limitSpeed by setting("Limit Speed", false)
	@Group("Speed") private val speed by setting("Speed", 0.5, 0.1..1.0, 0.05) { limitSpeed }

	@Group("Pausing") private val pauseWhileMining by setting("Pause While Mining", false, "Pauses walking while breaking blocks or when breaks are queued")
	@Group("Pausing") private val pauseWhilePlacing by setting("Pause While Placing", false, "Pauses walking while placing blocks or when places are queued - only works with blocks placed by a lambda module")
	@Group("Pausing") private val pauseIfPlacedLastTick by setting("Pause If Placed Last Tick", false, "Pauses walking on the tick after placing a block - Works with all placing")

	private var didPlaceLastTick = false

	private val SafeContext.breakQueued
		get() = interaction.isBreakingBlock ||
			BreakManager.activeThisTick ||
			BreakManager.queuedRequest != null ||
			BreakManager.blockedPositions.isNotEmpty()

	private val placeQueued
		get() = InteractManager.activeThisTick ||
			InteractManager.queuedRequest != null ||
			InteractManager.blockedPositions.isNotEmpty()

	init {
		onDisable { didPlaceLastTick = false }

		listen<PacketEvent.Send.Pre> { event ->
			if (event.packet is PlayerInteractBlockC2SPacket) didPlaceLastTick = true
		}

		listen<MovementEvent.InputUpdate> { event ->
			val placedLastTick = didPlaceLastTick
			didPlaceLastTick = false

			if (pauseIfPlacedLastTick && placedLastTick ||
				pauseWhileMining && breakQueued ||
				pauseWhilePlacing && placeQueued
			) return@listen

			val input = event.input
			if (walkForward || walkBackward) input.forward = (walkForward.toDouble() - walkBackward.toDouble()).toFloat()
			if (strafeLeft || strafeRight) input.strafe = (strafeRight.toDouble() - strafeLeft.toDouble()).toFloat()
			if (sneak || sprint) input.update(sneak = sneak || input.sneaking, sprint = sprint || input.sprinting)

			if (limitSpeed) input.movementVector = Vec2f(input.strafe, input.forward).normalize().multiply(speed.toFloat())
		}
	}
}

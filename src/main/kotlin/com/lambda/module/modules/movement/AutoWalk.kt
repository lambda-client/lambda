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
import com.lambda.util.player.MovementUtils.roundedForward
import com.lambda.util.player.MovementUtils.roundedStrafing
import com.lambda.util.player.MovementUtils.sneaking
import com.lambda.util.player.MovementUtils.sprinting
import com.lambda.util.player.MovementUtils.update
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket

@Suppress("unused")
object AutoWalk : Module(
	name = "AutoWalk",
	description = "Automatically walks in the configured direction when certain conditions are met",
	tag = ModuleTag.MOVEMENT,
) {
	private const val MOVEMENT_GROUP = "Movement"
	private const val PAUSING_GROUP = "Pausing"

	@Group(MOVEMENT_GROUP) private val walkForward by setting("Forward", true, "Automatically walks forward")
	@Group(MOVEMENT_GROUP) private val walkBackward by setting("Backward", false, "Automatically walks backward")
	@Group(MOVEMENT_GROUP) private val strafeLeft by setting("Strafe Left", false, "Automatically strafes left")
	@Group(MOVEMENT_GROUP) private val strafeRight by setting("Strafe Right", false, "Automatically strafes right")
	@Group(MOVEMENT_GROUP) private val sneak by setting("Sneak", false, "Automatically sneaks")
	@Group(MOVEMENT_GROUP) private val sprint by setting("Sprint", false, "Automatically sprints")

	@Group(PAUSING_GROUP) private val pauseWhileMining by setting("Pause While Mining", false, "Pauses walking while breaking blocks or when breaks are queued")
	@Group(PAUSING_GROUP) private val pauseWhilePlacing by setting("Pause While Placing", false, "Pauses walking while placing blocks or when places are queued - only works with blocks placed by a lambda module")
	@Group(PAUSING_GROUP) private val pauseAfterPlacing by setting("Pause After Placing", false, "Pauses walking for a period after placing a block - Works with all placing")
	@Group(PAUSING_GROUP) private val ticksToWaitAfterPlacing by setting("Ticks To Wait After Placing", 1, 1..20, 1, "Extends the pause after placing to this many ticks") { pauseAfterPlacing }

	private var placeWaitTicks = 0

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
		listen<PacketEvent.Send.Pre> { event ->
			if (event.packet is PlayerInteractBlockC2SPacket && pauseAfterPlacing)
				placeWaitTicks = ticksToWaitAfterPlacing
		}

		listen<MovementEvent.InputUpdate> { event ->
			val waitingAfterPlacing = placeWaitTicks > 0
			if (placeWaitTicks > 0) placeWaitTicks--

			if (pauseAfterPlacing && waitingAfterPlacing ||
				pauseWhileMining && breakQueued ||
				pauseWhilePlacing && placeQueued
			) return@listen

			val input = event.input
			val forward =
				if (walkForward || walkBackward) walkForward.toDouble() - walkBackward.toDouble()
				else input.roundedForward
			val strafe =
				if (strafeLeft || strafeRight) strafeLeft.toDouble() - strafeRight.toDouble()
				else input.roundedStrafing

			input.update(
				forward = forward,
				strafe = strafe,
				sneak = sneak || input.sneaking,
				sprint = sprint || input.sprinting
			)
		}
	}
}

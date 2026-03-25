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

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.hotbar.HotbarRequest
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.containers.HotbarContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Timer
import net.minecraft.item.ExperienceBottleItem
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.util.Hand
import kotlin.time.Duration.Companion.milliseconds


object AutoLevel : Module(
	name = "AutoLevel",
	description = "Automatically uses xp bottles to level up to a certain level",
	tag = ModuleTag.PLAYER
) {
	var targetLevel by setting("Target Level", 2, 1..100)
	var burstAmount by setting("Burst Amount", 5, 1..100)
	var interval by setting("Interval", 5, 0..100, unit = "ticks")

	var rotate by setting("Rotate", true)
	var packetRotate by setting("Packet Rotate", true)

	val timer = Timer()

	init {
		setDefaultAutomationConfig {
			applyEdits {
				hideGroups(eatConfig, buildConfig, breakConfig, interactConfig, inventoryConfig)
				hotbarConfig::tickStageMask.edit {
					defaultValue(mutableSetOf(TickEvent.Pre, TickEvent.Input.Post))
				}
			}
		}
		listen<TickEvent.Pre> {
			if (player.experienceLevel >= targetLevel) return@listen

			if (rotate && !packetRotate)
				rotationRequest {
					pitch(90f)
				}.submit()
			if (timer.timePassed((50 * interval).milliseconds)) {
				withXp {
					var pitch = if (rotate) 90f else RotationManager.activeRotation.pitch.toFloat()
					var yaw = RotationManager.activeRotation.yaw.toFloat()
					repeat(burstAmount) {
						interaction.sendSequencedPacket(world) { seq ->
							PlayerInteractItemC2SPacket(Hand.MAIN_HAND, seq, yaw, pitch)
						}
					}
					timer.reset()
				}
			}
		}
	}

	private fun SafeContext.withXp(block: () -> Unit) {
		if (player.mainHandStack?.item == Items.EXPERIENCE_BOTTLE) {
			block()
			return
		}
		val hotbarSlot = StackSelection.selectStack(count = 1) {
			isItem<ExperienceBottleItem>()
		}.filterSlots(HotbarContainer.slots).firstOrNull() ?: return
		val done = HotbarRequest(
			hotbarSlot.index,
			this@AutoLevel
		).submit(queueIfMismatchedStage = false).done
		if (done) block()
	}
}
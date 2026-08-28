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
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.PlayerPacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.inventory.StackSelectionBuilder.Companion.select
import com.lambda.interaction.inventory.container.containers.HotbarContainer
import com.lambda.interaction.inventory.container.containers.InventoryContainer
import com.lambda.interaction.manager.managers.rotating.RotationManager
import com.lambda.interaction.manager.managers.rotating.RotationRequestBuilder.Companion.rotationRequest
import com.lambda.module.modules.movement.BetterFirework.startFirework
import com.lambda.module.modules.movement.elytrafly.ElytraFly
import com.lambda.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.lambda.module.modules.movement.elytrafly.ElytraFly.hasFirework
import com.lambda.module.modules.movement.elytrafly.ElytraFly.withinFireworkTimeframe
import com.lambda.module.modules.movement.elytrafly.ElytraFlyMode
import com.lambda.module.modules.render.Freecam
import com.lambda.util.NamedEnum
import com.lambda.util.TickTimer
import com.lambda.util.math.MathUtils.toFloat
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.Vec3d

class GrimControlElytraFly(
	override val c: Config
) : ElytraFlyMode(FlyMode.GrimControl) {
	private val inventory by c.setting("Inventory", true, "Allow using fireworks from the players inventory")
	private val upAngle by c.setting("Up Angle", 45f, 0f..90f, 0.1f)
	private val downAngle by c.setting("Down Angle", 20f, 0f..90f, 0.1f)
	val flipFlopMode by c.setting("Flip Flop Mode", FlipFlopMode.None)
	private val packetGap by c.setting("Packet Gap", 20, 0..100, 1, "The gap between allowing player movement packets to pass") { flipFlopMode != FlipFlopMode.None }

	@Suppress("unused")
	enum class FlipFlopMode(
		override val displayName: String,
		val isFlipFlopping: (hasFirework: Boolean) -> Boolean
	) : NamedEnum {
		Full("Full", { true }),
		WithFirework("With Firework", { hasFirework -> hasFirework }),
		None("None", { false })
	}

	private var flipFlop = false
	private var rotFlipFlop = false
	var still = false
	var moving = false
	private val stillTickTimer = TickTimer()

	init {
		listen<TickEvent.Pre>({ 1 }) {
			if (!player.isGliding) return@listen

			if (fakeGliding) flyOrFakeFly()

			var vec = Vec3d.ZERO
			val yaw = player.yaw
			var specificYaw = false
			if (mc.options.forwardKey.isPressed) {
				vec = vec.add(Vec3d.fromPolar(0f, yaw))
				specificYaw = true
			}
			if (mc.options.backKey.isPressed) {
				vec = vec.add(Vec3d.fromPolar(0f, yaw + 180f))
				specificYaw = true
			}
			if (mc.options.leftKey.isPressed) {
				vec = vec.add(Vec3d.fromPolar(0f, yaw - 90f))
				specificYaw = true
			}
			if (mc.options.rightKey.isPressed) {
				vec = vec.add(Vec3d.fromPolar(0f, yaw + 90f))
				specificYaw = true
			}
			if (mc.options.jumpKey.isPressed) {
				vec =
					if (specificYaw) Vec3d.fromPolar(-upAngle, vec.yawAndPitch.y)
					else Vec3d.fromPolar(-90f, yaw)
			}
			if (mc.options.sneakKey.isPressed) {
				vec =
					if (specificYaw) Vec3d.fromPolar(downAngle, vec.yawAndPitch.y)
					else Vec3d.fromPolar(90f, yaw)
			}

			val prevStill = still
			still = vec.lengthSquared() < 1e-4 || Freecam.isEnabled

			if (still) {
				moving = false
				if (!flipFlopMode.isFlipFlopping(hasFirework)) {
					stillTickTimer.tick()
					return@listen
				}
				rotationRequest { rotation(if (flipFlop) 0.0 else 180.0, 0.0) }.submit()
				flipFlop = !flipFlop
				if (flipFlopMode == FlipFlopMode.WithFirework) return@listen
			}

			if (!hasFirework && !withinFireworkTimeframe()) {
				if (findFirework() == null) return@listen
				startFirework(inventory)
			}

			if (still) return@listen

			val rot = vec.yawAndPitch
			rotationRequest { rotation(rot.y, rot.x) }.submit()
			if (hasFirework) {
				moving = true
				if (prevStill) {
					val activeRot = RotationManager.activeRotation
					player.velocity = ElytraFly.getFireworkTargetVelocity(activeRot.pitchF, activeRot.yawF)
				}
			}
		}

		listen<PlayerPacketEvent.Send> { event ->
			if (!player.isGliding) return@listen
			val rot = RotationManager.activeRotation
			val flipFlop = rotFlipFlop.toFloat() * 0.0001f
			rotFlipFlop = !rotFlipFlop
			event.packet =
				PlayerMoveC2SPacket.Full(
					player.pos,
					rot.yawF + flipFlop,
					rot.pitchF,
					player.isOnGround,
					player.horizontalCollision
				)
		}

		listen<PacketEvent.Send.Pre> { event ->
			if (event.packet !is PlayerMoveC2SPacket ||
				!player.isGliding ||
				flipFlopMode.isFlipFlopping(hasFirework) ||
				moving
				) return@listen
			if (stillTickTimer.hasSurpassed(packetGap)) {
				stillTickTimer.reset()
				return@listen
			}
			event.cancel()
		}
	}

	private fun findFirework(): ItemStack? {
		val stack = Items.FIREWORK_ROCKET.select()
		return stack.bestMatch(HotbarContainer.stacks) ?: if (inventory) stack.bestMatch(InventoryContainer.stacks) else null
	}

	override fun pausingMovement() = still
}
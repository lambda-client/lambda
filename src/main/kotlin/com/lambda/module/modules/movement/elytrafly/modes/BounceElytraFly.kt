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
import com.lambda.config.Group
import com.lambda.context.SafeContext
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.BaritoneHandler
import com.lambda.interaction.managers.inventory.InventoryRequest
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.module.hud.Speedometer
import com.lambda.module.modules.movement.BetterFirework.isElytraEquipped
import com.lambda.module.modules.movement.elytrafly.ElytraFly
import com.lambda.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.lambda.module.modules.movement.elytrafly.ObstaclePassingMode
import com.lambda.module.modules.movement.elytrafly.PasserSettings
import com.lambda.threading.runSafe
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.SpeedUnit
import com.lambda.util.TickTimer
import com.lambda.util.player.SlotUtils.armorSlots
import com.lambda.util.player.SlotUtils.hotbarSlots
import com.lambda.util.player.SlotUtils.inventorySlots
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.Items
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.Vec3d
import kotlin.math.abs

class BounceElytraFly(
	override val c: Config
) : ObstaclePassingMode(c, FlyMode.Bounce) {
	companion object {
		private const val Y_MOTION_GROUP = "Y Motion"
		private const val BOUNCE_OBSTACLE_PASSER_GROUP = "Bounce Obstacle Passer"
	}

	private val takeoff by c.setting("Takeoff", true, "Automatically jumps and initiates gliding")
	private val autoPitch by c.setting("Auto Pitch", true, "Automatically pitches the players rotation down to bounce at faster speeds")
	private val pitch by c.setting("Pitch", 80.0, -90.0..90.0, 0.000001) { autoPitch }
	private val jump by c.setting("Jump", true, "Automatically jumps")
	private val fakeFly by c.setting("Fake Fly", false, "Rapidly swaps the chestplate and elytra to give the appearance the player is flying without an elytra. May also reduce durability loss")
	private val flagPause by c.setting("FlagPause Pause", 5, 0..100, 1, "How long to pause if the server flags you for a movement check", "ticks")
	private val minimizePackets by c.setting("Minimize Packets", true, "Shrinks the amount of start fly packets sent to the server as much as possible")

	@Group(Y_MOTION_GROUP) val yMotionSetting by c.setting("Y Motion", false, "Cancels the players y velocity to aid speed")
	@Group(Y_MOTION_GROUP) val onlyOnDiagonal: Boolean by c.setting("Only On Diagonal", true, "Only use y motion when the player is flying on a non-axial angle") { yMotionSetting }
	@Group(Y_MOTION_GROUP) val minDiagonalAngle by c.setting("Min Diagonal Angle", 15.0, 0.0..180.0, 0.1, "The minimum angle the player must be flying to use y motion") { yMotionSetting && onlyOnDiagonal }
	@Group(Y_MOTION_GROUP) val yMotionStartSpeed by c.setting("Y Motion Start Speed", 30, 0..40, 1, unit = "bps") { yMotionSetting }
	@Group(Y_MOTION_GROUP) val speedLimit by c.setting("Speed Limit", 110, 10..400, 1, unit = "bps") { yMotionSetting }
	context(safeContext: SafeContext)
	private val yMotion
		get() = yMotionSetting &&
				(!onlyOnDiagonal || abs(RotationManager.activeRotation.yaw % 90) > minDiagonalAngle) &&
				safeContext.player.isOnGround &&
				safeContext.player.isGliding &&
				Speedometer.calculateSpeed(true, SpeedUnit.BlocksPerSecond).let { speed ->
					speed > yMotionStartSpeed && speed < speedLimit
				}

	@Group(BOUNCE_OBSTACLE_PASSER_GROUP) override val passerConfig by c.configBlock(PasserSettings(c))

	private var jumpThisTick = false
	private var prevGliding: Boolean? = null
	private val pauseTimer = TickTimer()

	private val ClientPlayerEntity.canTakeoff: Boolean
		get() = (isOnGround || canOpenElytra) && (isElytraEquipped xor fakeFly)

	private val ClientPlayerEntity.canOpenElytra: Boolean
		get() = !isGliding &&
				!isClimbing &&
				!isTouchingWater &&
				!abilities.flying &&
				!isOnGround &&
				!this.hasVehicle() &&
				!this.hasStatusEffect(StatusEffects.LEVITATION)

	init {
		listen<TickEvent.Pre> {
			pauseTimer.tick()

			if (autoPitch) rotationRequest { pitch(pitch) }.submit()

			if (handlePassingObstacles()) return@listen

			if (!pauseTimer.hasSurpassed(flagPause)) return@listen

			if (!player.isGliding) {
				if (takeoff && player.canTakeoff) {
					if (player.canOpenElytra) {
						player.startGliding()
						startFlyPacket()
					} else jumpThisTick = true
				}
				return@listen
			}

			if (minimizePackets && player.getFlag(Entity.GLIDING_FLAG_INDEX) && !fakeFly && !yMotion) return@listen
			
			if (!fakeFly) {
				fly()
				return@listen
			}

			player.inventory.equipment.get(EquipmentSlot.CHEST).let { chestStack ->
				if (chestStack.item == Items.ELYTRA) {
					logError("Fake Fly requires that you don't have an elytra equipped")
					ElytraFly.disable()
					return@listen
				}
			}

			val elytraSlot = findElytra() ?: run {
				logError("Fake Fly requires an elytra in your inventory, preferably in your hotbar.")
				ElytraFly.disable()
				return@listen
			}
			val elytraInHotbar = elytraSlot.index in 0..8

			val chestSlot = player.armorSlots[1]
			val chestSlotEmpty = chestSlot.stack.isEmpty

			fun InventoryRequest.InvRequestBuilder.swapChest() {
				if (elytraInHotbar) swap(chestSlot.id, elytraSlot.index)
				else {
					moveSlot(elytraSlot.id, chestSlot.id)
					if (!chestSlotEmpty) pickup(elytraSlot.id)
				}
			}

			inventoryRequest {
				swapChest()
				action { fly() }
				swapChest()
			}.submit(false)
		}

		listen<MovementEvent.InputUpdate> { event ->
			if ((player.isGliding && jump) || jumpThisTick) {
				event.input.jump()
				jumpThisTick = false
			}
		}

		onFlag { pauseTimer.reset() }
	}

	fun SafeContext.findElytra(): Slot? =
		selectStack {
			isItem(Items.ELYTRA)
				.and { it.damage < it.maxDamage }
		}.run {
			filterSlots(player.hotbarSlots)
				.firstOrNull()
				?: filterSlots(player.inventorySlots)
					.firstOrNull()
		}

	fun SafeContext.fly() {
		player.setFlag(Entity.GLIDING_FLAG_INDEX, true)
		startFlyPacket()
	}

	fun getModifiedVelocity(original: Vec3d) =
		runSafe {
			if (!yMotion) return@runSafe original
			else Vec3d(original.x, 0.0, original.z)
		} ?: original

	override fun isGliding(): Boolean? = runSafe {
		val original: Boolean = player.getFlag(Entity.GLIDING_FLAG_INDEX)
		return if (
			prevGliding == true &&
			pauseTimer.hasSurpassed(flagPause) &&
			!BaritoneHandler.isActive) true
		else {
			prevGliding = original
			original
		}
	}
}
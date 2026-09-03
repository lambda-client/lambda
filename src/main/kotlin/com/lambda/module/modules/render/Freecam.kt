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

package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.editSetting
import com.lambda.config.entries.Setting.Companion.onValueChange
import com.lambda.config.hideAllExcept
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.RotationMode
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.extension.rotation
import com.lambda.util.math.Vec2d
import com.lambda.util.math.dist
import com.lambda.util.math.interpolate
import com.lambda.util.math.normal
import com.lambda.util.math.plus
import com.lambda.util.math.times
import com.lambda.util.player.MovementUtils.calcMoveRad
import com.lambda.util.player.MovementUtils.cancel
import com.lambda.util.player.MovementUtils.handledByBaritone
import com.lambda.util.player.MovementUtils.isInputting
import com.lambda.util.player.MovementUtils.movementVector
import com.lambda.util.player.MovementUtils.newMovementInput
import com.lambda.util.player.MovementUtils.roundedForward
import com.lambda.util.player.MovementUtils.roundedStrafing
import com.lambda.util.player.MovementUtils.verticalMovement
import com.lambda.util.player.RotationUtils.lookAt
import com.lambda.util.world.raycast.RayCastUtils.orMiss
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.option.Perspective
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sign

object Freecam : Module(
	name = "Freecam",
	description = "Move your camera freely",
	tag = ModuleTag.RENDER,
	autoDisable = true,
) {
	private val mode by setting("Mode", Mode.Free, "Freecam movement mode")
	private val speed by setting("Speed", 0.5, 0.1..1.0, 0.1, "Freecam movement speed", unit = "m/s") { mode == Mode.Free }
	private val sprint by setting("Sprint Multiplier", 3.0, 0.1..10.0, 0.1, description = "Set below 1.0 to fly slower on sprint.") { mode == Mode.Free }
	private val reach by setting("Reach", 10.0, 1.0..100.0, 1.0, "Freecam reach distance")
	private val rotateMode by setting("Rotate Mode", FreecamRotationMode.None, "Rotation mode").onValueChange { _, it -> if (it == FreecamRotationMode.LookAtTarget) mc.crosshairTarget = BlockHitResult.createMissed(Vec3d.ZERO, Direction.UP, BlockPos.ORIGIN) }
	private val relative by setting("Relative", false, "Moves freecam relative to player position") { mode == Mode.Free }.onValueChange { _, it -> if (it) lastPlayerPosition = player.pos }
	private val keepYLevel by setting("Keep Y Level", false, "Don't change the camera y-level on player movement") { mode == Mode.Free && relative }

	// Follow Player settings
	private val followMaxDistance by setting("String Length", 10.0, 2.0..50.0, 0.5, "Maximum distance before the string pulls the camera", unit = "m") { mode == Mode.FollowPlayer }
	private val followTrackPlayer by setting("Track Player", false, "Keeps looking at the followed player") { mode == Mode.FollowPlayer }

	private var lastPerspective = Perspective.FIRST_PERSON
	private var lastPlayerPosition: Vec3d = Vec3d.ZERO
	private var prevPosition: Vec3d = Vec3d.ZERO
	private var position: Vec3d = Vec3d.ZERO
	private val lerpPos: Vec3d
		get() {
			val tickProgress = mc.gameRenderer.camera.lastTickProgress
			return prevPosition.interpolate(tickProgress, position)
		}

	private var rotation: Rotation = Rotation.ZERO
	private var velocity: Vec3d = Vec3d.ZERO

	@JvmStatic
	fun updateCam() {
		if (mode == Mode.FollowPlayer && followTrackPlayer) {
			runSafe {
				findFollowTarget()?.let {
					val vec = it.getLerpedPos(mc.gameRenderer.camera.lastTickProgress).add(.0, it.standingEyeHeight.toDouble(), .0).subtract(lerpPos)                    // look from lerp pos to target's eye pos
					val yaw = Math.toDegrees(atan2(vec.z, vec.x)) - 90.0
					val pitch = -Math.toDegrees(atan2(vec.y, hypot(vec.x, vec.z)))
					rotation = Rotation(yaw, pitch)
				}
			}
		}
		mc.gameRenderer.apply {
			camera.setRotation(rotation.yawF, rotation.pitchF)
			camera.setPos(lerpPos.x, lerpPos.y, lerpPos.z)
		}
	}

	/**
	 * @see net.minecraft.entity.Entity.changeLookDirection
	 */
	private const val SENSITIVITY_FACTOR = 0.15

	init {
		setDefaultAutomationConfig()
			.withEdits {
				rotationConfig::rotationMode.editSetting { defaultValue(RotationMode.Lock) }
				hideAllExcept(::rotationConfig)
			}

		onEnable {
			lastPerspective = mc.options.perspective
			position = player.eyePos
			rotation = player.rotation
			velocity = Vec3d.ZERO
			lastPlayerPosition = player.pos
		}

		onDisable {
			mc.options.perspective = lastPerspective
		}

		listen<TickEvent.Pre> {
			when (rotateMode) {
				FreecamRotationMode.None -> return@listen
				FreecamRotationMode.KeepRotation -> rotationRequest { rotation(rotation) }.submit()
				FreecamRotationMode.LookAtTarget -> mc.crosshairTarget?.let { rotationRequest { rotation(lookAt(it.pos)) }.submit() }
			}
		}

		listen<PlayerEvent.ChangeLookDirection> {
			rotation = rotation.withDelta(it.deltaYaw * SENSITIVITY_FACTOR, it.deltaPitch * SENSITIVITY_FACTOR)
			it.cancel()
		}

		listen<MovementEvent.InputUpdate>({ 1 }) { event -> // Higher priority than auto walk so it's input survives the cancel below
			mc.options.perspective = Perspective.FIRST_PERSON

			// Don't block baritone from working
			if (!event.input.handledByBaritone) {
				// Reset actual input
				event.input.cancel()
			}

			when (mode) {
				Mode.Free -> {
					// Create new input for freecam
					val input = newMovementInput(assumeBaritone = false, slowdownCheck = false)
					val sprintModifier = if (mc.options.sprintKey.isPressed) sprint else 1.0
					val moveDir = calcMoveRad(rotation.yawF, input.roundedForward, input.roundedStrafing)
					var moveVec = movementVector(moveDir, input.verticalMovement) * speed * sprintModifier
					if (!input.isInputting) moveVec *= Vec3d(0.0, 1.0, 0.0)
					// Apply movement
					velocity += moveVec
					velocity *= 0.6
					// Update position
					prevPosition = position
					position += velocity

					if (relative) {
						val delta = player.pos.subtract(lastPlayerPosition)
						position += if (keepYLevel) Vec3d(delta.x, 0.0, delta.z) else delta
						lastPlayerPosition = player.pos
					}
				}

				Mode.FollowPlayer -> {
					// Allow manual camera nudges via input
					val input = newMovementInput(assumeBaritone = false, slowdownCheck = false)
					val moveDir = calcMoveRad(rotation.yawF, input.roundedForward, input.roundedStrafing)
					var moveVec = movementVector(moveDir, input.verticalMovement) * speed
					if (!input.isInputting) moveVec *= Vec3d(0.0, 1.0, 0.0)
					// Apply movement
					velocity += moveVec
					velocity *= 0.6
					// Update position
					prevPosition = position

					findFollowTarget()?.let { target ->
						val targetPos2d = Vec2d(target.eyePos.x, target.eyePos.z)
						val cameraPos2d = Vec2d(position.x, position.z)
						val distance2d = targetPos2d.dist(cameraPos2d)
						if (distance2d > followMaxDistance) {
							val excess2d = distance2d - followMaxDistance
							val pullDirection2d = Vec2d(targetPos2d.x - position.x, targetPos2d.y - position.z).normal()
							val pullVec2d = pullDirection2d * excess2d
							position = Vec3d(position.x + pullVec2d.x, position.y, position.z + pullVec2d.y)
						}

						if (abs(target.eyePos.y - position.y) > followMaxDistance) {
							val excessY = abs(target.eyePos.y - position.y) - followMaxDistance
							val pullDirectionY = sign(target.eyePos.y - position.y)
							position = Vec3d(position.x, position.y + pullDirectionY * excessY, position.z)
						}
						val delta = player.pos.subtract(lastPlayerPosition)
						position += Vec3d(0.0, delta.y, 0.0)
					}
					position += velocity
					lastPlayerPosition = player.pos
				}
			}
		}

		listen<RenderEvent.UpdateTarget>({ 1 }) { event -> // Higher priority then RotationManager to run before RotationManager modifies mc.crosshairTarget
			mc.crosshairTarget = rotation.rayCast(reach, lerpPos).orMiss // Can't be null (otherwise mc will spam "Null returned as 'hitResult', this shouldn't happen!")
			mc.crosshairTarget?.let { if (it.type != HitResult.Type.MISS) event.cancel() }
		}

		listenUnsafe<PacketEvent.Receive.Pre> { event ->
			if (event.packet is PlayerRespawnS2CPacket) disable()
		}
	}

	private fun SafeContext.findFollowTarget(): PlayerEntity? {
		if (player.gameMode != GameMode.SPECTATOR) {
			return player
		}
		val players = world.players.filter { it !is ClientPlayerEntity }
		return players.minByOrNull { it.eyePos.squaredDistanceTo(position) }
	}

	private enum class FreecamRotationMode(override val displayName: String, override val description: String) : NamedEnum, Describable {
		None("None", "No rotation changes"),
		LookAtTarget("Look At Target", "Look at the block or entity under your crosshair"),
		KeepRotation("Keep Rotation", "Look in the same direction as the camera");
	}

	private enum class Mode(override val displayName: String, override val description: String) : NamedEnum, Describable {
		Free("Free", "Move the camera freely with keyboard input"),
		FollowPlayer("Follow Player", "Camera follows a player as if attached by an invisible string");
	}
}

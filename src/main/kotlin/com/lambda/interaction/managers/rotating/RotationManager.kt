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

package com.lambda.interaction.managers.rotating

import com.lambda.Lambda.mc
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.BaritoneHandler
import com.lambda.interaction.managers.Manager
import com.lambda.interaction.managers.rotating.Rotation.Companion.slerpPitch
import com.lambda.interaction.managers.rotating.Rotation.Companion.slerpYaw
import com.lambda.interaction.managers.rotating.RotationManager.activeRotation
import com.lambda.interaction.managers.rotating.RotationManager.serverRotation
import com.lambda.interaction.managers.rotating.RotationManager.updateActiveRotation
import com.lambda.threading.runGameScheduled
import com.lambda.threading.runSafe
import com.lambda.util.extension.rotation
import com.lambda.util.math.MathUtils.toRadian
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.world.raycast.RayCastUtils.orMiss
import net.minecraft.client.input.Input
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.PlayerInput
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.Vec2f
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manager designed to rotate the player and adjust movement input to match the camera's direction.
 */
@Suppress("unused")
object RotationManager : Manager<RotationRequest>(
	1,
	*(ALL_STAGES.subList(ALL_STAGES.indexOf(TickEvent.Player.Post), ALL_STAGES.size - 1).toTypedArray()),
) {
	var pitchRequest
		get() = requests[0] as? IRotationRequest.PitchRot
		set(value) { requests[0] = value }
	var yawRequest
		get() = requests[1] as? IRotationRequest.YawRot
		set(value) { requests[1] = value }
	@JvmStatic val requests = mutableListOf<IRotationRequest?>(null, null)

	private var usingBaritoneRotation = false
	@JvmStatic var activeRotation = Rotation.ZERO
	@JvmStatic var serverRotation = Rotation.ZERO
	@JvmStatic var prevServerRotation = Rotation.ZERO

	private var changedThisTick = false

	private val IRotationRequest.overridable get() = age >= 1

	private var pauseVanillaOverrides = false

	override fun load(): String {
		super.load()

        listen<TickEvent.Pre>({ Int.MAX_VALUE }) {
            requests.forEachIndexed { index, request ->
                if (request == null) return@forEachIndexed
                if (request.keepTicks <= 0 && request.decayTicks <= 0) {
                    requests[index] = null
                }
            }
        }

        listen<TickEvent.Post>({ Int.MIN_VALUE }) {
            usingBaritoneRotation = false
            requests.forEach { request ->
                request?.age++
            }
            changedThisTick = false
        }

		listen<RenderEvent.UpdateTarget> {
			if (requests.any { request -> request == null }) return@listen
			it.cancel()

			val eye = player.eyePos
			val entityHit = player.rotation.rayCast(player.entityInteractionRange, eye).orMiss
			mc.targetedEntity = (entityHit as? EntityHitResult)?.entity
			val blockHit = player.rotation.rayCast(player.blockInteractionRange, eye).orMiss
			mc.crosshairTarget = blockHit
		}

        listen<PacketEvent.Receive.Post>({ Int.MIN_VALUE }) { event ->
            val packet = event.packet
            if (packet !is PlayerPositionLookS2CPacket) return@listen

			runGameScheduled {
				reset(Rotation(packet.change.yaw, packet.change.pitch))
			}
		}

        listenUnsafe<ConnectionEvent.Connect.Pre>({ Int.MIN_VALUE }) {
            reset(Rotation.ZERO)
        }

        // Override user interactions with max priority
        listen<PlayerEvent.Attack.Block>({ Int.MAX_VALUE }) { if (!pauseVanillaOverrides) activeRotation = player.rotation }
        listen<PlayerEvent.Attack.Entity>({ Int.MAX_VALUE }) { if (!pauseVanillaOverrides) activeRotation = player.rotation }
        listen<PlayerEvent.Interact.Item>({ Int.MAX_VALUE }) { if (!pauseVanillaOverrides) activeRotation = player.rotation }
        listen<PlayerEvent.Interact.Block>({ Int.MAX_VALUE }) { if (!pauseVanillaOverrides) activeRotation = player.rotation }
        listen<PlayerEvent.Interact.Entity>({ Int.MAX_VALUE }) { if (!pauseVanillaOverrides) activeRotation = player.rotation }

		return "Loaded Rotation Manager"
	}

	/**
	 * If the [activeRequest] is from an older tick or null, and the [request]'s target rotation is not null,
	 * the request is accepted and set as the [activeRequest]. The [activeRotation] is then updated.
	 *
	 * @see updateActiveRotation
	 */
	override fun AutomatedSafeContext.handleRequest(request: RotationRequest) {
		if (usingBaritoneRotation) return
		if (acceptAndSetRequests(request)) {
			updateActiveRotation()
			changedThisTick = true
		}
	}

	private fun acceptAndSetRequests(request: RotationRequest) =
		when (request) {
			is IRotationRequest.Full ->
				(request.rotation.value != null && requests.all { it?.overridable != false }).also { accepted ->
					if (accepted) {
						pitchRequest = request
						yawRequest = request
					}
				}
			is IRotationRequest.Yaw ->
				(request.yaw.value != null && yawRequest?.overridable != false).also { accepted ->
					if (accepted) yawRequest = request
				}
			is IRotationRequest.Pitch ->
				(request.pitch.value != null && pitchRequest?.overridable != false).also { accepted ->
					if (accepted) pitchRequest = request
				}
			else -> false
		}

	context(safeContext: SafeContext)
	fun setPlayerYaw(yaw: Double) {
		if (lockYaw == null) safeContext.player.yaw = yaw.toFloat()
	}

	context(safeContext: SafeContext)
	fun setPlayerPitch(pitch: Double) {
		if (lockPitch == null) safeContext.player.pitch = pitch.toFloat()
	}

	context(safeContext: SafeContext)
	fun setPlayerRotation(rotation: Rotation) {
		setPlayerYaw(rotation.yaw)
		setPlayerPitch(rotation.pitch)
	}

	fun withoutVanillaOverrides(block: () -> Unit) {
		pauseVanillaOverrides = true
		block()
		pauseVanillaOverrides = false
	}

	/**
	 * If the rotation has not been changed this tick, the [activeRequest]'s target rotation is updated, and
	 * likewise the [activeRotation]. The [activeRequest] is then updated, ticking the [RotationRequest.keepTicks]
	 * and [RotationRequest.decayTicks].
	 */
	@JvmStatic
	fun processRotations() = runSafe {
		if (requests.any { it != null }) activeThisTick = true

		if (!changedThisTick) { // rebuild the rotation if the same context gets used again
			requests.forEach { request -> request?.updateRotation() }
			updateActiveRotation()
		}
	}

	@JvmStatic
	fun handleBaritoneRotation(yaw: Double, pitch: Double) {
		runSafe {
			usingBaritoneRotation = true
			val request = IRotationRequest.Full(BaritoneHandler) { Rotation(yaw, pitch) }
			yawRequest = request
			pitchRequest = request
			updateActiveRotation()
			changedThisTick = true
		}
	}

	/**
	 * Calculates and sets the optimal movement input for moving in the direction the player is facing. This
	 * is not the direction the rotation manager is rotated towards, but the underlying player rotation, typically
	 * also the camera's rotation.
	 */
	@JvmStatic
	fun redirectStrafeInputs(input: Input) = runSafe {
		if (usingBaritoneRotation) return@runSafe

		val movementYaw = movementYaw ?: return@runSafe
		val playerYaw = player.yaw

		if (movementYaw.minus(playerYaw).rem(360f).let { it * it } < 0.001f) return@runSafe

		val originalStrafe = input.movementVector.x
		val originalForward = input.movementVector.y

		if (originalStrafe == 0.0f && originalForward == 0.0f) return@runSafe

		val deltaYawRad = (playerYaw - movementYaw).toRadian()

		val cos = cos(deltaYawRad)
		val sin = sin(deltaYawRad)
		val newStrafe = originalStrafe * cos - originalForward * sin
		val newForward = originalStrafe * sin + originalForward * cos

		val angle = atan2(newStrafe.toDouble(), newForward.toDouble())

		// Define the boundaries for our 8 sectors (in radians). Each sector is 45 degrees (PI/4).
		val sector = (PI / 4.0).toFloat()
		val boundary = (PI / 8.0).toFloat() // The halfway point between sectors (22.5 degrees)

		var pressForward = false
		var pressBackward = false
		var pressLeft = false
		var pressRight = false

		// Determine which 45-degree sector the angle falls into and set the corresponding keys.
		when {
			angle > -boundary && angle <= boundary -> {
				pressForward = true
			}
			angle > boundary && angle <= boundary + sector -> {
				pressForward = true
				pressLeft = true
			}
			angle > boundary + sector && angle <= boundary + 2 * sector -> {
				pressLeft = true
			}
			angle > boundary + 2 * sector && angle <= boundary + 3 * sector -> {
				pressBackward = true
				pressLeft = true
			}
			angle > boundary + 3 * sector || angle <= -(boundary + 3 * sector) -> {
				pressBackward = true
			}
			angle > -(boundary + 3 * sector) && angle <= -(boundary + 2 * sector) -> {
				pressBackward = true
				pressRight = true
			}
			angle > -(boundary + 2 * sector) && angle <= -(boundary + sector) -> {
				pressRight = true
			}
			angle > -(boundary + sector) && angle <= -boundary -> {
				pressForward = true
				pressRight = true
			}
		}

		input.playerInput = PlayerInput(
			pressForward,
			pressBackward,
			pressLeft,
			pressRight,
			input.playerInput.jump(),
			input.playerInput.sneak(),
			input.playerInput.sprint()
		)

		val x = multiplier(input.playerInput.left(), input.playerInput.right())
		val y = multiplier(input.playerInput.forward(), input.playerInput.backward())
		input.movementVector = Vec2f(x, y).normalize()
	}

	private fun multiplier(positive: Boolean, negative: Boolean) =
		((if (positive) 1 else 0) - (if (negative) 1 else 0)).toFloat()

	@JvmStatic fun onRotationSend() {
		prevServerRotation = serverRotation
		serverRotation = activeRotation

		if (yawRequest?.rotationConfig?.rotationMode == RotationMode.Lock)
			mc.player?.yaw = serverRotation.yawF
		if (pitchRequest?.rotationConfig?.rotationMode == RotationMode.Lock)
			mc.player?.pitch = serverRotation.pitchF
	}

	/**
	 * Updates the [activeRotation]. If [activeRequest] is null, the player's rotation is used.
	 * Otherwise, the [serverRotation] is interpolated towards the [RotationRequest.target] rotation.
	 */
	private fun SafeContext.updateActiveRotation() {
		val newYaw = yawRequest?.let { yawRequest ->
			val toYaw = if (yawRequest.keepTicks >= 0)
				yawRequest.yaw.value ?: activeRotation.yaw
			else player.rotation.yaw
			serverRotation.slerpYaw(toYaw, yawRequest.rotationConfig.turnSpeed)
		} ?: player.rotation.yaw

		val newPitch = pitchRequest?.let { pitchRequest ->
			val toPitch = if (pitchRequest.keepTicks >= 0)
				pitchRequest.pitch.value ?: activeRotation.pitch
			else player.rotation.pitch
			serverRotation.slerpPitch(toPitch, pitchRequest.rotationConfig.turnSpeed)
		} ?: player.rotation.pitch

		requests.forEach { request ->
			if (request == null) return@forEach
			if (request.keepTicks-- <= 0) {
				request.decayTicks--
			}
		}

		if (!newYaw.isNaN() && !newPitch.isNaN()) {
			activeRotation = Rotation(newYaw, newPitch)
		}
	}

	private fun reset(rotation: Rotation) {
		prevServerRotation = rotation
		serverRotation = rotation
		activeRotation = rotation
		pitchRequest = null
		yawRequest = null
	}

	@JvmStatic
	val lockRotation: Rotation?
		get() = runSafe {
			val pitch = activeRotation.pitchF.takeIf { pitchRequest?.rotationConfig?.rotationMode == RotationMode.Lock }
			val yaw = activeRotation.yawF.takeIf { yawRequest?.rotationConfig?.rotationMode == RotationMode.Lock }
			if (pitch == null && yaw == null) return@runSafe null
			Rotation(yaw ?: player.yaw, pitch ?: player.pitch)
		}

	@JvmStatic
	val lockYaw: Double?
		get() = runSafe { activeRotation.yaw.takeIf { yawRequest?.rotationConfig?.rotationMode == RotationMode.Lock } }

	@JvmStatic
	val lockPitch: Double?
		get() = runSafe { activeRotation.pitch.takeIf { pitchRequest?.rotationConfig?.rotationMode == RotationMode.Lock } }

	@JvmStatic
	val headYaw
		get() = activeRotation.yawF.takeIf { yawRequest != null }

	@JvmStatic
	val headPitch
		get() = activeRotation.pitchF.takeIf { pitchRequest != null }

	@JvmStatic
	val handYaw
		get() = activeRotation.yawF.takeIf { yawRequest?.rotationConfig?.rotationMode == RotationMode.Lock }

	@JvmStatic
	val handPitch
		get() = activeRotation.pitchF.takeIf { pitchRequest?.rotationConfig?.rotationMode == RotationMode.Lock }

	@JvmStatic
	val movementYaw: Float?
		get() {
			return if (yawRequest == null || yawRequest?.rotationConfig?.rotationMode == RotationMode.Silent) null
			else activeRotation.yaw.toFloat()
		}

	@JvmStatic
	val movementPitch: Float?
		get() {
			return if (pitchRequest == null || pitchRequest?.rotationConfig?.rotationMode == RotationMode.Silent) null
			else activeRotation.pitch.toFloat()
		}

	@JvmStatic
	fun getRotationForVector(deltaTime: Double): Vec2d? = runSafe {
		val yaw = activeRotation.yawF.takeIf { yawRequest != null && yawRequest?.rotationConfig?.rotationMode != RotationMode.Silent }
		val pitch = activeRotation.pitchF.takeIf { pitchRequest != null && pitchRequest?.rotationConfig?.rotationMode != RotationMode.Silent }
		if (yaw == null && pitch == null) return@runSafe null

		val rot = lerp(deltaTime, serverRotation, Rotation(yaw ?: player.yaw, pitch ?: player.pitch))
		return Vec2d(rot.yaw, rot.pitch)
	}
}

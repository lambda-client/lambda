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
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handlers.BaritoneHandler
import com.lambda.interaction.managers.Manager
import com.lambda.interaction.managers.rotating.RotationManager.activeRotation
import com.lambda.interaction.managers.rotating.RotationManager.serverRotation
import com.lambda.interaction.managers.rotating.RotationManager.updateActiveRotation
import com.lambda.threading.runSafe
import com.lambda.util.ClientTicks
import com.lambda.util.extension.rotation
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.player.RotationUtils.getInputRelativeTo
import com.lambda.util.world.raycast.RayCastUtils.orMiss
import net.minecraft.client.input.Input
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec2f
import kotlin.math.abs

/**
 * Manager designed to rotate the player and adjust movement input to match the camera's direction.
 */
@Suppress("unused")
object RotationManager : Manager<RotationRequest>(
	4,
	*ALL_STAGES.filter { it !in TickEvent.ROTATION_STAGES }.toTypedArray(),
) {
	var yawRequest
		get() = requests[1] as? IRotationRequest.YawRot
		set(value) { requests[1] = value }
	var pitchRequest
		get() = requests[0] as? IRotationRequest.PitchRot
		set(value) { requests[0] = value }
	@JvmStatic val requests = mutableListOf<IRotationRequest?>(null, null)

	@JvmStatic var activeRotation = Rotation.ZERO
	@JvmStatic var serverRotation = Rotation.ZERO
	@JvmStatic var prevServerRotation = Rotation.ZERO

	private var changedThisTick = false

	private val hand = MouseHand(System.nanoTime())

	private var pendingStep: MouseHand.Step? = null

	private val IRotationRequest.overridable get() = age >= 1

	private inline fun forEachRequest(action: (IRotationRequest) -> Unit) {
		val pitch = requests[0]
		pitch?.let(action)
		requests[1]?.takeIf { it !== pitch }?.let(action)
	}

	private var pauseVanillaOverrides = false

	private val overrideHolds = ArrayList<() -> Boolean>()

	private val vanillaOverridesPaused: Boolean get() = pauseVanillaOverrides || overrideHolds.any { it() }

	override fun load(): String {
		super.load()

        listen<TickEvent.Pre>({ Int.MAX_VALUE }) {
            requests.forEachIndexed { index, request ->
                if (request == null) return@forEachIndexed
                if (request.keepTicks <= 0 && request.decayTicks <= 0 && look.reaches(serverRotation, player.rotation)) {
                    requests[index] = null
                }
            }
        }

        listen<TickEvent.Post>({ Int.MIN_VALUE }) {
            forEachRequest { request -> request.age++ }
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

        // Override user interactions with max priority
        listen<PlayerEvent.Attack.Block>({ Int.MAX_VALUE }) { if (!vanillaOverridesPaused) activeRotation = player.rotation }
        listen<PlayerEvent.Attack.Entity>({ Int.MAX_VALUE }) { if (!vanillaOverridesPaused) activeRotation = player.rotation }
        listen<PlayerEvent.Interact.Item>({ Int.MAX_VALUE }) { if (!vanillaOverridesPaused) activeRotation = player.rotation }
        listen<PlayerEvent.Interact.Block>({ Int.MAX_VALUE }) { if (!vanillaOverridesPaused) activeRotation = player.rotation }
        listen<PlayerEvent.Interact.Entity>({ Int.MAX_VALUE }) { if (!vanillaOverridesPaused) activeRotation = player.rotation }

		return "Loaded Rotation Manager"
	}

	/**
	 * If the [activeRequest] is from an older tick or null, and the [request]'s target rotation is not null,
	 * the request is accepted and set as the [activeRequest]. The [activeRotation] is then updated.
	 *
	 * @see updateActiveRotation
	 */
	override fun AutomatedSafeContext.handleRequest(request: RotationRequest) {
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

	fun holdVanillaOverridesWhile(condition: () -> Boolean) {
		overrideHolds += condition
	}

	fun <T> withoutVanillaOverrides(block: () -> T): T {
		pauseVanillaOverrides = true
		try {
			return block()
		} finally {
			pauseVanillaOverrides = false
		}
	}

	fun <T> SafeContext.withServerLook(block: () -> T): T {
		if (BaritoneHandler.isActive) return block()
		val yaw = player.yaw
		val pitch = player.pitch
		player.yaw = MathHelper.wrapDegrees(serverRotation.yawF)
		player.pitch = serverRotation.pitchF
		try {
			return block()
		} finally {
			player.yaw = yaw
			player.pitch = pitch
		}
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
			forEachRequest { request -> request.updateRotation() }
			updateActiveRotation()
		}
	}

	/**
	 * Calculates and sets the optimal movement input for moving in the direction the player is facing. This
	 * is not the direction the rotation manager is rotated towards, but the underlying player rotation, typically
	 * also the camera's rotation.
	 */
	@JvmStatic
	fun redirectStrafeInputs(input: Input) = runSafe {
		val movementYaw = movementYaw ?: return@runSafe
		val viewYaw = player.yaw

		val newPlayerInput = getInputRelativeTo(movementYaw, input.playerInput, viewYaw)

		input.playerInput = newPlayerInput

		val x = multiplier(input.playerInput.left(), input.playerInput.right())
		val y = multiplier(input.playerInput.forward(), input.playerInput.backward())
		input.movementVector = Vec2f(x, y).normalize()
	}

	private fun multiplier(positive: Boolean, negative: Boolean) =
		(if (positive) 1f else 0f) - (if (negative) 1f else 0f)

	@JvmStatic fun onRotationSend() {
		prevServerRotation = serverRotation
		serverRotation = activeRotation
		pendingStep?.let(hand::commit) ?: hand.rest()
		pendingStep = null
		forEachRequest { request ->
			if (request.keepTicks-- <= 0) request.decayTicks--
		}

		if (yawRequest?.rotationConfig?.rotationMode == RotationMode.Lock)
			mc.player?.yaw = serverRotation.yawF
		if (pitchRequest?.rotationConfig?.rotationMode == RotationMode.Lock)
			mc.player?.pitch = serverRotation.pitchF
	}

	/**
	 * Updates the [activeRotation]: whole mouse counts from the [serverRotation] toward the requested rotation,
	 * or the player's rotation while nothing is requested.
	 */
	private fun SafeContext.updateActiveRotation() {
		val yawRequest = yawRequest
		val pitchRequest = pitchRequest
		val config = (yawRequest ?: pitchRequest)?.rotationConfig
		if (config == null) {
			pendingStep = null
			activeRotation = player.rotation
			return
		}

		val toYaw = yawRequest?.let { if (it.keepTicks >= 0) it.yaw.value ?: activeRotation.yaw else player.rotation.yaw } ?: serverRotation.yaw
		val toPitch = pitchRequest?.let { if (it.keepTicks >= 0) it.pitch.value ?: activeRotation.pitch else player.rotation.pitch } ?: serverRotation.pitch
		if (toYaw.isNaN() || toPitch.isNaN()) return

		val look = look
		val target = Rotation(toYaw, toPitch)
		val next = if (yawRequest.aimsExactly || pitchRequest.aimsExactly) {
			pendingStep = null
			look.nearest(serverRotation, target)
		} else {
			val limits = MouseHand.Limits(config.turnSpeed, config.turnAcceleration, config.turnNoise)
			val step = hand.step(serverRotation, target, look, limits, ClientTicks.now)
			pendingStep = step
			look.turn(serverRotation, step.dx, step.dy)
		}
		activeRotation = Rotation(
			if (yawRequest != null) next.yaw else player.rotation.yaw,
			if (pitchRequest != null) next.pitch else player.rotation.pitch,
		)
	}

	private val IRotationRequest?.aimsExactly: Boolean get() = this != null && exact && keepTicks >= 0

	val SafeContext.look: MouseLook
		get() = MouseLook(
			MouseLook.factor(mc.options.mouseSensitivity.value, mc.options.perspective.isFirstPerson && player.isUsingSpyglass),
			mc.options.invertMouseX.value,
			mc.options.invertMouseY.value,
		)

	private val SafeContext.sentRotation: Rotation get() = if (BaritoneHandler.isActive) player.rotation else serverRotation

	fun SafeContext.reachedYaw(yaw: Double): Boolean = abs(Rotation.wrap(yaw - sentRotation.yaw)) <= look.degreesPerCount / 2

	fun SafeContext.reachedPitch(pitch: Double): Boolean = abs(pitch - sentRotation.pitch) <= look.degreesPerCount / 2

	@JvmStatic
	fun resetYaw(yaw: Double, active: Boolean) {
		prevServerRotation = Rotation(yaw, prevServerRotation.pitch)
		serverRotation = Rotation(yaw, serverRotation.pitch)
		if (active) {
			activeRotation = Rotation(yaw, activeRotation.pitch)
			yawRequest = null
		}
	}

	@JvmStatic
	fun resetPitch(pitch: Double, active: Boolean) {
		prevServerRotation = Rotation(prevServerRotation.yaw, pitch)
		serverRotation = Rotation(serverRotation.yaw, pitch)
		if (active) {
			activeRotation = Rotation(activeRotation.yaw, pitch)
			pitchRequest = null
		}
	}

	@JvmStatic
	fun lockRotationAt(tickProgress: Float): Rotation? = runSafe {
		val yaw = yawRequest?.rotationConfig?.rotationMode == RotationMode.Lock
		val pitch = pitchRequest?.rotationConfig?.rotationMode == RotationMode.Lock
		if (!yaw && !pitch) return@runSafe null
		val progress = tickProgress.toDouble()
		Rotation(
			if (yaw) lerp(progress, prevServerRotation.yaw, prevServerRotation.yaw + Rotation.wrap(serverRotation.yaw - prevServerRotation.yaw)) else player.getYaw(tickProgress).toDouble(),
			if (pitch) lerp(progress, prevServerRotation.pitch, serverRotation.pitch) else player.getPitch(tickProgress).toDouble(),
		)
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
		get() =
			if (yawRequest == null || yawRequest?.rotationConfig?.rotationMode == RotationMode.Silent) null
			else activeRotation.yaw.toFloat()

	@JvmStatic
	val movementPitch: Float?
		get() =
			if (pitchRequest == null || pitchRequest?.rotationConfig?.rotationMode == RotationMode.Silent) null
			else activeRotation.pitch.toFloat()

	@JvmStatic
	fun getRotationForVector(deltaTime: Double): Vec2d? = runSafe {
		val yaw = activeRotation.yawF.takeIf { yawRequest != null && yawRequest?.rotationConfig?.rotationMode != RotationMode.Silent }
		val pitch = activeRotation.pitchF.takeIf { pitchRequest != null && pitchRequest?.rotationConfig?.rotationMode != RotationMode.Silent }
		if (yaw == null && pitch == null) return@runSafe null

		val target = Rotation(yaw ?: player.yaw, pitch ?: player.pitch)
		val rot = if (deltaTime == 1.0) target else lerp(deltaTime, serverRotation, target)
		return Vec2d(rot.yaw, rot.pitch)
	}
}

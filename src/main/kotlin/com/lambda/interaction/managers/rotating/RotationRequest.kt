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

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.handlers.BaritoneHandler
import com.lambda.interaction.managers.Request
import com.lambda.interaction.managers.rotating.IRotationRequest.Full
import com.lambda.interaction.managers.rotating.IRotationRequest.Pitch
import com.lambda.interaction.managers.rotating.IRotationRequest.Yaw
import com.lambda.interaction.managers.rotating.Rotation.Companion.dist
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotation
import com.lambda.interaction.managers.rotating.Rotation.Companion.wrap
import com.lambda.threading.runSafe
import com.lambda.util.collections.UpdatableLazy
import com.lambda.util.collections.updatableLazy
import kotlin.math.abs
import kotlin.math.hypot

abstract class RotationRequest(automated: Automated) : Request(), Automated by automated {
	override val requestId = requestCount++
	override val tickStageMask = automated.rotationConfig.tickStageMask
	override val nowOrNothing = true

	abstract infix fun dist(rotation: Rotation): Double

	@RotationRequestMarker
	override fun submit(queueIfMismatchedStage: Boolean) =
		RotationManager.request(this, queueIfMismatchedStage)

	companion object {
		var requestCount = 0
			private set
	}
}

@DslMarker
annotation class RotationRequestMarker

interface IRotationRequest : Automated {
	var keepTicks: Int
	var decayTicks: Int
	var age: Int

	val done: Boolean

	fun updateRotation()

	class Yaw(
		automated: Automated,
		val buildYaw: SafeContext.() -> Double
	) : RotationRequest(automated), YawRot {
		override val yaw = updatableLazy { runSafe { buildYaw() } }
		override var keepTicks = rotationConfig.keepTicks
		override var decayTicks = rotationConfig.decayTicks
		override var age = 0

		override val done
			get() =
				runSafe {
					val delta = (if (BaritoneHandler.isActive) player.rotation else RotationManager.activeRotation)
						.yaw - (yaw.value ?: return@runSafe false)
					val wrappedDelta = ((delta + 180) % 360 + 360) % 360 - 180
					abs(wrappedDelta) <= 0.001
				} == true

		override fun dist(rotation: Rotation): Double {
			return wrap((yaw.value ?: return Double.MAX_VALUE) - rotation.yaw)
		}

		override fun updateRotation() = yaw.update()
	}

	class Pitch(
		automated: Automated,
		val buildPitch: SafeContext.() -> Double
	) : RotationRequest(automated), PitchRot {
		override val pitch = updatableLazy { runSafe { buildPitch() } }
		override var keepTicks = rotationConfig.keepTicks
		override var decayTicks = rotationConfig.decayTicks
		override var age = 0

		override val done
			get() =
				runSafe {
					abs((if (BaritoneHandler.isActive) player.rotation else RotationManager.activeRotation)
						.pitch - (pitch.value ?: return@runSafe false)) <= 0.001
				} == true

		override fun dist(rotation: Rotation): Double {
			return wrap((pitch.value ?: return Double.MAX_VALUE) - rotation.pitch)
		}

		override fun updateRotation() = pitch.update()
	}

	class Full(
		automated: Automated,
		val buildRotation: SafeContext.() -> Rotation
	) : RotationRequest(automated), FullRot {
		override val rotation = updatableLazy { runSafe { buildRotation() } }
		override val yaw get() = updatableLazy { rotation.value?.yaw }
		override val pitch get() = updatableLazy { rotation.value?.pitch }
		override var keepTicks = rotationConfig.keepTicks
		override var decayTicks = rotationConfig.decayTicks
		override var age = 0

		override val done
			get() =
				runSafe {
					(if (BaritoneHandler.isActive) player.rotation else RotationManager.activeRotation)
						.dist(rotation.value ?: return@runSafe false) <= 0.001
				} == true

		override fun dist(rotation: Rotation) =
			this.rotation.value?.let {
				hypot(
					wrap(it.yaw - rotation.yaw),
					wrap(it.pitch - rotation.pitch)
				)
			} ?: Double.MAX_VALUE

		override fun updateRotation() = rotation.update()
	}

	interface YawRot : IRotationRequest { val yaw: UpdatableLazy<Double?> }
	interface PitchRot : IRotationRequest { val pitch: UpdatableLazy<Double?> }
	interface FullRot : YawRot, PitchRot { val rotation: UpdatableLazy<Rotation?> }
}

@RotationRequestMarker
class RotationRequestBuilder private constructor() {
	private var pitchBuilder: (SafeContext.() -> Double)? = null
	private var yawBuilder: (SafeContext.() -> Double)? = null
	private var rotationBuilder: (SafeContext.() -> Rotation)? = null

	@JvmName("yawBuilder1")
	fun yaw(builder: SafeContext.() -> Double) { yawBuilder = builder }

	@JvmName("yawBuilder2")
	fun yaw(builder: SafeContext.() -> Float) { yawBuilder = { builder().toDouble() } }

	fun yaw(yaw: Double) { yawBuilder = { yaw } }

	fun yaw(yaw: Float) { yawBuilder = { yaw.toDouble() } }

	@JvmName("pitchBuilder1")
	fun pitch(builder: SafeContext.() -> Double) { pitchBuilder = builder }

	@JvmName("pitchBuilder2")
	fun pitch(builder: SafeContext.() -> Float) { pitchBuilder = { builder().toDouble() } }

	fun pitch(pitch: Double) { pitchBuilder = { pitch } }

	fun pitch(pitch: Float) { pitchBuilder = { pitch.toDouble() } }

	fun rotation(builder: SafeContext.() -> Rotation) { rotationBuilder = builder }

	fun rotation(yaw: Double, pitch: Double) { rotationBuilder = { Rotation(yaw, pitch) } }

	fun rotation(yaw: Float, pitch: Float) { rotationBuilder = { Rotation(yaw, pitch) } }

	fun rotation(rotation: Rotation) { rotationBuilder = { rotation } }

	context(automated: Automated)
	private fun build(): RotationRequest {
		val yawBuilder = yawBuilder
		val pitchBuilder = pitchBuilder
		val rotationBuilder = rotationBuilder
		return when {
			rotationBuilder != null -> Full(automated, rotationBuilder)
			yawBuilder != null && pitchBuilder != null -> Full(automated) { Rotation(yawBuilder(), pitchBuilder()) }
			yawBuilder != null -> Yaw(automated, yawBuilder)
			pitchBuilder != null -> Pitch(automated, pitchBuilder)
			else -> throw IllegalArgumentException("Must specify at least one rotation value to build a rotation request")
		}
	}

	companion object {
		fun Automated.rotationRequest(builder: RotationRequestBuilder.() -> Unit) =
			RotationRequestBuilder().apply(builder).build()
	}
}

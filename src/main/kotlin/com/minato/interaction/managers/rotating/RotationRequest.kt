
package com.minato.interaction.managers.rotating

import com.minato.context.Automated
import com.minato.context.SafeContext
import com.minato.interaction.managers.Request
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.requestCount
import com.minato.interaction.managers.rotating.Rotation.Companion.dist
import com.minato.interaction.managers.rotating.Rotation.Companion.rotation
import com.minato.interaction.managers.rotating.Rotation.Companion.wrap
import com.minato.threading.runSafe
import com.minato.util.collections.UpdatableLazy
import com.minato.util.collections.updatableLazy
import kotlin.math.abs
import kotlin.math.hypot

@DslMarker
annotation class RotationRequestDsl

abstract class RotationRequest(automated: Automated) : Request(), Automated by automated {
	override val requestId = requestCount++
	override val tickStageMask = automated.rotationConfig.tickStageMask
	override val nowOrNothing = true

	abstract infix fun dist(rotation: Rotation): Double

	@RotationRequestDsl
	override fun submit(queueIfMismatchedStage: Boolean) =
		RotationManager.request(this, queueIfMismatchedStage)
}

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
					val delta = RotationManager.activeRotation
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
					abs(RotationManager.activeRotation.pitch - (pitch.value ?: return@runSafe false)) <= 0.001
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
					RotationManager.activeRotation.dist(rotation.value ?: return@runSafe false) <= 0.001
				} == true

		override fun dist(other: Rotation): Double {
			return rotation.value?.let {
				hypot(
					wrap(it.yaw - other.yaw),
					wrap(it.pitch - other.pitch)
				)
			} ?: Double.MAX_VALUE
		}

		override fun updateRotation() = rotation.update()
	}

	interface YawRot : IRotationRequest { val yaw: UpdatableLazy<Double?> }
	interface PitchRot : IRotationRequest { val pitch: UpdatableLazy<Double?> }
	interface FullRot : YawRot, PitchRot { val rotation: UpdatableLazy<Rotation?> }

	class RotationRequestBuilder {
		var pitchBuilder: (SafeContext.() -> Double)? = null
		var yawBuilder: (SafeContext.() -> Double)? = null
		var rotationBuilder: (SafeContext.() -> Rotation)? = null

		@JvmName("yawBuilder1")
		@RotationRequestDsl
		fun yaw(builder: SafeContext.() -> Double) { yawBuilder = builder }

		@JvmName("yawBuilder2")
		@RotationRequestDsl
		fun yaw(builder: SafeContext.() -> Float) { yawBuilder = { builder().toDouble() } }

		@RotationRequestDsl
		fun yaw(yaw: Double) { yawBuilder = { yaw } }

		@RotationRequestDsl
		fun yaw(yaw: Float) { yawBuilder = { yaw.toDouble() } }

		@JvmName("pitchBuilder1")
		@RotationRequestDsl
		fun pitch(builder: SafeContext.() -> Double) { pitchBuilder = builder }

		@JvmName("pitchBuilder2")
		@RotationRequestDsl
		fun pitch(builder: SafeContext.() -> Float) { pitchBuilder = { builder().toDouble() } }

		@RotationRequestDsl
		fun pitch(pitch: Double) { pitchBuilder = { pitch } }

		@RotationRequestDsl
		fun pitch(pitch: Float) { pitchBuilder = { pitch.toDouble() } }

		@RotationRequestDsl
		fun rotation(builder: SafeContext.() -> Rotation) { rotationBuilder = builder }

		@RotationRequestDsl
		fun rotation(yaw: Double, pitch: Double) { rotationBuilder = { Rotation(yaw, pitch) } }

		@RotationRequestDsl
		fun rotation(yaw: Float, pitch: Float) { rotationBuilder = { Rotation(yaw, pitch) } }

		@RotationRequestDsl
		fun rotation(rotation: Rotation) { rotationBuilder = { rotation } }
	}

	companion object {
		var requestCount = 0

		@RotationRequestDsl
		fun Automated.rotationRequest(builder: RotationRequestBuilder.() -> Unit) =
			RotationRequestBuilder().apply(builder).build()

		@RotationRequestDsl
		context(automated: Automated)
		private fun RotationRequestBuilder.build(): RotationRequest {
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
	}
}

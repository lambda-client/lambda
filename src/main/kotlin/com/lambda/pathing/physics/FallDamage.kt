package com.lambda.pathing.physics

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import kotlin.math.floor

fun ClientPlayerEntity.fallLimits(acceptedDamage: Int): FallLimits {
	if (abilities.allowFlying || hasStatusEffect(StatusEffects.SLOW_FALLING)) return FallLimits.IMMUNE
	return FallLimits(
		acceptedDamage = acceptedDamage,
		safeFallDistance = getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE),
		fallDamageMultiplier = getAttributeValue(EntityAttributes.FALL_DAMAGE_MULTIPLIER),
	)
}

data class FallRule(val distanceScale: Double, val damagePerDistance: Double, val tramples: Boolean = false) {
	val cushions: Boolean get() = distanceScale != NORMAL.distanceScale || damagePerDistance != NORMAL.damagePerDistance

	companion object {
		val NORMAL = FallRule(1.0, 1.0)

		val HAY = FallRule(1.0, 0.2)

		val BED = FallRule(0.5, 1.0)

		val NONE = FallRule(1.0, 0.0)

		val FARMLAND = FallRule(1.0, 1.0, tramples = true)

		const val TRAMPLE_FALL_DISTANCE = 0.5
	}
}

const val FALL_DISTANCE_TOLERANCE = 1.0E-3

fun SimulationEnvironment.landingRule(state: MovementSimulationState): FallRule {
	val below = MathHelper.floor(state.position.y - LANDING_PROBE_DEPTH)
	val support = state.supportingBlockPos
	val pos = when {
		support == null -> BlockPos(MathHelper.floor(state.position.x), below, MathHelper.floor(state.position.z))
		isFenceLike(support) -> support
		else -> BlockPos(support.x, below, support.z)
	}
	return try {
		fallRule(pos)
	} catch (_: SimulationEnvironmentException) {
		FallRule.NORMAL
	}
}

private const val LANDING_PROBE_DEPTH = 0.2

data class FallLimits(
	val acceptedDamage: Int = 0,
	val safeFallDistance: Double = DEFAULT_SAFE_FALL_DISTANCE,
	val fallDamageMultiplier: Double = 1.0,
) {
	init {
		require(acceptedDamage >= 0 && safeFallDistance >= 0.0 && fallDamageMultiplier >= 0.0)
	}

	fun maxFallOnto(rule: FallRule): Double {
		val perDistance = rule.damagePerDistance * fallDamageMultiplier
		if (perDistance <= 0.0) return Double.POSITIVE_INFINITY
		return (safeFallDistance + (acceptedDamage + 1) / perDistance - VANILLA_FALL_EPSILON) / rule.distanceScale - FALL_DISTANCE_TOLERANCE
	}

	val maxSafeFallDistance: Double get() = maxFallOnto(FallRule.NORMAL)

	companion object {
		const val DEFAULT_SAFE_FALL_DISTANCE = 3.0

		val IMMUNE = FallLimits(fallDamageMultiplier = 0.0)

		fun acceptingFallsOf(distance: Double): FallLimits =
			if (distance.isInfinite()) IMMUNE
			else FallLimits(acceptedDamage = floor(distance + VANILLA_FALL_EPSILON - DEFAULT_SAFE_FALL_DISTANCE).toInt().coerceAtLeast(0))

		private const val VANILLA_FALL_EPSILON = 1.0E-6
	}
}

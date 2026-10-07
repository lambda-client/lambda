package com.lambda.pathing.physics

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects

fun ClientPlayerEntity.damageFreeFallDistance(): Double {
	if (abilities.allowFlying || hasStatusEffect(StatusEffects.SLOW_FALLING)) return Double.POSITIVE_INFINITY
	val multiplier = getAttributeValue(EntityAttributes.FALL_DAMAGE_MULTIPLIER)
	if (multiplier <= 0.0) return Double.POSITIVE_INFINITY
	return getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE) + 1.0 / multiplier - FALL_DISTANCE_TOLERANCE
}

private const val FALL_DISTANCE_TOLERANCE = 1.0E-3

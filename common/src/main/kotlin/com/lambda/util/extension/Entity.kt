package com.lambda.util.extension

import com.lambda.interaction.rotation.Rotation
import com.lambda.util.math.lerp
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.Vec3d

val Entity.prevPos
    get() = Vec3d(prevX, prevY, prevZ)

val Entity.rotation
    get() = Rotation(yaw, pitch)

var LivingEntity.isElytraFlying
    get() = isFallFlying
    set(value) { setFlag(7, value) }

fun Vec3d.interpolate(other: Vec3d, t: Double) = lerp(t, this, other)

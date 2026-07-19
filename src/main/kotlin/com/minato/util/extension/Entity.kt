
package com.minato.util.extension

import com.minato.interaction.managers.rotating.Rotation
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.Vec3d

val Entity.prevPos
    get() = Vec3d(lastX, lastY, lastZ)

val Entity.rotation
    get() = Rotation(yaw, pitch)

val LivingEntity.fullHealth: Double
    get() = health + absorptionAmount.toDouble()

val LivingEntity.maxFullHealth: Double
    get() = maxHealth + maxAbsorption.toDouble()

var LivingEntity.isElytraFlying
    get() = isGliding
    set(value) {
        setFlag(7, value)
    }

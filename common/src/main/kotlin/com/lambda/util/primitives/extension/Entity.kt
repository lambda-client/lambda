package com.lambda.util.primitives.extension

import com.lambda.interaction.rotation.Rotation
import com.lambda.util.math.MathUtils.lerp
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

val Entity.prevPos
    get() = Vec3d(prevX, prevY, prevZ)

val Entity.interpolatedPos
    get() = lerp(prevPos, pos, MinecraftClient.getInstance().partialTicks)

val Entity.rotation
    get() = Rotation(yaw, pitch)

val Entity.interpolatedBox: Box
    get() {
        val box = boundingBox
        val xw = (box.maxX - box.minX) * 0.5
        val yw = box.maxY - box.minY
        val zw = (box.maxZ - box.minZ) * 0.5

        val pos = interpolatedPos
        return Box(pos.x - xw, pos.y, pos.z - zw, pos.x + xw, pos.y + yw, pos.z + zw)
    }

fun Vec3d.interpolate(other: Vec3d, t: Double) = lerp(this, other, t)
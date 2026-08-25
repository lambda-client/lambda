package com.lambda.pathing.core

import kotlin.math.floor
import net.minecraft.util.math.Vec3d

data class Stance(val x: Int, val y: Int, val z: Int) {
    fun offset(dx: Int, dy: Int, dz: Int) = Stance(x + dx, y + dy, z + dz)

    override fun toString() = "($x, $y, $z)"

    companion object {

        fun of(position: Vec3d, onGround: Boolean): Stance = Stance(
            floor(position.x).toInt(),
            if (onGround) floor(position.y - SURFACE_EPSILON).toInt() + 1
            else floor(position.y + SURFACE_EPSILON).toInt(),
            floor(position.z).toInt(),
        )

        private const val SURFACE_EPSILON = 1e-6
    }
}

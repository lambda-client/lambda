
package com.minato.util.extension

import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

val Box.min get() = Vec3d(minX, minY, minZ)
val Box.max get() = Vec3d(maxX, maxY, maxZ)

fun Box.shrinkByEpsilon(): Box = expand(-1e-7)

operator fun Box.contains(boundingBox: Box) = this.intersects(boundingBox)
operator fun DoubleArray.component6() = this[5]

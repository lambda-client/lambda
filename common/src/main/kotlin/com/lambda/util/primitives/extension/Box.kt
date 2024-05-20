package com.lambda.util.primitives.extension

import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

val Box.min get() = Vec3d(minX, minY, minZ)

val Box.max get() = Vec3d(maxX, maxY, maxZ)
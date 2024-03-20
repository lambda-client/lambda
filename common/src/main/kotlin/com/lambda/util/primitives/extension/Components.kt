package com.lambda.util.primitives.extension

import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d

operator fun Vec2f.component1() = this.x
operator fun Vec2f.component2() = this.y
operator fun Vec3d.component1() = this.x
operator fun Vec3d.component2() = this.y
operator fun Vec3d.component3() = this.z
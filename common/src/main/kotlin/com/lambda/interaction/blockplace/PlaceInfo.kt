package com.lambda.interaction.blockplace

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

data class PlaceInfo(
    val clickPos: BlockPos,
    val clickSide: Direction,
    val placedPos: BlockPos,
    val hitVec: Vec3d,

    val eyeDistanceSq: Double,
    val placeSteps: Int
)
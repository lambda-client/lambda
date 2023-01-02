package com.lambda.client.activity.activities.types

import com.lambda.client.util.color.ColorHolder
import net.minecraft.util.math.BlockPos

interface RenderBlockActivity {
    var renderBlockPos: BlockPos
    var color: ColorHolder
}
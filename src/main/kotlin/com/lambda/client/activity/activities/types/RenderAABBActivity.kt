package com.lambda.client.activity.activities.types

import com.lambda.client.util.color.ColorHolder
import net.minecraft.util.math.AxisAlignedBB

interface RenderAABBActivity {
    var renderAABB: AxisAlignedBB
    var color: ColorHolder
}
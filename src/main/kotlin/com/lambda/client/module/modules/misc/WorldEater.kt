package com.lambda.client.module.modules.misc

import com.lambda.client.activity.activities.highlevel.BreakArea
import com.lambda.client.activity.activities.highlevel.BuildSchematic
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.manager.managers.ActivityManager.cancel
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.VectorUtils.multiply
import com.lambda.client.util.schematic.LambdaSchematicaHelper
import com.lambda.client.util.schematic.LambdaSchematicaHelper.isSchematicaPresent
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.runSafe
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

object WorldEater : Module(
    name = "WorldEater",
    description = "Easy perimeter building",
    category = Category.MISC,
    alias = arrayOf("we")
) {
    private val size by setting("Size", 10, 1..100, 1)
    private val depth by setting("Depth", 3, 1..100, 1)
    private val layerSize by setting("Layers size", 1, 1..6, 1)
    private val sliceSize by setting("Slice size", 1, 1..6, 1)
    private val sliceDirection by setting("Slice direction", EnumFacing.NORTH)

    private var ownedBuildStructure: BreakArea? = null

    init {
        onEnable {
            runSafe {
                val currentDirection = player.horizontalFacing
                val pos1 = player.flooredPosition.add(currentDirection.directionVec.multiply(2))
                val pos2 = player.flooredPosition.add(
                    currentDirection.directionVec.multiply(size)
                ).add(
                    currentDirection.rotateY().directionVec.multiply(size)
                ).down(depth)

                BreakArea(pos1, pos2, layerSize).let {
                    ownedBuildStructure = it
                    ActivityManager.addSubActivities(it)
                }
            }
        }

        onDisable {
            runSafe {
                ownedBuildStructure?.let {
                    with(it) {
                        cancel()
                    }
                }
            }
        }
    }
}
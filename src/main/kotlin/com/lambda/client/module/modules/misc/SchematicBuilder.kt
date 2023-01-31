package com.lambda.client.module.modules.misc

import com.lambda.client.activity.activities.highlevel.BuildSchematic
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.VectorUtils.multiply
import com.lambda.client.util.schematic.LambdaSchematicaHelper
import com.lambda.client.util.threads.runSafe
import net.minecraft.util.math.BlockPos

object SchematicBuilder : Module(
    name = "SchematicBuilder",
    description = "Build schematics",
    category = Category.MISC,
    alias = arrayOf("sb")
) {
    private val offset by setting("Offset", 0, -10..10, 1)
    private val inLayers by setting("In Layers", true)

    private var ownedBuildStructure: BuildSchematic? = null

    init {
        onEnable {
            runSafe {
                LambdaSchematicaHelper.loadedSchematic?.let { schematic ->
                    val direction = Direction.fromEntity(player)

                    BuildSchematic(
                        schematic,
                        inLayers,
                        direction,
                        BlockPos(direction.directionVec.multiply(offset))
                    ).let {
                        ownedBuildStructure = it
                        ActivityManager.addSubActivities(it)
                    }
                }
            }
        }

        onDisable {
            ownedBuildStructure?.let {
                with(it) {
                    runSafe {
                        success()
                    }
                }
            }
            ActivityManager.reset()
        }
    }
}
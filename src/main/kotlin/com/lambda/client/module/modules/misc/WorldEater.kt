package com.lambda.client.module.modules.misc

import com.lambda.client.activity.activities.highlevel.ClearArea
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.lwjgl.input.Mouse

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
    private val start by setting("Start", false, consumer = { _, _ ->
        clearArea()
        false
    })

    private var ownedBuildStructure: ClearArea? = null
    private var firstPos: BlockPos? = null
    private var secondPos: BlockPos? = null

    init {
//        onEnable {
//            runSafe {
//                val currentDirection = player.horizontalFacing
//                val pos1 = player.flooredPosition.add(currentDirection.directionVec.multiply(2))
//                val pos2 = player.flooredPosition.add(
//                    currentDirection.directionVec.multiply(size)
//                ).add(
//                    currentDirection.rotateY().directionVec.multiply(size)
//                ).down(depth)
//
//                BreakArea(pos1, pos2, layerSize).let {
//                    ownedBuildStructure = it
//                    ActivityManager.addSubActivities(it)
//                }
//            }
//        }

        onDisable {
            runSafe {
                ownedBuildStructure?.let {
                    with(it) {
                        cancel()
                    }
                }
            }
        }

        safeListener<InputEvent.MouseInputEvent> {
            mc.objectMouseOver?.let { result ->
                if (result.typeOfHit != RayTraceResult.Type.BLOCK) return@safeListener

                when (Mouse.getEventButton()) {
                    0 -> firstPos = result.blockPos
                    1 -> secondPos = result.blockPos
                    2 -> clearArea()
                }
            }
        }
    }

    private fun clearArea() {
        val first = firstPos ?: return
        val second = secondPos ?: return

        ClearArea(
            first,
            second,
            layerSize,
            sliceSize,
        ).let {
            ownedBuildStructure = it
            ActivityManager.addSubActivities(it)
        }
    }
}
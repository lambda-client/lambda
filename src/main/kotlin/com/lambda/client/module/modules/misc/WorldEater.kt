package com.lambda.client.module.modules.misc

import com.lambda.client.activity.activities.construction.ClearArea
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.setting.settings.impl.collection.CollectionSetting
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.VectorUtils.multiply
import com.lambda.client.util.threads.runSafe
import net.minecraft.item.Item
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
    private val collectAll by setting("Collect all", false)
    private val start by setting("Start", false, consumer = { _, _ ->
        runSafe {
            val origin = player.flooredPosition
            val currentDirection = player.horizontalFacing
            val firstPos = origin.add(currentDirection.directionVec)
            val secondPos = origin.add(
                currentDirection.directionVec.multiply(size)
            ).add(
                currentDirection.rotateY().directionVec.multiply(size)
            ).down(depth)

            startClearingArea(firstPos, secondPos)
        }
        false
    })
    val collectables = setting(CollectionSetting("Pick up items", linkedSetOf("minecraft:dirt", "minecraft:cobblestone")))
    val pos1 = setting("Pos1", BlockPos.ORIGIN)
    val pos2 = setting("Pos2", BlockPos.ORIGIN)

    val stashes = setting(CollectionSetting("Stashes", linkedSetOf<BlockPos>()))
    val dropOff = setting(CollectionSetting("Drop off", linkedSetOf<BlockPos>()))

    val pickUp: List<Item>
        get() = collectables.value.mapNotNull { Item.getByNameOrId(it) }

    private var ownedBuildStructure: ClearArea? = null

    init {
        onEnable {
            runSafe {
                startClearingArea()
            }
        }

        onDisable {
            runSafe {
                ownedBuildStructure?.let {
                    with(it) {
                        cancel()
                    }
                }
                ownedBuildStructure = null
            }
        }
    }

    fun startClearingArea(
        pos1: BlockPos = this.pos1.value,
        pos2: BlockPos = this.pos2.value
    ) {
        ClearArea(
            pos1,
            pos2,
            layerSize,
            sliceSize,
            collectAll = collectAll
        ).also {
            ownedBuildStructure = it
            addSubActivities(it)
        }
    }
}
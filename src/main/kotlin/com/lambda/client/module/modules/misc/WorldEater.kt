package com.lambda.client.module.modules.misc

import com.lambda.client.activity.activities.construction.ClearArea
import com.lambda.client.command.CommandManager.prefix
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.setting.settings.impl.collection.CollectionSetting
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.items.item
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.runSafe
import net.minecraft.init.Blocks
import net.minecraft.item.Item
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

object WorldEater : Module(
    name = "WorldEater",
    description = "Full auto excavation",
    category = Category.MISC,
    alias = arrayOf("we")
) {
    private val layerSize by setting("Layers size", 1, 1..6, 1)
    private val sliceSize by setting("Slice size", 1, 1..6, 1)
    private val sliceDirection by setting("Slice direction", EnumFacing.NORTH)
    private val collectAll by setting("Collect all", false)

    private val defaultPickupItems = linkedSetOf(
        Blocks.DIRT.item,
        Blocks.GRASS.item,
        Blocks.STONE.item,
        Blocks.COBBLESTONE.item,
        Blocks.GRAVEL.item,
        Blocks.SAND.item,
        Blocks.SANDSTONE.item,
        Blocks.RED_SANDSTONE.item,
        Blocks.CLAY.item
    )

    val tools = setting(CollectionSetting("Tools", mutableListOf(), entryType = Item::class.java))

    val collectables = setting(CollectionSetting("Pick up items", defaultPickupItems, entryType = Item::class.java))
    val quarries = setting(CollectionSetting("Quarries", mutableListOf(), entryType = Area::class.java))
    val stashes = setting(CollectionSetting("Stashes", mutableListOf(), entryType = Stash::class.java))
    val dropOff = setting(CollectionSetting("Drop offs", mutableListOf(), entryType = Stash::class.java))

    var ownedActivity: ClearArea? = null

    init {
        onEnable {
            if (quarries.value.isEmpty()) {
                MessageSendHelper.sendChatMessage("No quarries set yet. Use &7${prefix}we quarry add&r to add one.")
                disable()
                return@onEnable
            }

            runSafe {
                clearAllAreas()
            }
        }

        onDisable {
            runSafe {
                ownedActivity?.let {
                    with(it) {
                        cancel()
                    }
                }
                ownedActivity = null
            }
        }
    }

    fun SafeClientEvent.clearAllAreas() {
        quarries.value.minByOrNull { player.distanceTo(it.pos1) }?.let { area ->
            MessageSendHelper.sendChatMessage("Start excavating closest area: $area")
            ClearArea(
                area,
                layerSize,
                sliceSize,
                sliceDirection,
                collectAll = collectAll
            ).also {
                ownedActivity = it
                addSubActivities(it)
            }
        }
    }

    data class Stash(val area: Area, val items: List<Item>) {
        override fun toString() = "$area\n  ${items.joinToString("\n  ") {
            "&7+&r ${it.registryName.toString()}"
        }}"
    }

    data class Area(val pos1: BlockPos, val pos2: BlockPos) {
        val center: BlockPos
            get() = BlockPos(
                (pos1.x + pos2.x) / 2,
                (pos1.y + pos2.y) / 2,
                (pos1.z + pos2.z) / 2
            )

        val SafeClientEvent.playerInArea: Boolean
            get() = player.flooredPosition.x in minX..maxX
                && player.flooredPosition.z in minZ..maxZ

        val containedBlockPositions: Set<BlockPos>
            get() = BlockPos.getAllInBox(pos1, pos2).toSet()

        val maxWidth: Int
            get() = maxOf(maxX - minX + 1, maxZ - minZ + 1)

        val minX: Int
            get() = minOf(pos1.x, pos2.x)
        val minY: Int
            get() = minOf(pos1.y, pos2.y)
        val minZ: Int
            get() = minOf(pos1.z, pos2.z)
        val maxX: Int
            get() = maxOf(pos1.x, pos2.x)
        val maxY: Int
            get() = maxOf(pos1.y, pos2.y)
        val maxZ: Int
            get() = maxOf(pos1.z, pos2.z)

        override fun toString() = "(${pos1.asString()}x${pos2.asString()})"
    }
}
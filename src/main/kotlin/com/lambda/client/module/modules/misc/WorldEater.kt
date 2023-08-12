package com.lambda.client.module.modules.misc

import com.lambda.client.activity.activities.construction.ClearArea
import com.lambda.client.activity.activities.construction.core.BuildStructure
import com.lambda.client.activity.activities.storage.types.Area
import com.lambda.client.activity.activities.storage.types.Stash
import com.lambda.client.command.CommandManager.prefix
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.setting.settings.impl.collection.CollectionSetting
import com.lambda.client.util.items.item
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.runSafe
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.util.EnumFacing

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
        Blocks.CLAY.item,
        Blocks.IRON_ORE.item,
        Blocks.GOLD_ORE.item,
        Items.COAL,
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
                ActivityManager.reset() // ToDo: Should also cancel the maintain inventory activity
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

    fun SafeClientEvent.info() = "WorldEater:\n&7Progress&r:${
            "%.3f".format(quarries.value.sumOf { it.containedBlocks.count { pos -> world.isAirBlock(pos) } }.toFloat() / quarries.value.sumOf { it.containedBlocks.size } * 100)
        }% with ${
            ownedActivity?.subActivities?.filterIsInstance<BuildStructure>()?.size
        } layers left.\n&7Pickup&r:${collectables.value.joinToString {
            "${it.registryName?.path}"
        }}\n&7Quarries&r: ${
            if (quarries.value.isEmpty()) "None"
            else quarries.value.joinToString { "${quarries.indexOf(it) + 1}: $it" }
        }\n&7Stashes&r: ${
            if (stashes.value.isEmpty()) "None"
            else stashes.value.joinToString { "${stashes.indexOf(it) + 1}: $it" }
        }\n&7Drop-off&r: ${
            if (dropOff.value.isEmpty()) "None"
            else dropOff.value.joinToString { "${dropOff.indexOf(it) + 1}: $it" }
        }"
}
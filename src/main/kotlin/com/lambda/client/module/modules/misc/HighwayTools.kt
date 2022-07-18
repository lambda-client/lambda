package com.lambda.client.module.modules.misc

import com.lambda.client.buildtools.BuildToolsManager.buildStructure
import com.lambda.client.buildtools.blueprint.StructureTask
import com.lambda.client.buildtools.blueprint.strategies.MoveXStrategy
import com.lambda.client.buildtools.task.TaskFactory
import com.lambda.client.buildtools.task.sequence.strategies.OriginStrategy
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.Direction
import com.lambda.client.util.threads.runSafe
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos

object HighwayTools : Module(
    name = "HighwayTools",
    description = "Be the grief a step a head.",
    category = Category.MISC,
    alias = arrayOf("HT", "HWT")
) {
    private val structure by setting("Structure", Structure.HIGHWAY, description = "Choose the structure")
    private val width by setting("Width", 6, 1..11, 1, description = "Sets the width of blueprint", unit = " blocks")
    private val height by setting("Height", 4, 2..6, 1, { clearSpace }, description = "Sets height of blueprint", unit = " blocks")
    private val backfill by setting("Backfill", false, { structure == Structure.TUNNEL }, description = "Fills the tunnel behind you")
    private val clearSpace by setting("Clear Space", true, { structure == Structure.HIGHWAY }, description = "Clears out the tunnel if necessary")
    private val cleanFloor by setting("Clean Floor", false, { structure == Structure.TUNNEL && !backfill }, description = "Cleans up the tunnels floor")
    private val cleanRightWall by setting("Clean Right Wall", false, { structure == Structure.TUNNEL && !backfill }, description = "Cleans up the right wall")
    private val cleanLeftWall by setting("Clean Left Wall", false, { structure == Structure.TUNNEL && !backfill }, description = "Cleans up the left wall")
    private val cleanRoof by setting("Clean Roof", false, { structure == Structure.TUNNEL && !backfill }, description = "Cleans up the tunnels roof")
    private val cleanCorner by setting("Clean Corner", false, { structure == Structure.TUNNEL && !cornerBlock && !backfill && width > 2 }, description = "Cleans up the tunnels corner")
    private val cornerBlock by setting("Corner Block", false, { structure == Structure.HIGHWAY || (structure == Structure.TUNNEL && !backfill && width > 2) }, description = "If activated will break the corner in tunnel or place a corner while paving")
    private val railing by setting("Railing", true, { structure == Structure.HIGHWAY }, description = "Adds a railing/rim/border to the highway")
    private val railingHeight by setting("Railing Height", 1, 1..4, 1, { structure == Structure.HIGHWAY && railing }, description = "Sets height of railing", unit = " blocks")

    enum class Structure {
        HIGHWAY, TUNNEL
    }

    private var originDirection = Direction.NORTH
    private var originPosition = BlockPos.ORIGIN
    var distance = 0 // 0 means infinite

    var material: Block
        get() = Block.getBlockFromName(materialSaved.value) ?: Blocks.OBSIDIAN
        set(value) {
            materialSaved.value = value.registryName.toString()
        }

    init {
        onEnable {
            runSafe {
                originPosition = player.flooredPosition
                originDirection = Direction.fromEntity(player)

                printEnable()

                val structureTask = StructureTask(
                    generateBlueprint(),
                    blueprintStrategy = MoveXStrategy(originPosition, originDirection, 1, 0, hasStartPadding = true),
                    taskSequenceStrategy = OriginStrategy
                )

                buildStructure(structureTask)
            }
        }
    }

    private fun SafeClientEvent.generateBlueprint(): HashMap<BlockPos, TaskFactory.BlueprintTask> {
        val blueprint = hashMapOf<BlockPos, TaskFactory.BlueprintTask>()

        val origin = player.flooredPosition.add(originDirection.directionVec)

        (1..height).forEach {
            blueprint[origin.up(it)] = TaskFactory.BlueprintTask(Blocks.OBSIDIAN)
        }

        return blueprint
    }

    private fun SafeClientEvent.printEnable() {

    }

    fun printSettings() {

    }

    private val materialSaved = setting("Material", "minecraft:obsidian", { false })
}
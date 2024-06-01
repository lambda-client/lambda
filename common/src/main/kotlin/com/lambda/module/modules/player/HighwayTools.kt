package com.lambda.module.modules.player

import baritone.api.pathing.goals.GoalNear
import com.lambda.interaction.construction.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.Task
import com.lambda.task.tasks.BuildStructure.Companion.buildStructure
import com.lambda.util.BaritoneUtils.primary
import com.lambda.util.Communication.info
import com.lambda.util.KeyCode
import com.lambda.util.player.MovementUtils.direction
import com.lambda.util.primitives.extension.Structure
import com.lambda.util.world.StructureUtils.generateDirectionalTube
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.EightWayDirection
import net.minecraft.util.math.Vec3i
import kotlin.math.roundToInt

object HighwayTools : Module(
    name = "HighwayTools",
    description = "Auto highway builder",
    defaultTags = setOf(ModuleTag.PLAYER, ModuleTag.AUTOMATION),
    defaultKeybind = KeyCode.X
) {
    private val height by setting("Height", 4, 1..10, 1)
    private val width by setting("Width", 6, 1..30, 1)
    private val rimHeight by setting("Rim Height", 0, 1..6, 1)
    private val cornerBlock by setting("Corner Block", false, description = "Include corner blocks in the highway")
    private val material = Blocks.OBSIDIAN
    private val distance by setting("Distance", -1, -1..1000000, 1, description = "Distance to build the highway (negative for infinite)")
    // ToDo: Fix block setting
//    private val material by setting("Material", Blocks.OBSIDIAN, description = "Material to build the highway with")

    private var direction = EightWayDirection.NORTH
    private var distanceMoved = 0
    private var startPos = BlockPos.ORIGIN
    private var currentPos = BlockPos.ORIGIN
    private var runningTask: Task<*>? = null

    init {
        onEnable {
            direction = player.direction()
            startPos = player.blockPos
            currentPos = startPos
            buildSlice()
        }
        onDisable {
            runningTask?.cancel()
            runningTask = null
            distanceMoved = 0
        }
    }

    private fun buildSlice() {
        val blueprint = generateHighway()
            .map { it.key.add(currentPos) to it.value }
            .toMap()
            .toBlueprint()

        buildStructure {
            blueprint
        }.apply {
            runningTask = this
            primary.customGoalProcess.setGoalAndPath(GoalNear(currentPos, 1))
            onSuccess { _, _ ->
                distanceMoved++

                val vec = Vec3i(direction.offsetX, 0, direction.offsetZ)
                currentPos = currentPos.add(vec)

                if (distanceMoved < distance || distance < 0) {
                    buildSlice()
                } else {
                    this@HighwayTools.info("Highway built")
                    disable()
                }
            }
            start(null)
        }
    }

    private fun generateHighway(): Structure {
        val structure = mutableMapOf<BlockPos, TargetState>()
        val orthogonal = EightWayDirection.entries[(direction.ordinal + 2).mod(8)]
        val center = (width / 2.0).roundToInt()

        // Area to clear
        structure += generateDirectionalTube(
            orthogonal,
            width,
            height,
            -center,
            -1,
        ).associateWith { TargetState.Air }

        // Highway
        structure += generateDirectionalTube(
            orthogonal,
            width,
            1,
            -center,
            -1,
        ).associateWith { TargetState.Block(material) }

        // Left rim
        structure += generateDirectionalTube(
            orthogonal,
            1,
            rimHeight,
            -center + width - 1,
            0,
        ).associateWith { TargetState.Block(material) }

        // Right rim
        structure += generateDirectionalTube(
            orthogonal,
            1,
            rimHeight,
            -center,
            0,
        ).associateWith { TargetState.Block(material) }

        if (!cornerBlock) {
            structure -= generateDirectionalTube(
                orthogonal,
                1,
                1,
                -center + width - 1,
                -1,
            )

            structure -= generateDirectionalTube(
                orthogonal,
                1,
                1,
                -center,
                -1,
            )

            // Remove the left corner
//            structure += generateDirectionalTube(
//                orthogonal,
//                1,
//                1,
//                -center + width - 1,
//                -1,
//            ).associateWith { TargetState.Support(Direction.UP) }

            // Remove the right corner
//            structure += generateDirectionalTube(
//                orthogonal,
//                1,
//                1,
//                -center,
//                -1,
//            ).associateWith { TargetState.Support(Direction.UP) }
        }

        return structure
    }
}
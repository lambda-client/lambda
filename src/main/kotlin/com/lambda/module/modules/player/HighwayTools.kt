/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.player

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.interaction.BaritoneManager
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.emptyStructure
import com.lambda.interaction.construction.blueprint.PropagatingBlueprint.Companion.propagatingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.Communication.info
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.extension.Structure
import com.lambda.util.extension.moveY
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.rotateClockwise
import com.lambda.util.player.MovementUtils.octant
import com.lambda.util.world.StructureUtils.generateDirectionalTube
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.EightWayDirection
import net.minecraft.util.math.Vec3i

object HighwayTools : Module(
    name = "HighwayTools",
    description = "Auto highway builder",
    tag = ModuleTag.PLAYER,
) {
    private val height by setting("Height", 4, 2..10, 1)
    private val width by setting("Width", 6, 1..30, 1)
    private val pavement by setting("Pavement", Material.Block, "Material for the pavement")
    private val rimHeight by setting("Pavement Rim Height", 1, 0..6, 1) { pavement != Material.None }
    private val cornerBlock by setting("Corner", Corner.None, "Include corner blocks in the highway") { pavement != Material.None }
    private val pavementMaterial by setting("Pavement Material", Blocks.OBSIDIAN, "Material to build the highway with") { pavement == Material.Block }
    private val floor by setting("Floor", Material.None, "Material for the floor")
    private val floorMaterial by setting("Floor Material", Blocks.NETHERRACK, "Material to build the floor with") { floor == Material.Block }
    private val rightWall by setting("Right Wall", Material.None, "Build the right wall")
    private val leftWall by setting("Left Wall", Material.None, "Build the left wall")
    private val wallMaterial by setting("Wall Material", Blocks.NETHERRACK, "Material to build the walls with") { rightWall == Material.Block || leftWall == Material.Block }
    private val ceiling by setting("Ceiling", Material.None, "Material for the ceiling")
    private val ceilingMaterial by setting("Ceiling Material", Blocks.OBSIDIAN, "Material to build the ceiling with") { ceiling == Material.Block }
    private val replaceableSolids by setting("Replaceable Solids", setOf(Blocks.MAGMA_BLOCK, Blocks.SOUL_SAND))
    private val distance by setting("Distance", -1, -1..1000000, 1, "Distance to build the highway/tunnel (negative for infinite)")
    private val sliceSize by setting("Slice Size", 3, 1..5, 1, "Number of slices to build at once")

    private var octant = EightWayDirection.NORTH
    private var distanceMoved = 0
    private var startPos = BlockPos.ORIGIN
    private var currentPos = BlockPos.ORIGIN
    private var runningTask: Task<*>? = null

    enum class Material(
        override val displayName: String,
        override val description: String
    ): NamedEnum, Describable {
        None("None", "Wont pave the highway"),
        Solid("Solid", "Paves the highway with solid blocks. Will use any full block available. Useful if you only want to make sure that the highway is encased."),
        Block("Block", "Paves the highway with a specific block. Will use the block you specified in the settings"),
    }

    enum class Corner(
        override val displayName: String,
        override val description: String
    ): NamedEnum, Describable {
        None("None", "Wont fill the corner block of the highway pavement below the rims."),
        Solid("Solid", "Fills the corner block of the highway pavement below the rims with solid blocks."),
    }

    init {
		setDefaultAutomationConfig {
            applyEdits {
                buildConfig.apply {
                    editTyped(::pathing, ::stayInRange) { defaultValue(true) }
                }
            }
        }

        onEnable {
            octant = player.octant
            startPos = player.blockPos
            currentPos = startPos
            buildHighway()
        }
        onDisable {
            runningTask?.cancel()
            runningTask = null
            distanceMoved = 0
            BaritoneManager.cancel()
        }
    }

    private fun buildHighway() {
        runningTask = propagatingBlueprint {
            if (distance !in 0..distanceMoved) {
                var structure = emptyStructure()
                val slice = generateSlice()
                repeat(sliceSize) {
                    structure = structure.plus(slice.map { it.key.add(currentPos) to it.value })
                    val vec = Vec3i(octant.offsetX, 0, octant.offsetZ)
                    currentPos = currentPos.add(vec)
                }
                distanceMoved += sliceSize
                structure
            } else {
                this@HighwayTools.info("Highway built")
                disable()
                emptyStructure()
            }
        }.build(collectDrops = buildConfig.collectDrops, lifeMaintenance = true)
            .run()
    }

    private fun generateSlice(): Structure {
        val structure = mutableMapOf<BlockPos, TargetState>()
        val orthogonal = octant.rotateClockwise(2)
        val center = (width / 2.0).floorToInt()

        // Hole
        structure += generateDirectionalTube(
            orthogonal,
            width,
            height,
            -center,
            0,
        ).associateWith { TargetState.Air }

        if (pavement != Material.None) {
            structure += generateDirectionalTube(
                orthogonal,
                width,
                1,
                -center,
                0,
            ).associateWith { target(pavement, pavementMaterial) }

            // Left rim
            structure += generateDirectionalTube(
                orthogonal,
                1,
                rimHeight,
                -center + width - 1,
                1,
            ).associateWith { target(pavement, pavementMaterial) }

            // Right rim
            structure += generateDirectionalTube(
                orthogonal,
                1,
                rimHeight,
                -center,
                1,
            ).associateWith { target(pavement, pavementMaterial) }

            if (cornerBlock == Corner.None && rimHeight > 0) {
                // Support for the left rim
                structure += generateDirectionalTube(
                    orthogonal,
                    1,
                    1,
                    -center + width - 1,
                    0,
                ).associateWith { TargetState.Support(Direction.UP) }

                // Support for the right rim
                structure += generateDirectionalTube(
                    orthogonal,
                    1,
                    1,
                    -center,
                    0,
                ).associateWith { TargetState.Support(Direction.UP) }
            }
        }

        if (ceiling != Material.None) {
            structure += generateDirectionalTube(
                orthogonal,
                width,
                1,
                -center,
                height,
            ).associateWith { target(ceiling, ceilingMaterial) }
        }

        val wallElevation = if (pavement != Material.None) rimHeight else 0 + if (pavement != Material.None) 1 else 0
        if (rightWall != Material.None) {
            structure += generateDirectionalTube(
                orthogonal,
                1,
                height - wallElevation,
                -center + width,
                wallElevation,
            ).associateWith { target(rightWall, wallMaterial) }
        }

        if (leftWall != Material.None) {
            structure += generateDirectionalTube(
                orthogonal,
                1,
                height - wallElevation,
                -center - 1,
                wallElevation,
            ).associateWith { target(leftWall, wallMaterial) }
        }

        if (floor != Material.None) {
            structure += generateDirectionalTube(
                orthogonal,
                width,
                1,
                -center,
                -1,
            ).associateWith { target(floor, floorMaterial) }
        }

        val transformed = when {
            pavement != Material.None -> structure.moveY(-1)
            else -> structure
        }

        return transformed
    }

    private fun target(target: Material, material: net.minecraft.block.Block) =
        when (target) {
            Material.Solid -> TargetState.Solid(replaceableSolids)
            Material.Block -> TargetState.Block(material)
            else -> throw IllegalStateException("Invalid material")
        }
}

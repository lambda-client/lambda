/*
 * Copyright 2024 Lambda
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

import com.lambda.config.groups.*
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.emptyStructure
import com.lambda.interaction.construction.blueprint.PropagatingBlueprint.Companion.propagatingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.Task
import com.lambda.task.RootTask.run
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BaritoneUtils
import com.lambda.util.Communication.info
import com.lambda.util.extension.Structure
import com.lambda.util.extension.moveY
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.rotateClockwise
import com.lambda.util.player.MovementUtils.octant
import com.lambda.util.world.StructureUtils.generateDirectionalTube
import com.lambda.util.world.raycast.InteractionMask
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.EightWayDirection
import net.minecraft.util.math.Vec3i

object HighwayTools : Module(
    name = "HighwayTools",
    description = "Auto highway builder",
    defaultTags = setOf(ModuleTag.PLAYER, ModuleTag.AUTOMATION)
) {
    private val page by setting("Page", Page.Structure)

    private val height by setting("Height", 4, 2..10, 1, "Height of the full tunnel tube including the pavement", " blocks") { page == Page.Structure }
    private val width by setting("Width", 6, 1..30, 1, "Width of the full tunnel tube including the pavements rims", " blocks") { page == Page.Structure }
    private val pavement by setting("Pavement", Material.Block, "Material for the pavement") { page == Page.Structure }
    private val rimHeight by setting("Pavement Rim Height", 1, 0..6, 1, "Height of the pavements rims where 0 is none", " blocks") { page == Page.Structure && pavement != Material.None }
    private val cornerBlock by setting("Corner", Corner.None, "Include corner blocks in the highway") { page == Page.Structure && pavement != Material.None }
    private val pavementMaterial by setting("Pavement Material", Blocks.OBSIDIAN, "Material to build the highway with") { page == Page.Structure && pavement == Material.Block }
    private val floor by setting("Floor", Material.None, "Material for the floor") { page == Page.Structure }
    private val floorMaterial by setting("Floor Material", Blocks.NETHERRACK, "Material to build the floor with") { page == Page.Structure && floor == Material.Block }
    private val walls by setting("Walls", Material.None, "Material for the walls") { page == Page.Structure }
    private val wallMaterial by setting("Wall Material", Blocks.NETHERRACK, "Material to build the walls with") { page == Page.Structure && walls == Material.Block }
    private val ceiling by setting("Ceiling", Material.None, "Material for the ceiling") { page == Page.Structure }
    private val ceilingMaterial by setting("Ceiling Material", Blocks.OBSIDIAN, "Material to build the ceiling with") { page == Page.Structure && ceiling == Material.Block }
    private val distance by setting("Distance", -1, -1..1000000, 1, "Distance to build the highway/tunnel (negative for infinite)", " blocks") { page == Page.Structure }
    private val sliceSize by setting("Slice Size", 3, 1..5, 1, "Number of slices to build at once", " blocks") { page == Page.Structure }

    private val build = BuildSettings(this) { page == Page.Build }
    private val rotation = RotationSettings(this) { page == Page.Rotation }
    private val interact = InteractionSettings(this, InteractionMask.Block) { page == Page.Interaction }
    private val inventory = InventorySettings(this) { page == Page.Inventory }

    private var octant = EightWayDirection.NORTH
    private var distanceMoved = 0
    private var startPos = BlockPos.ORIGIN
    private var currentPos = BlockPos.ORIGIN
    private var runningTask: Task<*>? = null

    enum class Material {
        None, Solid, Block
    }

    enum class Corner {
        None, Solid
    }

    enum class Page {
        Structure, Build, Rotation, Interaction, Inventory
    }

    init {
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
            BaritoneUtils.cancel()
        }
    }

    private fun buildHighway() {
        runningTask = propagatingBlueprint {
            if (distanceMoved < distance || distance < 0) {
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
        }.build(
            build = build,
            rotation = rotation,
            interact = interact,
            inventory = inventory,
        ).run()
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

        if (walls != Material.None) {
            val wallElevation = rimHeight + if (pavement != Material.None) 1 else 0

            // Left wall
            structure += generateDirectionalTube(
                orthogonal,
                1,
                height - wallElevation,
                -center + width,
                wallElevation,
            ).associateWith { target(walls, wallMaterial) }

            // Right wall
            structure += generateDirectionalTube(
                orthogonal,
                1,
                height - wallElevation,
                -center - 1,
                wallElevation,
            ).associateWith { target(walls, wallMaterial) }
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

    private fun target(target: Material, material: net.minecraft.block.Block): TargetState {
        return when (target) {
            Material.Solid -> TargetState.Solid
            Material.Block -> TargetState.Block(material)
            else -> throw IllegalStateException("Invalid material")
        }
    }
}

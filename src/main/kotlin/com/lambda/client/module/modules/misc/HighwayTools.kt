package com.lambda.client.module.modules.misc

import com.lambda.client.activity.activities.highlevel.BuildStructure
import com.lambda.client.commons.extension.floorToInt
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.client.BuildTools.defaultFillerMat
import com.lambda.client.module.modules.client.BuildTools.maxReach
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.VectorUtils.multiply
import com.lambda.client.util.threads.runSafe
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos

object HighwayTools : Module(
    name = "HighwayTools",
    description = "Be the grief a step a head.",
    category = Category.MISC,
    alias = arrayOf("HT", "HWT")
) {
    private val structure by setting("Structure", Structure.HIGHWAY, description = "Choose the structure")
    private val width by setting("Width", 6, 1..50, 1, description = "Sets the width of blueprint", unit = " blocks")
    private val height by setting("Height", 4, 2..10, 1, { clearSpace }, description = "Sets height of blueprint", unit = " blocks")
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
    private var originOrthogonalDirection = Direction.NORTH
    private var originPosition = BlockPos.ORIGIN
    var distance = 0 // 0 means infinite

    private var ownedBuildStructure: BuildStructure? = null

    private var material: Block
        get() = Block.getBlockFromName(materialSaved.value) ?: Blocks.OBSIDIAN
        set(value) {
            materialSaved.value = value.registryName.toString()
        }

    init {
        onEnable {
            runSafe {
                originPosition = player.flooredPosition.down()
                originDirection = Direction.fromEntity(player)
                originOrthogonalDirection = originDirection.clockwise(if (originDirection.isDiagonal) 1 else 2)

                printEnable()

                BuildStructure(
                    generateHighway(),
                    direction = originDirection,
                    offsetMove = BlockPos(originDirection.directionVec),
                    maximumRepeats = 0,
                    respectIgnore = true
                ).let {
                    ownedBuildStructure = it
                    ActivityManager.addSubActivities(it)
                }
            }
        }

        onDisable {
            ActivityManager.reset()
        }
    }

    private fun generateHighway(): HashMap<BlockPos, IBlockState> {
        val blueprint = hashMapOf<BlockPos, IBlockState>()

        for (x in -width..width) {
            val thisPos = originPosition.add(originDirection.directionVec.multiply(x))
            if (clearSpace) generateClear(blueprint, thisPos)

            if (structure == Structure.TUNNEL) {
                if (cleanFloor) generateFloor(blueprint, thisPos)
                if (cleanRightWall || cleanLeftWall) generateWalls(blueprint, thisPos)
                if (cleanRoof) generateRoof(blueprint, thisPos)
                if (cleanCorner && !cornerBlock && width > 2) generateCorner(blueprint, thisPos)
            } else {
                generateBase(blueprint, thisPos)
            }
        }

        if (structure == Structure.TUNNEL && (!cleanFloor || backfill)) {
            if (originDirection.isDiagonal) {
                for (x in 0..width) {
                    val pos = originPosition.add(originDirection.directionVec.multiply(x))
                    blueprint[pos] = fillerState()
                    blueprint[pos.add(originDirection.clockwise(7).directionVec)] = fillerState()
                }
            } else {
                for (x in 0..width) {
                    blueprint[originPosition.add(originDirection.directionVec.multiply(x))] = fillerState()
                }
            }
        }

        return blueprint
    }

    private fun generateClear(blueprint: HashMap<BlockPos, IBlockState>, basePos: BlockPos) {
        for (w in 0 until width) {
            for (h in 0 until height) {
                val x = w - width / 2
                val pos = basePos.add(originOrthogonalDirection.directionVec.multiply(x)).up(h)

                if (structure == Structure.HIGHWAY && h == 0 && isRail(w)) {
                    continue
                }

                if (structure == Structure.HIGHWAY) {
                    blueprint[pos] = Blocks.AIR.defaultState
                } else {
                    if (!(isRail(w) && h == 0 && !cornerBlock && width > 2)) blueprint[pos.up()] = Blocks.AIR.defaultState
                }
            }
        }
    }

    private fun generateBase(blueprint: HashMap<BlockPos, IBlockState>, basePos: BlockPos) {
        for (w in 0 until width) {
            val x = w - width / 2
            val pos = basePos.add(originOrthogonalDirection.directionVec.multiply(x))

            if (structure == Structure.HIGHWAY && isRail(w)) {
                if (!cornerBlock && width > 2 && originDirection.isDiagonal) blueprint[pos] = fillerState() // support block
                val startHeight = if (cornerBlock && width > 2) 0 else 1
                for (y in startHeight..railingHeight) {
                    blueprint[pos.up(y)] = material.defaultState
                }
            } else {
                blueprint[pos] = material.defaultState
            }
        }
    }

    private fun generateFloor(blueprint: HashMap<BlockPos, IBlockState>, basePos: BlockPos) {
        val wid = if (cornerBlock && width > 2) {
            width
        } else {
            width - 2
        }
        for (w in 0 until wid) {
            val x = w - wid / 2
            val pos = basePos.add(originOrthogonalDirection.directionVec.multiply(x))
            blueprint[pos] = fillerState()
        }
    }

    private fun generateWalls(blueprint: HashMap<BlockPos, IBlockState>, basePos: BlockPos) {
        val cb = if (!cornerBlock && width > 2) {
            1
        } else {
            0
        }
        for (h in cb until height) {
            if (cleanRightWall) blueprint[basePos.add(originOrthogonalDirection.directionVec.multiply(width - width / 2)).up(h + 1)] = fillerState()
            if (cleanLeftWall) blueprint[basePos.add(originOrthogonalDirection.directionVec.multiply(-1 - width / 2)).up(h + 1)] = fillerState()
        }
    }

    private fun generateRoof(blueprint: HashMap<BlockPos, IBlockState>, basePos: BlockPos) {
        for (w in 0 until width) {
            val x = w - width / 2
            val pos = basePos.add(originOrthogonalDirection.directionVec.multiply(x))
            blueprint[pos.up(height + 1)] = fillerState()
        }
    }

    private fun generateCorner(blueprint: HashMap<BlockPos, IBlockState>, basePos: BlockPos) {
        blueprint[basePos.add(originOrthogonalDirection.directionVec.multiply(-1 - width / 2 + 1)).up()] = fillerState()
        blueprint[basePos.add(originOrthogonalDirection.directionVec.multiply(width - width / 2 - 1)).up()] = fillerState()
    }

    private fun isRail(w: Int) = railing && w !in 1 until width - 1

    private fun SafeClientEvent.printEnable() {

    }

    fun printSettings() {

    }

    private fun fillerState() = defaultFillerMat.defaultState

    private val materialSaved = setting("Material", "minecraft:obsidian", { false })
}
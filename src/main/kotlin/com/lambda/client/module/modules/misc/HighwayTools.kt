package com.lambda.client.module.modules.misc

import com.lambda.client.activity.activities.construction.core.BuildStructure
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.elements.client.ActivityManagerHud
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.client.BuildTools.defaultFillerMat
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.math.Direction
import com.lambda.client.util.math.VectorUtils.multiply
import com.lambda.client.util.threads.runSafe
import net.minecraft.block.Block
import net.minecraft.block.BlockColored
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.item.EnumDyeColor
import net.minecraft.util.math.BlockPos

object HighwayTools : Module(
    name = "HighwayTools",
    description = "Be the grief a step a head.",
    category = Category.MISC,
    alias = arrayOf("HT", "HWT")
) {
    private val structure by setting("Structure", Structure.HIGHWAY, description = "Choose the structure")
    private val width by setting("Width", 6, 1..50, 1, description = "Sets the width of blueprint", unit = " blocks")
    private val height by setting("Height", 4, 2..10, 1, description = "Sets height of blueprint", unit = " blocks")
    private val offset by setting("Offset", 3, -10..10, 1, description = "Sets the offset of the structure", unit = " blocks")
    private val rainbowMode by setting("Rainbow Mode", false, description = "Rainbow highway uwu")

    private val backfill by setting("Backfill", false, { structure == Structure.TUNNEL }, description = "Fills the tunnel behind you")
    private val fillFloor by setting("Fill Floor", false, { structure == Structure.TUNNEL && !backfill }, description = "Cleans up the tunnels floor")
    private val fillRightWall by setting("Fill Right Wall", false, { structure == Structure.TUNNEL && !backfill }, description = "Cleans up the right wall")
    private val fillLeftWall by setting("Fill Left Wall", false, { structure == Structure.TUNNEL && !backfill }, description = "Cleans up the left wall")
    private val fillRoof by setting("Fill Roof", false, { structure == Structure.TUNNEL && !backfill }, description = "Cleans up the tunnels roof")
    private val fillCorner by setting("Fill Corner", false, { structure == Structure.TUNNEL && !cornerBlock && !backfill && width > 2 }, description = "Cleans up the tunnels corner")
    private val cornerBlock by setting("Corner Block", false, { structure == Structure.HIGHWAY || (structure == Structure.TUNNEL && !backfill && width > 2) }, description = "If activated will break the corner in tunnel or place a corner while paving")
    private val railingHeight by setting("Railing Height", 1, 0..4, 1, { structure == Structure.HIGHWAY }, description = "Sets height of railing", unit = " blocks")

    enum class Structure {
        HIGHWAY, TUNNEL
    }

    private enum class Pages {
        GENERAL, CLEAN_UP, MATERIALS
    }

    private var originDirection = Direction.NORTH
    private var originOrthogonalDirection = Direction.NORTH
    private var originPosition = BlockPos.ORIGIN
    var distance = 0 // 0 means infinite

    private var ownedBuildStructure: BuildStructure? = null

    var material: Block
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

                ActivityManagerHud.totalBlocksBroken = 0
                ActivityManagerHud.totalBlocksPlaced = 0

                BuildStructure(
                    generateHighway(),
                    direction = originDirection,
                    offsetMove = BlockPos(originDirection.directionVec.multiply(offset)),
                    maximumRepeats = distance,
                    doPadding = true
                ).let {
                    ownedBuildStructure = it
                    ActivityManager.addSubActivities(it)
                }
            }
        }

        onDisable {
            runSafe {
                ownedBuildStructure?.let {
                    with(it) {
                        cancel()
                    }
                }
            }
        }
    }

    private fun generateHighway(): HashMap<BlockPos, IBlockState> {
        val blueprint = hashMapOf<BlockPos, IBlockState>()

        for (x in -5..5) {
            val thisPos = originPosition.add(originDirection.directionVec.multiply(x))
            generateClear(blueprint, thisPos)

            if (structure == Structure.TUNNEL) {
                if (fillFloor) generateFloor(blueprint, thisPos)
                if (fillRightWall || fillLeftWall) generateWalls(blueprint, thisPos)
                if (fillRoof) generateRoof(blueprint, thisPos)
                if (fillCorner && !cornerBlock && width > 2) generateCorner(blueprint, thisPos)
            } else {
                generateBase(blueprint, thisPos)
            }
        }

        if (structure == Structure.TUNNEL && (!fillFloor || backfill)) {
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
                    blueprint[pos.up(y)] = if (rainbowMode) getRainbowBlockState(pos.up(y)) else material.defaultState
                }
            } else {
                blueprint[pos] = if (rainbowMode) getRainbowBlockState(pos) else material.defaultState
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
            if (fillRightWall) blueprint[basePos.add(originOrthogonalDirection.directionVec.multiply(width - width / 2)).up(h + 1)] = fillerState()
            if (fillLeftWall) blueprint[basePos.add(originOrthogonalDirection.directionVec.multiply(-1 - width / 2)).up(h + 1)] = fillerState()
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

    private fun isRail(w: Int) = railingHeight > 0 && w !in 1 until width - 1

    private fun SafeClientEvent.printEnable() {

    }

    fun printSettings() {

    }

    private fun getRainbowBlockState(pos: BlockPos): IBlockState {
        val rainbowColors = listOf(
            EnumDyeColor.PURPLE,
            EnumDyeColor.BLUE,
            EnumDyeColor.CYAN,
            EnumDyeColor.LIME,
            EnumDyeColor.YELLOW,
            EnumDyeColor.ORANGE,
            EnumDyeColor.RED
        )

        return Blocks.CONCRETE.defaultState.withProperty(
            BlockColored.COLOR,
            rainbowColors[(originPosition.subtract(pos).z + width / 2).mod(rainbowColors.size)]
        )
    }

    private fun fillerState() = defaultFillerMat.defaultState

    private val materialSaved = setting("Material", "minecraft:obsidian", { false })
}
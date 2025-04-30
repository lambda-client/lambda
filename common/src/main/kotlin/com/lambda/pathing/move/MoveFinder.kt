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

package com.lambda.pathing.move

import com.lambda.context.SafeContext
import com.lambda.pathing.PathingConfig
import com.lambda.pathing.goal.Goal
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.fluidState
import com.lambda.util.world.FastVector
import com.lambda.util.world.WorldUtils.traversable
import com.lambda.util.world.add
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.length
import com.lambda.util.world.toBlockPos
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.CampfireBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.LeavesBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.Items
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.EightWayDirection
import kotlin.reflect.KFunction1

object MoveFinder {
    private val nodeTypeCache = HashMap<FastVector, NodeType>()

    fun SafeContext.moveOptions(origin: FastVector, heuristic: KFunction1<FastVector, Double>, config: PathingConfig): Set<Move> {
        if (!traversable(origin.toBlockPos())) return setOf()
        return EightWayDirection.entries.flatMap { direction ->
            (-1..1).mapNotNull { y ->
                getPathNode(heuristic, origin, direction, y, config)
            }
        }.toSet()
    }

    private fun SafeContext.getPathNode(
        heuristic: KFunction1<FastVector, Double>,
        origin: FastVector,
        direction: EightWayDirection,
        height: Int,
        config: PathingConfig
    ): Move? {
        val offset = fastVectorOf(direction.offsetX, height, direction.offsetZ)
        val diagonal = direction.ordinal.mod(2) == 1
        val checkingPos = origin.add(offset)
        val checkingBlockPos = checkingPos.toBlockPos()
        val originBlockPos = origin.toBlockPos()
        if (!world.worldBorder.contains(checkingBlockPos)) return null

        val nodeType = findPathType(checkingPos)
        if (nodeType == NodeType.BLOCKED) return null

        val clear = if (diagonal) {
            val enclose = when {
                checkingBlockPos.y == originBlockPos.y -> Box.enclosing(originBlockPos.up(), checkingBlockPos)
                checkingBlockPos.y < originBlockPos.y -> Box.enclosing(originBlockPos.up(), checkingBlockPos.up())
                else -> Box.enclosing(originBlockPos.up(2), checkingBlockPos)
            }
            traversable(checkingBlockPos) && world.isSpaceEmpty(enclose.contract(0.01))
        } else {
            traversable(checkingBlockPos)
        }
        if (!clear) return null

        val hCost = heuristic(checkingPos) /** nodeType.penalty*/
        val cost = offset.length()
        val currentFeetY = getFeetY(checkingBlockPos)

        return when {
//            cost == Double.POSITIVE_INFINITY -> BreakMove(checkingPos, hCost, nodeType, currentFeetY, cost)
//            (currentFeetY - origin.feetY) > player.stepHeight -> ParkourMove(checkingPos, hCost, nodeType, currentFeetY, cost)
            else -> TraverseMove(checkingPos, hCost, nodeType, currentFeetY, cost)
        }
    }

    fun SafeContext.findPathType(pos: FastVector) = nodeTypeCache.getOrPut(pos) {
        val blockPos = pos.toBlockPos()
        val state = blockState(blockPos)
        val fluidState = fluidState(blockPos)

        when {
            state.isAir -> NodeType.OPEN
            fluidState.isIn(FluidTags.WATER) -> NodeType.WATER
            state.isFullCube(world, blockPos) -> NodeType.BLOCKED
            fluidState.isIn(FluidTags.LAVA) -> NodeType.LAVA
            state.isIn(BlockTags.LEAVES) && !state.getOrEmpty(LeavesBlock.PERSISTENT).orElse(false) -> NodeType.LEAVES
            state.isOf(Blocks.LADDER) -> NodeType.LADDER
            state.isOf(Blocks.SCAFFOLDING) -> NodeType.SCAFFOLDING
            state.isOf(Blocks.POWDER_SNOW) -> when {
                player.getEquippedStack(EquipmentSlot.FEET).isOf(Items.LEATHER_BOOTS) -> NodeType.DANGER_POWDER_SNOW
                else -> NodeType.POWDER_SNOW
            }
            state.isOf(Blocks.BIG_DRIPLEAF) -> NodeType.DRIP_LEAF
            state.isOf(Blocks.CACTUS) || state.isOf(Blocks.SWEET_BERRY_BUSH) -> NodeType.DAMAGE_OTHER
            state.isOf(Blocks.HONEY_BLOCK) -> NodeType.STICKY_HONEY
            state.isOf(Blocks.SLIME_BLOCK) -> NodeType.SLIME
            state.isOf(Blocks.SOUL_SAND) -> NodeType.SOUL_SAND
            state.isOf(Blocks.SOUL_SOIL) && EnchantmentHelper.getEquipmentLevel(Enchantments.SOUL_SPEED, player) > 0 -> NodeType.SOUL_SOIL
            state.isOf(Blocks.WITHER_ROSE) && state.isOf(Blocks.POINTED_DRIPSTONE) -> NodeType.DAMAGE_CAUTIOUS
            state.inflictsFireDamage() -> when {
                !player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE) -> NodeType.DAMAGE_FIRE
                else -> NodeType.DANGER_FIRE
            }
            state.isIn(BlockTags.DOORS) -> when {
                state.getOrEmpty(DoorBlock.OPEN).orElse(false) -> NodeType.DOOR_OPEN
                state.isIn(BlockTags.WOODEN_DOORS) -> NodeType.DOOR_WOOD_CLOSED
                else -> NodeType.DOOR_IRON_CLOSED
            }
            state.isIn(BlockTags.TRAPDOORS) -> when {
                state.getOrEmpty(TrapdoorBlock.OPEN).orElse(false) -> NodeType.TRAPDOOR_OPEN
                else -> NodeType.TRAPDOOR_CLOSED
            }
            state.isIn(BlockTags.FENCE_GATES) -> when {
                state.getOrEmpty(FenceGateBlock.OPEN).orElse(false) -> NodeType.FENCE_GATE_OPEN
                else -> NodeType.FENCE_GATE_CLOSED
            }
            state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.WALLS) -> NodeType.FENCE
            else -> NodeType.OPEN
        }

    }

    private fun BlockState.inflictsFireDamage() =
        isIn(BlockTags.FIRE)
            || isOf(Blocks.LAVA)
            || isOf(Blocks.MAGMA_BLOCK)
            || CampfireBlock.isLitCampfire(this)
            || isOf(Blocks.LAVA_CAULDRON)

    fun SafeContext.getFeetY(pos: BlockPos): Double {
        val blockPos = pos.down()
        val voxelShape = blockState(blockPos).getCollisionShape(world, blockPos)
        return blockPos.y.toDouble() + (if (voxelShape.isEmpty) 0.0 else voxelShape.getMax(Direction.Axis.Y))
    }

    fun clear(u: FastVector) = nodeTypeCache.remove(u)
    fun clean() = nodeTypeCache.clear()
}
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

package com.lambda.interaction.blockplace

import com.lambda.context.SafeContext
import com.lambda.util.Communication.info
import net.minecraft.block.*
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object PlaceInteraction {
    fun SafeContext.placeBlock(result: BlockHitResult, hand: Hand, swing: Boolean) {
        val actionResult = interaction.interactBlock(player, hand, result)

        when (actionResult) {
            ActionResult.PASS -> info("Internal interaction skipped")
            ActionResult.FAIL -> info("Internal interaction failed")
            else -> {}
        }

        if (!swing) return

        if (actionResult.shouldSwingHand()) {
            player.swingHand(hand)
        }

        if (!player.getStackInHand(hand).isEmpty && interaction.hasCreativeInventory()) {
            mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
        }
    }

    fun SafeContext.canPlaceAt(blockPos: BlockPos, state: BlockState = Blocks.OBSIDIAN.defaultState): Boolean {
        if (!World.isValid(blockPos)) return false
        if (!world.getBlockState(blockPos).isReplaceable) return false

        return world.canPlace(state, blockPos, ShapeContext.absent())
    }

    val BlockState.isClickable
        get() = isReplaceable ||
                block is CraftingTableBlock ||
                block is AnvilBlock ||
                block is ButtonBlock ||
                block is AbstractPressurePlateBlock ||
                block is BlockWithEntity ||
                block is BedBlock ||
                block is FenceGateBlock ||
                block is DoorBlock ||
                block is NoteBlock ||
                block is TrapdoorBlock
}

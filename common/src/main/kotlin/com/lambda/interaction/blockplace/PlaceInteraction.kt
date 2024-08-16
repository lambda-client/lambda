package com.lambda.interaction.blockplace

import com.lambda.context.SafeContext
import com.lambda.util.Communication.info
import net.minecraft.block.*
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object PlaceInteraction {
    fun SafeContext.placeBlock(result: BlockHitResult, hand: Hand, swing: Boolean) {
        val actionResult = interaction.interactBlock(player, hand, result)

        if (!actionResult.isAccepted) {
            info("Internal interaction failed with $actionResult")
            return
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

    val BlockState.isClickable get() = isReplaceable ||
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
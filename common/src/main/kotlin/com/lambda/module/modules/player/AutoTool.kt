package com.lambda.module.modules.player

import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.effect.StatusEffectUtil
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos

object AutoTool: Module(
    name = "AutoTool",
    description = "Automatically swaps to the most suitable tool",
    defaultTags = setOf(ModuleTag.PLAYER)
) {

    private var swapped = false
    private var returnSlot = -1

    init {
        listener<PacketEvent.Send.Pre> {
            if (it.packet is PlayerActionC2SPacket
                && (it.packet.action.equals(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK)
                        || it.packet.action.equals(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK)
                        || it.packet.action.equals(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK))
                ) run {
                    val state = world.getBlockState(it.packet.pos)

                if (state.isAir
                    || state.block.equals(Blocks.WATER)) {
                    return@listener
                }

                // Wasn't quite sure how to get this to work so for now ive just used temp methods for this part
//                ContainerManager.findBestAvailableTool(world.getBlockState(it.packet.pos))?.select()
//                    ?.transfer(MainHandContainer)

                val bestTool = getBestTool(state, it.packet.pos)
                if (player.inventory.selectedSlot != bestTool) {
                    if (!swapped) {
                        returnSlot = player.inventory.selectedSlot
                        swapped = true
                    }
                    player.inventory.selectedSlot = bestTool
                }
            }
        }

        listener<TickEvent.Pre> {
            if (swapped
                && returnSlot != -1
                && !interaction.isBreakingBlock) {
                player.inventory.selectedSlot = returnSlot
                swapped = false
                returnSlot = -1
            }
        }
    }
}

private fun SafeContext.getBestTool(state: BlockState, pos: BlockPos): Int {
    var bestTool = -1
    var bestTimeToMine = 0f

    for (i in 0..8) {
        val currentToolsTimeToMine = calcBreakDelta(state, pos, i)
        if (currentToolsTimeToMine > bestTimeToMine) {
            bestTimeToMine = currentToolsTimeToMine
            bestTool = i
        }
    }
    return bestTool
}

private fun SafeContext.calcBreakDelta(state: BlockState, pos: BlockPos, toolSlot: Int): Float {
    val f: Float = state.getHardness(world, pos)
    if (f == -1.0f) {
        return 0.0f
    } else {
        val i = if (!state.isToolRequired || player.inventory.getStack(toolSlot).isSuitableFor(state)) 30 else 100
        return getBlockBreakingSpeed(state, toolSlot) / f / i.toFloat()
    }
}

fun SafeContext.getBlockBreakingSpeed(state: BlockState, toolSlot: Int): Float {
    var f: Float = player.inventory.getStack(toolSlot).getMiningSpeedMultiplier(state)
    if (f > 1.0f) {
        val itemStack: ItemStack = player.inventory.getStack(toolSlot)
        val i = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, itemStack)
        if (i > 0 && !itemStack.isEmpty) {
            f += (i * i + 1).toFloat()
        }
    }

    if (StatusEffectUtil.hasHaste(player)) {
        f *= 1.0f + (StatusEffectUtil.getHasteAmplifier(player) + 1).toFloat() * 0.2f
    }

    if (player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
        val g = when (player.getStatusEffect(StatusEffects.MINING_FATIGUE)?.amplifier) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.0027f
                3 -> 8.1E-4f
                else -> 8.1E-4f
            }
        f *= g
    }

    if (player.isSubmergedIn(FluidTags.WATER)
        && !EnchantmentHelper.hasAquaAffinity(player)
    ) {
        f /= 5.0f
    }

    if (!player.isOnGround()) {
        f /= 5.0f
    }

    return f
}
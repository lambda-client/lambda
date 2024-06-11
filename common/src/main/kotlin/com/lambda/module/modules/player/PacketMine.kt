package com.lambda.module.modules.player

import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.block.BlockState
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.effect.StatusEffectUtil
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.fluid.Fluids
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket
import net.minecraft.registry.tag.FluidTags
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.util.function.BiConsumer

object PacketMine : Module(
    name = "Packet Mine",
    description = "Mines blocks semi-automatically at a faster rate",
    defaultTags = setOf(ModuleTag.PLAYER)
) {

    private val reBreak by setting("Re-Break", true, "Automatically re-breaks the last mined block if it gets replaced")
    private val fastReBreak by setting("Speedy Re-Break", false, "Re-breaks blocks instantly however could potentially cause ghost blocks", visibility = ::reBreak)

    private var currentMiningBlock: BlockData? = null
    private var reBreakBlock: BlockData? = null
    private var ignorePacketSend = false

    init {
        listener<TickEvent.Pre> {
            currentMiningBlock?.apply {
                this.mineTicks++

                if (this.awaitingResponse) return@listener

                val state = world.getBlockState(this.pos)

                if (state != this.state) {
                    this.state = state
                }

                val bestTool = getBestTool(state, this.pos)

                if (this.mineTicks * calcBreakDelta(this.state, this.pos, bestTool) > 0.7) {
                    if (!this.awaitingResponse) {
                        this.awaitingResponse = true
                        swapStopBreak(this.pos, bestTool)
                    }
                }
            } ?: reBreakBlock?.apply {
                if (player.eyePos.distanceTo(this.pos.toCenterPos()) > 6) {
                    reBreakBlock = null
                    return@listener
                }
                this.mineTicks++

                val state = world.getBlockState(this.pos)

                if (state != this.state) this.state = state

                val bestTool = getBestTool(this.state, this.pos)

                if (this.mineTicks * calcBreakDelta(this.state, this.pos, bestTool) > 0.7) {
                    if ((!state.isAir && (state.fluidState.isEmpty || state.properties.contains(Properties.WATERLOGGED)))
                        || fastReBreak) {
                        swapStopBreak(this.pos, bestTool)
                        if (fastReBreak) {
                            interaction.breakBlock(this.pos)
                        }
                    }
                }
            }
        }

        listener<PacketEvent.Send.Pre> {
            if (it.packet is PlayerActionC2SPacket
                && (it.packet.action.equals(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK)
                        || it.packet.action.equals(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK)
                        || it.packet.action.equals(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK)
                        )) {
                if (ignorePacketSend) return@listener

                it.cancel()

                val state = world.getBlockState(it.packet.pos)

                currentMiningBlock?.let {miningBlock ->
                    if (it.packet.pos.equals(miningBlock.pos)) return@listener
                }

                val bestTool = getBestTool(state, it.packet.pos)

                if (calcBreakDelta(world.getBlockState(it.packet.pos), it.packet.pos, bestTool) > 0.7) {
                    swapStartBreak(it.packet.pos, bestTool)
                    currentMiningBlock = null
                    if (reBreak) {
                        reBreakBlock = BlockData(it.packet.pos, state)
                    }
                    state.block.onBreak(world, it.packet.pos, state, player)
                } else {
                    swapStartPacketBreak(it.packet.pos, bestTool)
                    reBreakBlock = null
                    currentMiningBlock = BlockData(it.packet.pos, state)
                }
            }
        }

        listener<PacketEvent.Receive.Pre> {
            currentMiningBlock?.let { miningBlock ->
                if (it.packet is BlockUpdateS2CPacket
                    && it.packet.pos.equals(miningBlock.pos)
                    && (if (miningBlock.state.properties.contains(Properties.WATERLOGGED))
                        it.packet.state.fluidState.fluid.equals(Fluids.WATER)
                        else it.packet.state.isAir)) {
                    if (reBreak
                        && player.eyePos.distanceTo(miningBlock.pos.toCenterPos()) < 6) {
                        reBreakBlock = miningBlock
                    }
                    miningBlock.state.block.onBreak(world, miningBlock.pos, miningBlock.state, player)
                    currentMiningBlock = null
                } else if (it.packet is ChunkDeltaUpdateS2CPacket) {
                    it.packet.visitUpdates(BiConsumer { pos: BlockPos, state: BlockState ->
                        currentMiningBlock?.let { miningBlock ->
                            if (pos == miningBlock.pos
                                && (if (miningBlock.state.properties.contains(Properties.WATERLOGGED))
                                    state.fluidState.fluid.equals(Fluids.WATER)
                                    else state.isAir)) {
                                if (reBreak
                                    && player.eyePos.distanceTo(miningBlock.pos.toCenterPos()) < 6) {
                                    reBreakBlock = miningBlock
                                }
                                miningBlock.state.block.onBreak(world, miningBlock.pos, miningBlock.state, player)
                                currentMiningBlock = null
                            }
                        }
                    })
                }
            } ?: reBreakBlock?.let { reBlock ->
                if (it.packet is BlockUpdateS2CPacket
                    && it.packet.pos.equals(reBlock.pos)
                    && (if (reBlock.state.properties.contains(Properties.WATERLOGGED))
                        it.packet.state.fluidState.fluid.equals(Fluids.WATER)
                    else it.packet.state.isAir)) {
                    if (!fastReBreak) reBlock.state.block.onBreak(world, reBlock.pos, reBlock.state, player)
                } else if (it.packet is ChunkDeltaUpdateS2CPacket) {
                    it.packet.visitUpdates(BiConsumer { pos: BlockPos, state: BlockState ->
                        if (pos == reBlock.pos
                            && (if (reBlock.state.properties.contains(Properties.WATERLOGGED))
                                state.fluidState.fluid.equals(Fluids.WATER)
                                else state.isAir)) {
                            if (!fastReBreak) reBlock.state.block.onBreak(world, reBlock.pos, reBlock.state, player)
                        }
                    })
                }
            }
        }
    }

    private fun SafeContext.swapStartPacketBreak(pos: BlockPos, toolSlot: Int) {
        connection.sendPacket(UpdateSelectedSlotC2SPacket(toolSlot))
        ignorePacketSend = true
        startBreak(pos)
        abortBreak(pos)
        stopBreak(pos)
        ignorePacketSend = false
        connection.sendPacket(UpdateSelectedSlotC2SPacket(player.inventory.selectedSlot))
    }

    private fun SafeContext.swapStopBreak(pos: BlockPos, toolSlot: Int) {
        connection.sendPacket(UpdateSelectedSlotC2SPacket(toolSlot))
        ignorePacketSend = true
        stopBreak(pos)
        ignorePacketSend = false
        connection.sendPacket(UpdateSelectedSlotC2SPacket(player.inventory.selectedSlot))
    }

    private fun SafeContext.swapStartBreak(pos: BlockPos, toolSlot: Int) {
        connection.sendPacket(UpdateSelectedSlotC2SPacket(toolSlot))
        ignorePacketSend = true
        startBreak(pos)
        ignorePacketSend = false
        connection.sendPacket(UpdateSelectedSlotC2SPacket(player.inventory.selectedSlot))
    }

    private fun SafeContext.startBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP, 0))
    }

    private fun SafeContext.abortBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.UP, 0))
    }

    private fun SafeContext.stopBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP, 0))
    }

    /*
    Still unsure on how the task system works so im using these methods for now
     */

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

    private fun SafeContext.getBlockBreakingSpeed(state: BlockState, toolSlot: Int): Float {
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

        if (!player.isOnGround) {
            f /= 5.0f
        }

        return f
    }

    private class BlockData(var pos: BlockPos, var state: BlockState) {
        var mineTicks: Int = 0
        var awaitingResponse: Boolean = false
    }
}
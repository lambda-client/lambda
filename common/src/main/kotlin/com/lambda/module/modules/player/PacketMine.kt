package com.lambda.module.modules.player

import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
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
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.registry.tag.FluidTags
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object PacketMine : Module(
    name = "Packet Mine",
    description = "Mines blocks semi-automatically at a faster rate",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val page by setting("Page", Page.General)

    private val breakSpeed by setting("Break Speed", 1.0, 0.0..1.0, 0.1, "Breaks the selected block at the time to break of the block multiplied by this value", visibility = { page == Page.General})
    private val clientSideBreak by setting("Client Side Break", true, "Breaks blocks client side rather than waiting for a response from the server", visibility = { page == Page.General })
    private val timeoutDelay by setting("Timeout Delay", 0.20, 0.00..1.00, 0.1, "Will wait this amount of time (seconds) after the time to break for the block is complete before moving on", visibility = { page == Page.General && !clientSideBreak })
    private val alternativePackets by setting("Alternative Packets", false, "Uses a different set of packets which tend to work better on servers using an anti-cheat like grim", visibility = { page == Page.General })
    private val queueBlocks by setting("Queue Blocks", false, "Queues any blocks you click for breaking", visibility = { page == Page.Queue }).apply { this.onValueSet { _, to -> if (!to) blockQueue.clear() } }
    private val doubleBreakQueue by setting("Double Break Queue", false, "Queues any blocks you click for breaking", visibility = { page == Page.Queue && queueBlocks })
    private val reBreak by setting("Re-Break", true, "Automatically re-breaks the last mined block if it gets replaced", visibility = { page == Page.ReBreak})
    private val fastReBreak by setting("Fast Re-Break", false, "Re-breaks blocks instantly however could potentially cause ghost blocks", visibility = { page == Page.ReBreak && reBreak })

    private var currentMiningBlock: BreakingContext? = null
    private var ignorePacketSend = false
    private val blockQueue: ArrayDeque<BlockPos> = ArrayDeque()

    private val validActions = setOf(Action.START_DESTROY_BLOCK, Action.STOP_DESTROY_BLOCK, Action.ABORT_DESTROY_BLOCK)

    init {
        listener<TickEvent.Pre> {
            currentMiningBlock?.apply {
                mineTicks++
                val activeState = world.getBlockState(pos)
                if (activeState != state) state = activeState
                val bestTool = getBestTool(activeState, pos)

                when (breakState) {
                    BreakState.Breaking -> {
                        if (mineTicks * calcBreakDelta(state, pos, bestTool) < breakSpeed) return@listener

                        timeCompleted = System.currentTimeMillis()
                        swapStopBreak(pos, bestTool)

                        if (!clientSideBreak) {
                            breakState = BreakState.AwaitingResponse
                            return@listener
                        }

                        interaction.breakBlock(pos)

                        if (breakNextQueueBlock()) return@listener

                        if (reBreak
                            && player.eyePos.distanceTo(pos.toCenterPos()) < 6) {
                            breakState = BreakState.ReBreaking
                            return@listener
                        }

                        currentMiningBlock = null
                    }
                    BreakState.ReBreaking -> {
                        if (breakNextQueueBlock()) return@listener

                        if (player.eyePos.distanceTo(pos.toCenterPos()) > 6) {
                            currentMiningBlock = null
                            return@listener
                        }

                        if (mineTicks * calcBreakDelta(state, pos, bestTool) < breakSpeed) return@listener

                        if (!fastReBreak
                            && (activeState.isAir
                            || (!activeState.fluidState.isEmpty && !activeState.properties.contains(Properties.WATERLOGGED)))
                            ) return@listener

                        swapStopBreak(pos, bestTool)
                        if (clientSideBreak) interaction.breakBlock(pos)
                    }
                    BreakState.AwaitingResponse -> {
                        if (clientSideBreak) return@listener

                        if (System.currentTimeMillis() - timeCompleted < timeoutDelay) return@listener

                        if (breakNextQueueBlock()) return@listener

                        if (reBreak
                            && player.eyePos.distanceTo(pos.toCenterPos()) < 6) {
                            breakState = BreakState.ReBreaking
                            return@listener
                        }

                        currentMiningBlock = null
                    }
                }
            }
        }

        listener<PacketEvent.Send.Pre> {
            if (it.packet !is PlayerActionC2SPacket || !validActions.contains(it.packet.action) || ignorePacketSend) return@listener
            it.cancel()

            val packetPos = it.packet.pos

            if (queueBlocks
                && (currentMiningBlock != null
                        && currentMiningBlock?.breakState != BreakState.ReBreaking
                        && currentMiningBlock?.pos != packetPos)
                || (!blockQueue.isEmpty() && !blockQueue.contains(packetPos))
                ) {
                blockQueue.add(packetPos)
                return@listener
            }

            startBreaking(packetPos)
        }

        listener<WorldEvent.BlockUpdate> {
            currentMiningBlock?.apply {
                if (it.pos != pos
                    || !(if (state.properties.contains(Properties.WATERLOGGED)) it.state.fluidState.fluid.equals(Fluids.WATER)
                    else it.state.isAir)) return@listener
                if (!clientSideBreak) state.block.onBreak(world, pos, world.getBlockState(pos), player)

                if (breakState == BreakState.ReBreaking) return@listener

                if (breakNextQueueBlock()) return@listener

                if (reBreak
                    && player.eyePos.distanceTo(pos.toCenterPos()) < 6) {
                    breakState = BreakState.ReBreaking
                    return@listener
                }

                currentMiningBlock = null
                return@listener
            }
        }
    }

    private fun SafeContext.startBreaking(pos: BlockPos) {
        if (currentMiningBlock?.pos == pos) return

        val state = world.getBlockState(pos)

        val bestTool = getBestTool(state, pos)

        swapStartPacketBreak(pos, bestTool)

        if (calcBreakDelta(world.getBlockState(pos), pos, bestTool) > breakSpeed) {
            if (reBreak) {
                swapStartPacketBreak(pos, bestTool)
                currentMiningBlock = BreakingContext(pos, state, BreakState.ReBreaking)
            } else {
                currentMiningBlock = if (clientSideBreak) {
                    null
                } else {
                    BreakingContext(pos, state, BreakState.AwaitingResponse)
                }
            }

            if (!clientSideBreak) return

            state.block.onBreak(world, pos, state, player)

            return
        }

        currentMiningBlock = BreakingContext(pos, state, BreakState.Breaking)
    }

    private fun SafeContext.breakNextQueueBlock(): Boolean {
        if (!queueBlocks) return false

        while (true) {
            val block = blockQueue.firstOrNull() ?: run { return false }

            if (player.eyePos.distanceTo(block.toCenterPos()) > 6) {
                blockQueue.removeFirst()
                continue
            }

            break
        }

        if (blockQueue.isEmpty()) return false

        startBreaking(blockQueue.first())
        blockQueue.removeFirst()
        return true
    }

    private fun SafeContext.swapStartPacketBreak(pos: BlockPos, toolSlot: Int) {
        connection.sendPacket(UpdateSelectedSlotC2SPacket(toolSlot))
        ignorePacketSend = true
        startBreak(pos)
        if (alternativePackets) {
            abortBreak(pos)
            stopBreak(pos)
        }
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

    private fun SafeContext.startBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, pos, Direction.UP, 0))
    }

    private fun SafeContext.stopBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.UP, 0))
    }

    private fun SafeContext.abortBreak(pos: BlockPos) {
        connection.sendPacket(PlayerActionC2SPacket(Action.ABORT_DESTROY_BLOCK, pos, Direction.UP, 0))
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

    private data class BreakingContext(var pos: BlockPos, var state: BlockState, var breakState: BreakState) {
        var mineTicks = 0
        var timeCompleted: Long = -1
    }

    private enum class BreakState {
        Breaking, ReBreaking, AwaitingResponse
    }

    private enum class Page {
        General, Queue, ReBreak
    }
}
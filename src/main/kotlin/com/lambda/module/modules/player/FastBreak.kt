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

import com.lambda.context.AutomationConfig.Companion.automationConfig
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.material.ContainerSelection.Companion.selectContainer
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.ContainerManager.containerWithMaterial
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.interaction.request.breaking.BreakConfig
import com.lambda.interaction.request.breaking.BreakRequest.Companion.breakRequest
import com.lambda.interaction.request.inventory.InventoryConfig
import com.lambda.interaction.request.rotating.Rotation.Companion.rotation
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.calcItemBlockBreakingDelta
import com.lambda.util.BlockUtils.instantBreakable
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.enchantment.Enchantments
import net.minecraft.item.ItemStack
import net.minecraft.registry.tag.ItemTags.DIAMOND_TOOL_MATERIALS
import net.minecraft.registry.tag.ItemTags.GOLD_TOOL_MATERIALS
import net.minecraft.registry.tag.ItemTags.IRON_TOOL_MATERIALS
import net.minecraft.registry.tag.ItemTags.NETHERITE_TOOL_MATERIALS
import net.minecraft.registry.tag.ItemTags.STONE_TOOL_MATERIALS
import net.minecraft.registry.tag.ItemTags.WOODEN_TOOL_MATERIALS
import net.minecraft.util.hit.BlockHitResult
import java.util.concurrent.ConcurrentLinkedQueue

object FastBreak : Module(
    name = "FastBreak",
    description = "Break blocks faster.",
    tag = ModuleTag.PLAYER,
) {
    override val breakConfig = object : BreakConfig by super.breakConfig {
        override val rotateForBreak = false
        override val doubleBreak = false
        override val breaksPerTick = 1
    }
    override val inventoryConfig = object : InventoryConfig by super.inventoryConfig {
        override val accessShulkerBoxes = false
        override val accessEnderChest = false
        override val accessChests = false
        override val accessStashes = false
    }

    private val pendingInteractions = ConcurrentLinkedQueue<BuildContext>()

    init {
        defaultConfig = automationConfig {
            breakConfig.apply {
                editTyped(
                    ::avoidLiquids,
                    ::avoidSupporting,
                    ::efficientOnly,
                    ::suitableToolsOnly
                ) { defaultValue(false) }
                hide(
                    ::rotateForBreak,
                    ::doubleBreak,
                    ::breaksPerTick
                )
            }
            inventoryConfig.apply {
                hide(
                    ::accessShulkerBoxes,
                    ::accessEnderChest,
                    ::accessChests,
                    ::accessStashes
                )
            }
        }

        listen<PlayerEvent.Attack.Block> { it.cancel() }
        listen<PlayerEvent.Breaking.Update> { event ->
            event.cancel()

            val hitResult = mc.crosshairTarget as? BlockHitResult ?: return@listen
            val pos = event.pos
            val state = blockState(pos)

            //ToDo: Copied this swap logic from the build sim. Needs reworking when we rework the build sim. Probably need to
            // adjust the build sim to accept partial simulations. For example, ignoring hit scanning in this situation
            val silentSwapSelection = selectContainer {
                ofAnyType(MaterialContainer.Rank.Hotbar)
            }

            val stackSelection = selectStack(
                sorter = compareByDescending<ItemStack> {
                    it.canBreak(CachedBlockPosition(world, pos, false))
                }.thenByDescending {
                    state.calcItemBlockBreakingDelta(pos, it)
                }
            ) {
                isTool() and if (breakConfig.suitableToolsOnly) {
                    isSuitableForBreaking(state)
                } else any() and if (breakConfig.forceSilkTouch) {
                    hasEnchantment(Enchantments.SILK_TOUCH)
                } else any() and if (breakConfig.forceFortunePickaxe) {
                    hasEnchantment(Enchantments.FORTUNE)
                } else any() and if (!breakConfig.useWoodenTools) {
                    hasTag(WOODEN_TOOL_MATERIALS).not()
                } else any() and if (!breakConfig.useStoneTools) {
                    hasTag(STONE_TOOL_MATERIALS).not()
                } else any() and if (!breakConfig.useIronTools) {
                    hasTag(IRON_TOOL_MATERIALS).not()
                } else any() and if (!breakConfig.useDiamondTools) {
                    hasTag(DIAMOND_TOOL_MATERIALS).not()
                } else any() and if (!breakConfig.useGoldTools) {
                    hasTag(GOLD_TOOL_MATERIALS).not()
                } else any() and if (!breakConfig.useNetheriteTools) {
                    hasTag(NETHERITE_TOOL_MATERIALS).not()
                } else any()
            }

            val swapCandidates = stackSelection.containerWithMaterial(silentSwapSelection)
            if (swapCandidates.isEmpty()) return@listen

            val swapStack = swapCandidates
                .map { it.matchingStacks(stackSelection) }
                .asSequence()
                .flatten()
                .let { containerStacks ->
                    var bestStack = ItemStack.EMPTY
                    var bestBreakDelta = -1f
                    containerStacks.forEach { stack ->
                        val breakDelta = state.calcItemBlockBreakingDelta(pos, stack)
                        if (breakDelta > bestBreakDelta ||
                            (stack == player.mainHandStack && breakDelta >= bestBreakDelta)
                        ) {
                            bestBreakDelta = breakDelta
                            bestStack = stack
                        }
                    }
                    bestStack
                }

            val breakContext = BreakContext(
                hitResult,
                RotationRequest(lookAt(player.rotation), this@FastBreak),
                player.hotbar.indexOf(swapStack),
                stackSelection,
                instantBreakable(
                    state,
                    pos,
                    if (breakConfig.swapMode.isEnabled()) swapStack
                    else player.mainHandStack,
                    breakConfig.breakThreshold
                ),
                state.getOutlineShape(world, pos).boundingBoxes.any { it.contains(player.eyePos) },
                state,
                this@FastBreak
            )

            breakRequest(setOf(breakContext), pendingInteractions).submit()
        }
    }
}

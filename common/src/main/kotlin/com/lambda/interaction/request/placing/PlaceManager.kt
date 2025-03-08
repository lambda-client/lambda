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

package com.lambda.interaction.request.placing

import com.lambda.config.groups.BuildConfig
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakManager
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.rotation.RotationManager.onRotate
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.player.swingHand
import net.minecraft.block.BlockState
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.SoundCategory
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameMode
import org.apache.commons.lang3.mutable.MutableObject

object PlaceManager : RequestHandler<PlaceRequest>() {
    private val pendingInteractions = LimitedDecayQueue<PlaceInfo>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) { info("${it::class.simpleName} at ${it.context.expectedPos.toShortString()} timed out") }

    init {
        listen<TickEvent.Pre>(Int.MIN_VALUE) {
            preEvent()

            if (!updateRequest { true }) {
                postEvent()
                return@listen
            }

            if (BreakManager.activeThisTick()) {
                postEvent()
                return@listen
            }

            currentRequest?.let request@ { request ->
                if (pendingInteractions.size >= request.buildConfig.placeSettings.maxPendingPlacements)
                    return@request

                activeThisTick = true

                if (request.placeContext.sneak && !player.isSneaking
                    || (request.buildConfig.placeSettings.rotateForPlace && !request.placeContext.rotation.done)
                    || (!request.hotbarConfig.request(HotbarRequest(request.placeContext.hotbarIndex)).done)
                    ) {
                    postEvent()
                    return@listen
                }
                pendingInteractions.setMaxSize(request.buildConfig.maxPendingInteractions)
                pendingInteractions.setDecayTime(request.buildConfig.interactionTimeout * 50L)
                placeBlock(request, Hand.MAIN_HAND)
            }

            postEvent()
        }

        listen<WorldEvent.BlockUpdate.Server> { event ->
            pendingInteractions
                .firstOrNull { it.context.expectedPos == event.pos }
                ?.let { pending ->
                    pendingInteractions.remove(pending)
                    if (!matchesTargetState(event.pos, pending.context.targetState, event.newState)) return@listen
                    if (pending.buildConfig.placeSettings.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.AwaitThenPlace)
                        placeSound(pending.item, pending.context.expectedState, pending.context.expectedPos)
                    pending.onPlace()
                }
        }

        onRotate {
            currentRequest?.let { request ->
                if (request.buildConfig.placeSettings.rotateForPlace) {
                    request.rotationConfig.request(request.placeContext.rotation)
                }
            }
        }

        listen<MovementEvent.InputUpdate> {
            if (currentRequest?.placeContext?.sneak == true) it.input.sneaking = true
        }
    }

    private fun SafeContext.matchesTargetState(pos: BlockPos, targetState: TargetState, newState: BlockState) =
        if (targetState.matches(newState, pos, world)) true
        else {
            this@PlaceManager.warn("Place at ${pos.toShortString()} was rejected with $newState instead of $targetState")
            false
        }

    private fun SafeContext.placeBlock(request: PlaceRequest, hand: Hand) {
        val stackInHand = player.getStackInHand(hand)
        val item = stackInHand.item as? BlockItem ?: return
        val stackCountPre = stackInHand.count
        val actionResult = interactBlock(request.buildConfig.placeSettings, hand, request.placeContext.result)

        if (actionResult.isAccepted) {
            if (request.buildConfig.placeSettings.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.None)
                request.onPlace()
            else {
                pendingInteractions.add(
                    PlaceInfo(
                        request.placeContext,
                        item,
                        request.buildConfig,
                        request.onPlace
                    )
                )
            }

            if (actionResult.shouldSwingHand() && request.buildConfig.placeSettings.swing) {
                swingHand(request.buildConfig.placeSettings.swingType)
            }

            if (!stackInHand.isEmpty && (stackInHand.count != stackCountPre || interaction.hasCreativeInventory())) {
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
            }
        } else {
            warn("Placement interaction failed with $actionResult")
        }
    }

    private fun SafeContext.interactBlock(placeConfig: PlaceConfig, hand: Hand, hitResult: BlockHitResult): ActionResult {
        interaction.syncSelectedSlot()
        if (!world.worldBorder.contains(hitResult.blockPos)) {
            return ActionResult.FAIL
        } else {
            val mutableActionResult = MutableObject<ActionResult>()
            interaction.sendSequencedPacket(world) { sequence: Int ->
                mutableActionResult.value = interactBlockInternal(placeConfig, hand, hitResult)
                PlayerInteractBlockC2SPacket(hand, hitResult, sequence)
            }
            return mutableActionResult.value
        }
    }

    private fun SafeContext.interactBlockInternal(
        placeConfig: PlaceConfig,
        hand: Hand,
        hitResult: BlockHitResult
    ): ActionResult {
        val itemStack = player.getStackInHand(hand)
        if (interaction.currentGameMode == GameMode.SPECTATOR) return ActionResult.SUCCESS

        val handNotEmpty = !player.getStackInHand(hand).isEmpty
        val cantInteract = player.shouldCancelInteraction() && handNotEmpty
        if (!cantInteract) return ActionResult.PASS

        if (!itemStack.isEmpty && !player.itemCooldownManager.isCoolingDown(itemStack.item)) {
            val itemUsageContext = ItemUsageContext(player, hand, hitResult)
            val itemUseResult: ActionResult
            if (interaction.currentGameMode.isCreative) {
                val i = itemStack.count
                itemUseResult = useOnBlock(placeConfig, itemStack, itemUsageContext)
                itemStack.count = i
            } else
                itemUseResult = useOnBlock(placeConfig, itemStack, itemUsageContext)

            return itemUseResult
        }
        return ActionResult.PASS
    }

    private fun SafeContext.useOnBlock(
        placeConfig: PlaceConfig,
        itemStack: ItemStack,
        context: ItemUsageContext
    ): ActionResult {
        val blockPos = context.blockPos
        val cachedBlockPosition = CachedBlockPosition(context.world, blockPos, false)
        if (!player.abilities.allowModifyWorld
            && !itemStack.canPlaceOn(context.world.registryManager.get(RegistryKeys.BLOCK), cachedBlockPosition)
            ) {
            return ActionResult.PASS
        }
        val item = (itemStack.item as? BlockItem) ?: return ActionResult.PASS
        val actionResult = place(placeConfig, item, ItemPlacementContext(context))

        return actionResult
    }

    private fun SafeContext.place(
        placeConfig: PlaceConfig,
        item: BlockItem,
        context: ItemPlacementContext
    ): ActionResult {
        if (!item.block.isEnabled(world.enabledFeatures)) return ActionResult.FAIL
        if (!context.canPlace()) return ActionResult.FAIL

        val itemPlacementContext = item.getPlacementContext(context) ?: return ActionResult.FAIL
        val blockState = item.getPlacementState(itemPlacementContext) ?: return ActionResult.FAIL

        if (placeConfig.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.AwaitThenPlace)
            return ActionResult.success(world.isClient)

        // TODO: Implement restriction checks (e.g., world height) to prevent unnecessary server requests when the
        //  "AwaitThenPlace" confirmation setting is enabled, as the block state setting methods that validate these
        //  rules are not called.
        if (!item.place(itemPlacementContext, blockState)) return ActionResult.FAIL

        val blockPos = itemPlacementContext.blockPos
        val itemStack = itemPlacementContext.stack
        var hitState = world.getBlockState(blockPos)
        if (hitState.isOf(blockState.block)) {
            hitState = item.placeFromNbt(blockPos, world, itemStack, hitState)
            item.postPlacement(blockPos, world, player, itemStack, hitState)
            hitState.block.onPlaced(world, blockPos, hitState, player, itemStack)
        }

        if (placeConfig.sounds) placeSound(item, hitState, blockPos)
        if (!player.abilities.creativeMode) itemStack.decrement(1)

        return ActionResult.success(world.isClient)
    }

    private fun SafeContext.placeSound(item: BlockItem, state: BlockState, pos: BlockPos) {
        val blockSoundGroup = state.soundGroup
        world.playSound(
            player,
            pos,
            item.getPlaceSound(state),
            SoundCategory.BLOCKS,
            (blockSoundGroup.getVolume() + 1.0f) / 2.0f,
            blockSoundGroup.getPitch() * 0.8f
        )
    }

    data class PlaceInfo(
        val context: PlaceContext,
        val item: BlockItem,
        val buildConfig: BuildConfig,
        val onPlace: () -> Unit
    )

    override fun preEvent() = UpdateManagerEvent.Place.Pre().post()
    override fun postEvent() = UpdateManagerEvent.Place.Post().post()
}
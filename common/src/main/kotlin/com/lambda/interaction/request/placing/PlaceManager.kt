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

import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.request.RequestHandler
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
import net.minecraft.stat.Stats
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.world.GameMode
import net.minecraft.world.event.GameEvent
import org.apache.commons.lang3.mutable.MutableObject

object PlaceManager : RequestHandler<PlaceRequest>() {
    private val pendingInteractions = LimitedDecayQueue<PlaceContext>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) { info("${it::class.simpleName} at ${it.expectedPos.toShortString()} timed out") }

    //ToDo: Add server response check for pending interactions
    init {
        listen<TickEvent.Pre>(Int.MIN_VALUE) {
            preEvent()

            if (!updateRequest { true }) {
                postEvent()
                return@listen
            }

            currentRequest?.let request@ { request ->
                if (pendingInteractions.size >= request.buildConfig.placeSettings.maxPendingPlacements) {
                    return@request
                }

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

        onRotate {
            currentRequest?.let { request ->
                if (request.buildConfig.placeSettings.rotateForPlace)
                    request.rotationConfig.request(request.placeContext.rotation)
            }
        }

        listen<MovementEvent.InputUpdate> {
            if (currentRequest?.placeContext?.sneak == true) it.input.sneaking = true
        }
    }

    private fun SafeContext.placeBlock(request: PlaceRequest, hand: Hand) {
        val stackInHand = player.getStackInHand(hand)
        val stackCountPre = stackInHand.count
        val actionResult = interactBlock(request.buildConfig.placeSettings, hand, request.placeContext.result)

        if (actionResult.isAccepted) {
            if (request.buildConfig.placeSettings.placeConfirmation != PlaceConfig.PlaceConfirmation.None)
                pendingInteractions.add(request.placeContext)

            if (actionResult.shouldSwingHand() && request.buildConfig.placeSettings.swing)
                swingHand(request.buildConfig.placeSettings.swingType)

            if (!stackInHand.isEmpty && (stackInHand.count != stackCountPre || interaction.hasCreativeInventory()))
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
        } else {
            warn("Placement interaction failed with $actionResult")
        }
        request.onPlace()
    }

    private fun SafeContext.interactBlock(placeConfig: PlaceConfig, hand: Hand, hitResult: BlockHitResult): ActionResult {
        interaction.syncSelectedSlot()
        if (!world.worldBorder.contains(hitResult.blockPos)) {
            return ActionResult.FAIL
        } else {
            val mutableObject = MutableObject<ActionResult>()
            interaction.sendSequencedPacket(world) { sequence: Int ->
                mutableObject.value = interactBlockInternal(placeConfig, hand, hitResult)
                PlayerInteractBlockC2SPacket(hand, hitResult, sequence)
            }
            return mutableObject.value
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
        val item: BlockItem = (itemStack.item as? BlockItem) ?: return ActionResult.PASS
        val actionResult = useOnBlock(placeConfig, item, context)
        if (actionResult.shouldIncrementStat()) player.incrementStat(Stats.USED.getOrCreateStat(item))

        return actionResult
    }

    private fun SafeContext.useOnBlock(
        placeConfig: PlaceConfig,
        item: BlockItem,
        context: ItemUsageContext
    ) = place(placeConfig, item, ItemPlacementContext(context))

    private fun SafeContext.place(
        placeConfig: PlaceConfig,
        item: BlockItem,
        context: ItemPlacementContext
    ): ActionResult {
        if (!item.block.isEnabled(world.enabledFeatures)) return ActionResult.FAIL
        if (!context.canPlace()) return ActionResult.FAIL

        val itemPlacementContext: ItemPlacementContext = item.getPlacementContext(context) ?: return ActionResult.FAIL
        val blockState: BlockState = item.getPlacementState(itemPlacementContext) ?: return ActionResult.FAIL

        if (placeConfig.placeConfirmation == PlaceConfig.PlaceConfirmation.AwaitThenPlace)
            return ActionResult.success(world.isClient)

        //ToDo: Add restriction checks, like world height, to avoid needlessly awaiting a server response which will never return
        // if the user has the AwaitThenPlace confirmation setting enabled, as none of the state-setting methods which check these rules
        // are called
        if (!item.place(itemPlacementContext, blockState)) return ActionResult.FAIL

        val blockPos = itemPlacementContext.blockPos
        val itemStack = itemPlacementContext.stack
        var hitState = world.getBlockState(blockPos)
        if (hitState.isOf(blockState.block)) {
            hitState = item.placeFromNbt(blockPos, world, itemStack, hitState)
            item.postPlacement(blockPos, world, player, itemStack, hitState)
            hitState.block.onPlaced(world, blockPos, hitState, player, itemStack)
        }

        if (placeConfig.sounds) {
            val blockSoundGroup = hitState.soundGroup
            world.playSound(
                player,
                blockPos,
                item.getPlaceSound(hitState),
                SoundCategory.BLOCKS,
                (blockSoundGroup.getVolume() + 1.0f) / 2.0f,
                blockSoundGroup.getPitch() * 0.8f
            )
        }
        world.emitGameEvent(GameEvent.BLOCK_PLACE, blockPos, GameEvent.Emitter.of(player, hitState))
        if (!player.abilities.creativeMode) itemStack.decrement(1)

        return ActionResult.success(world.isClient)
    }

    override fun preEvent() = UpdateManagerEvent.Place.Pre().post()
    override fun postEvent() = UpdateManagerEvent.Place.Post().post()
}
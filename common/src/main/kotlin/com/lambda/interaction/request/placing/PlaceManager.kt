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

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.PositionBlocking
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakManager
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.rotation.RotationManager.onRotate
import com.lambda.interaction.request.rotation.RotationManager.onRotatePost
import com.lambda.interaction.request.rotation.RotationRequest
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.item
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.player.gamemode
import com.lambda.util.player.isItemOnCooldown
import com.lambda.util.player.swingHand
import net.minecraft.block.BlockState
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.SoundCategory
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.GameMode

object PlaceManager : RequestHandler<PlaceRequest>(), PositionBlocking {
    private val pendingPlacements = LimitedDecayQueue<PlaceRequest>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) {
        info("${it::class.simpleName} at ${it.placeContext.expectedPos.toShortString()} timed out")
        mc.world?.setBlockState(it.placeContext.expectedPos, it.placeContext.checkedState)
        it.pendingInteractionsList.remove(it.placeContext)
    }

    override val blockedPositions
        get() = pendingPlacements.map { it.placeContext.expectedPos }

    private var rotation: RotationRequest? = null
    private var validRotation = false

    fun Any.onPlace(
        alwaysListen: Boolean = false,
        priority: Priority = 0,
        block: SafeContext.() -> Unit
    ) = this.listen<UpdateManagerEvent.Place.Pre>(priority, alwaysListen) {
        block()
    }

    fun Any.onPlacePost(
        alwaysListen: Boolean = false,
        priority: Priority = 0,
        block: SafeContext.() -> Unit
    ) = this.listen<UpdateManagerEvent.Place.Post>(priority, alwaysListen) {
        block()
    }

    init {
        listen<TickEvent.Pre>(priority = Int.MIN_VALUE) {
            currentRequest?.let { request ->
                val notSneaking = !player.isSneaking
                val hotbarRequest = request.hotbarConfig.request(HotbarRequest(request.placeContext.hotbarIndex))
                val invalidRotation = request.buildConfig.placeSettings.rotateForPlace && !validRotation
                if ((request.placeContext.sneak && notSneaking) || !hotbarRequest.done || invalidRotation)
                    return@listen

                val actionResult = placeBlock(request, Hand.MAIN_HAND)
                if (!actionResult.isAccepted) {
                    warn("Placement interaction failed with $actionResult")
                }
                activeThisTick = true
            }
        }

        onRotate(priority = Int.MIN_VALUE) {
            preEvent()

            if (!updateRequest { request -> canPlace(request.value.placeContext) }) {
                postEvent()
                return@onRotate
            }

            if (BreakManager.activeThisTick()) {
                postEvent()
                return@onRotate
            }

            currentRequest?.let request@ { request ->
                if (pendingPlacements.size >= request.buildConfig.placeSettings.maxPendingPlacements) {
                    postEvent()
                    return@onRotate
                }

                rotation = if (request.buildConfig.placeSettings.rotateForPlace)
                    request.rotationConfig.request(request.placeContext.rotation)
                else null

                pendingPlacements.setMaxSize(request.buildConfig.placeSettings.maxPendingPlacements)
                pendingPlacements.setDecayTime(request.buildConfig.interactionTimeout * 50L)
            }
        }

        onRotatePost(priority = Int.MIN_VALUE) {
            validRotation = rotation?.done ?: true
            postEvent()
        }

        listen<MovementEvent.InputUpdate> {
            if (currentRequest?.placeContext?.sneak == true) it.input.sneaking = true
        }

        listen<WorldEvent.BlockUpdate.Server> { event ->
            pendingPlacements
                .firstOrNull { it.placeContext.expectedPos == event.pos }
                ?.let { request ->
                    removePendingPlace(request)

                    // return if the block wasn't placed
                    if (!matchesTargetState(event.pos, request.placeContext.targetState, event.newState))
                        return@listen

                    if (request.buildConfig.placeSettings.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.AwaitThenPlace)
                        with (request.placeContext) {
                            placeSound(expectedState.block.item as BlockItem, expectedState, expectedPos)
                        }
                    request.onPlace()
                    return@listen
                }
        }
    }

    private fun canPlace(placeContext: PlaceContext) =
        pendingPlacements.none { pending ->
            pending.placeContext.expectedPos == placeContext.expectedPos
        }

    private fun SafeContext.matchesTargetState(pos: BlockPos, targetState: TargetState, newState: BlockState) =
        if (targetState.matches(newState, pos, world)) true
        else {
            this@PlaceManager.warn("Place at ${pos.toShortString()} was rejected with $newState instead of $targetState")
            false
        }

    private fun SafeContext.placeBlock(request: PlaceRequest, hand: Hand): ActionResult {
        interaction.syncSelectedSlot()
        val hitResult = request.placeContext.result
        if (!world.worldBorder.contains(hitResult.blockPos)) return ActionResult.FAIL
        if (gamemode == GameMode.SPECTATOR) return ActionResult.PASS
        return interactBlockInternal(request, request.buildConfig.placeSettings, hand, hitResult)
    }

    private fun SafeContext.interactBlockInternal(
        request: PlaceRequest,
        placeConfig: PlaceConfig,
        hand: Hand,
        hitResult: BlockHitResult
    ): ActionResult {
        val handNotEmpty = player.getStackInHand(hand).isEmpty.not()
        val cantInteract = player.shouldCancelInteraction() && handNotEmpty
        if (!cantInteract) {
            val blockState = blockState(hitResult.blockPos)
            if (!connection.hasFeature(blockState.block.requiredFeatures)) {
                return ActionResult.FAIL
            }

            val actionResult = blockState.onUse(world, player, hand, hitResult)
            if (actionResult.isAccepted) {
                return actionResult
            }
        }

        val itemStack = player.getStackInHand(hand)

        if (!itemStack.isEmpty && !isItemOnCooldown(itemStack.item)) {
            val itemUsageContext = ItemUsageContext(player, hand, hitResult)
            return if (gamemode.isCreative) {
                val i = itemStack.count
                useOnBlock(request, hand, hitResult, placeConfig, itemStack, itemUsageContext)
                    .also {
                        itemStack.count = i
                    }
            } else
                useOnBlock(request, hand, hitResult, placeConfig, itemStack, itemUsageContext)
        }
        return ActionResult.PASS
    }

    private fun SafeContext.useOnBlock(
        request: PlaceRequest,
        hand: Hand,
        hitResult: BlockHitResult,
        placeConfig: PlaceConfig,
        itemStack: ItemStack,
        context: ItemUsageContext
    ): ActionResult {
        val cachedBlockPosition = CachedBlockPosition(world, context.blockPos, false)

        val cantModifyWorld = !player.abilities.allowModifyWorld
        val cantPlaceOn = !itemStack.canPlaceOn(context.world.registryManager.get(RegistryKeys.BLOCK), cachedBlockPosition)
        if (cantModifyWorld && cantPlaceOn) return ActionResult.PASS

        val item = (itemStack.item as? BlockItem) ?: return ActionResult.PASS

        return place(request, hand, hitResult, placeConfig, item, ItemPlacementContext(context))
    }

    private fun SafeContext.place(
        request: PlaceRequest,
        hand: Hand,
        hitResult: BlockHitResult,
        placeConfig: PlaceConfig,
        item: BlockItem,
        context: ItemPlacementContext
    ): ActionResult {
        if (!item.block.isEnabled(world.enabledFeatures)) return ActionResult.FAIL
        if (!context.canPlace()) return ActionResult.FAIL

        val itemPlacementContext = item.getPlacementContext(context) ?: return ActionResult.FAIL
        val blockState = item.getPlacementState(itemPlacementContext) ?: return ActionResult.FAIL

        val stackInHand = player.getStackInHand(hand)
        val stackCountPre = stackInHand.count
        if (placeConfig.placeConfirmationMode != PlaceConfig.PlaceConfirmationMode.None) {
            addPendingPlace(request)
        }

        if (request.buildConfig.placeSettings.airPlace == PlaceConfig.AirPlaceMode.Grim) {
            airPlaceOffhandSwap()
            sendPlacePacket(hand, hitResult)
            airPlaceOffhandSwap()
        } else {
            sendPlacePacket(hand, hitResult)
        }

        if (request.buildConfig.placeSettings.swing) {
            swingHand(request.buildConfig.placeSettings.swingType, hand)
        }

        if (!stackInHand.isEmpty && (stackInHand.count != stackCountPre || interaction.hasCreativeInventory())) {
            mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
        }

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

        if (placeConfig.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.None) {
            request.onPlace()
        }

        return ActionResult.success(world.isClient)
    }

    private fun SafeContext.sendPlacePacket(hand: Hand, hitResult: BlockHitResult) =
        interaction.sendSequencedPacket(world) { sequence: Int ->
            PlayerInteractBlockC2SPacket(hand, hitResult, sequence)
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

    private fun SafeContext.airPlaceOffhandSwap() {
        connection.sendPacket(
            PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN,
                Direction.DOWN
            )
        )
    }

    private fun addPendingPlace(request: PlaceRequest) {
        pendingPlacements.add(request)
        request.pendingInteractionsList.add(request.placeContext)
    }

    private fun removePendingPlace(request: PlaceRequest) {
        pendingPlacements.remove(request)
        request.pendingInteractionsList.remove(request.placeContext)
    }

    override fun preEvent() = UpdateManagerEvent.Place.Pre().post()
    override fun postEvent() = UpdateManagerEvent.Place.Post().post()
}
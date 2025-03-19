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
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.SoundCategory
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameMode

object PlaceManager : RequestHandler<PlaceRequest>(), PositionBlocking {
    private val pendingPlacements = LimitedDecayQueue<PlaceRequest>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) {
        info("${it::class.simpleName} at ${it.placeContext.expectedPos.toShortString()} timed out")
        mc.world?.setBlockState(it.placeContext.expectedPos, it.placeContext.checkedState)
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

                placeBlock(request, Hand.MAIN_HAND)
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

                pendingPlacements.setMaxSize(request.buildConfig.maxPendingInteractions)
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
                    pendingPlacements.remove(request)

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

    private fun SafeContext.placeBlock(request: PlaceRequest, hand: Hand) =
        interactBlock(request, request.buildConfig.placeSettings, hand, request.placeContext.result)

    private fun SafeContext.interactBlock(request: PlaceRequest, placeConfig: PlaceConfig, hand: Hand, hitResult: BlockHitResult) {
        interaction.syncSelectedSlot()
        if (!world.worldBorder.contains(hitResult.blockPos)) return

        interaction.sendSequencedPacket(world) { sequence: Int ->
            val stackInHand = player.getStackInHand(hand)
            val stackCountPre = stackInHand.count
            val actionResult = interactBlockInternal(placeConfig, hand, hitResult)
            if (actionResult.isAccepted) {
                if (request.buildConfig.placeSettings.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.None)
                    request.onPlace()
                else
                    pendingPlacements.add(request)

                if (actionResult.shouldSwingHand() && request.buildConfig.placeSettings.swing) {
                    swingHand(request.buildConfig.placeSettings.swingType)
                }

                if (!stackInHand.isEmpty && (stackInHand.count != stackCountPre || interaction.hasCreativeInventory())) {
                    mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
                }
            } else {
                warn("Placement interaction failed with $actionResult")
            }
            PlayerInteractBlockC2SPacket(hand, hitResult, sequence)
        }
    }

    private fun SafeContext.interactBlockInternal(
        placeConfig: PlaceConfig,
        hand: Hand,
        hitResult: BlockHitResult
    ): ActionResult {
        val itemStack = player.getStackInHand(hand)
        if (gamemode == GameMode.SPECTATOR) return ActionResult.PASS

        // checks if the player should be able to interact with the block for if its something
        // like a furnace or chest where an action would happen
//        val handNotEmpty = player.getStackInHand(hand).isEmpty.not()
//        val cantInteract = player.shouldCancelInteraction() && handNotEmpty
//        if (!cantInteract) return ActionResult.PASS

        if (!itemStack.isEmpty && !isItemOnCooldown(itemStack.item)) {
            val itemUsageContext = ItemUsageContext(player, hand, hitResult)
            return if (gamemode.isCreative) {
                val i = itemStack.count
                useOnBlock(placeConfig, itemStack, itemUsageContext)
                    .also {
                        itemStack.count = i
                    }
            } else
                useOnBlock(placeConfig, itemStack, itemUsageContext)
        }
        return ActionResult.PASS
    }

    private fun SafeContext.useOnBlock(
        placeConfig: PlaceConfig,
        itemStack: ItemStack,
        context: ItemUsageContext
    ): ActionResult {
        val cachedBlockPosition = CachedBlockPosition(world, context.blockPos, false)

        val cantModifyWorld = !player.abilities.allowModifyWorld
        val cantPlaceOn = !itemStack.canPlaceOn(context.world.registryManager.get(RegistryKeys.BLOCK), cachedBlockPosition)
        if (cantModifyWorld && cantPlaceOn) return ActionResult.PASS

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

    override fun preEvent() = UpdateManagerEvent.Place.Pre().post()
    override fun postEvent() = UpdateManagerEvent.Place.Post().post()
}
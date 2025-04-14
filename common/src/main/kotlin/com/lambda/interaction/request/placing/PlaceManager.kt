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
import com.lambda.config.groups.TickStage
import com.lambda.context.SafeContext
import com.lambda.event.Event
import com.lambda.event.EventFlow.post
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.PositionBlocking
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.breaking.BreakManager
import com.lambda.interaction.request.hotbar.HotbarManager
import com.lambda.interaction.request.placing.PlaceManager.activeRequest
import com.lambda.interaction.request.placing.PlaceManager.preEvent
import com.lambda.interaction.request.placing.PlaceManager.processRequest
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
import net.minecraft.client.network.ClientPlayerEntity
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

object PlaceManager : RequestHandler<PlaceRequest>(
    TickStage.TickStart,
    TickStage.PostMovement,
    preOpen =  { activeRequest?.let { processRequest(it) } },
    onOpen = { preEvent() }
), PositionBlocking {
    private val pendingPlacements = LimitedDecayQueue<PlaceInfo>(
        TaskFlowModule.build.maxPendingInteractions, TaskFlowModule.build.interactionTimeout * 50L
    ) {
        info("${it::class.simpleName} at ${it.context.expectedPos.toShortString()} timed out")
        mc.world?.setBlockState(it.context.expectedPos, it.context.checkedState)
        it.pendingInteractionsList.remove(it.context)
    }

    private var activeRequest: PlaceRequest? = null

    private var placementsThisTick = 0
    private var maxPlacementsThisTick = 0

    private var potentialPlacements = mutableListOf<PlaceContext>()

    private var shouldSneak = false
    private val validSneak: (player: ClientPlayerEntity) -> Boolean =
        { player -> shouldSneak == player.isSneaking }

    override val blockedPositions
        get() = pendingPlacements.map { it.context.expectedPos }

    fun Any.onPlace(
        alwaysListen: Boolean = false,
        priority: Priority = 0,
        block: SafeContext.() -> Unit
    ) = this.listen<UpdateManagerEvent.Place>(priority, alwaysListen) {
        block()
    }

    init {
        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            activeRequest = null
            placementsThisTick = 0
        }

        listen<MovementEvent.InputUpdate>(priority = Int.MIN_VALUE) {
            if (shouldSneak) {
                shouldSneak = false
                it.input.sneaking = true
            }
        }

        listen<WorldEvent.BlockUpdate.Server>(priority = Int.MIN_VALUE) { event ->
            pendingPlacements
                .firstOrNull { it.context.expectedPos == event.pos }
                ?.let { info ->
                    removePendingPlace(info)

                    // return if the block wasn't placed
                    if (!matchesTargetState(event.pos, info.context.targetState, event.newState))
                        return@listen

                    if (info.placeConfig.placeConfirmationMode == PlaceConfig.PlaceConfirmationMode.AwaitThenPlace)
                        with (info.context) {
                            placeSound(expectedState.block.item as BlockItem, expectedState, expectedPos)
                        }
                    info.onPlace()
                    return@listen
                }
        }

        listenUnsafe<ConnectionEvent.Connect.Pre> {
            pendingPlacements.clear()
        }
    }

    override fun SafeContext.handleRequest(request: PlaceRequest) {
        if (activeRequest != null || BreakManager.activeThisTick) return

        activeRequest = request
        processRequest(request)
        if (placementsThisTick > 0) activeThisTick = true
    }

    fun SafeContext.processRequest(request: PlaceRequest) {
        pendingPlacements.cleanUp()

        if (request.fresh) populateFrom(request)

        val iterator = potentialPlacements.iterator()
        while (iterator.hasNext()) {
            if (placementsThisTick + 1 > maxPlacementsThisTick) break
            val ctx = iterator.next()

            if (ctx.sneak) shouldSneak = true
            if (!ctx.requestDependencies(request) || !validSneak(player)) return
            if (tickStage !in request.build.placing.placeStageMask) return

            val actionResult = placeBlock(ctx, request, Hand.MAIN_HAND)
            if (!actionResult.isAccepted) warn("Placement interaction failed with $actionResult")
            placementsThisTick++
            iterator.remove()
        }
        if (potentialPlacements.isEmpty()) {
            activeRequest = null
            return
        }
        if (!request.rotation.rotate) return

        // In case you cant rotate and place within the same tick
        potentialPlacements.getOrNull(maxPlacementsThisTick)?.let { nextPredictedPlacement ->
            request.rotation.request(nextPredictedPlacement.rotation)
        }
    }

    private fun SafeContext.populateFrom(request: PlaceRequest) {
        val place = request.build.placing

        pendingPlacements.setSizeLimit(place.maxPendingPlacements)
        pendingPlacements.setDecayTime(request.build.interactionTimeout * 50L)

        potentialPlacements = request.contexts
            .filter { canPlace(it) }
            .sortedWith(
                compareByDescending<PlaceContext> { it.hotbarIndex == HotbarManager.serverSlot }
                    .thenByDescending { it.sneak == player.isSneaking }
            ).toMutableList()

        val pendingLimit =  (place.maxPendingPlacements - pendingPlacements.size).coerceAtLeast(0)
        maxPlacementsThisTick = (place.placementsPerTick.coerceAtMost(pendingLimit))
    }

    private fun canPlace(placeContext: PlaceContext) =
        pendingPlacements.none { pending ->
            pending.context.expectedPos == placeContext.expectedPos
        }

    private fun SafeContext.matchesTargetState(pos: BlockPos, targetState: TargetState, newState: BlockState) =
        if (targetState.matches(newState, pos, world)) true
        else {
            this@PlaceManager.warn("Place at ${pos.toShortString()} was rejected with $newState instead of $targetState")
            false
        }

    private fun SafeContext.placeBlock(placeContext: PlaceContext, request: PlaceRequest, hand: Hand): ActionResult {
        interaction.syncSelectedSlot()
        val hitResult = placeContext.result
        if (!world.worldBorder.contains(hitResult.blockPos)) return ActionResult.FAIL
        if (gamemode == GameMode.SPECTATOR) return ActionResult.PASS
        return interactBlockInternal(placeContext, request, request.build.placing, hand, hitResult)
    }

    private fun SafeContext.interactBlockInternal(
        placeContext: PlaceContext,
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
                useOnBlock(placeContext, request, hand, hitResult, placeConfig, itemStack, itemUsageContext)
                    .also {
                        itemStack.count = i
                    }
            } else
                useOnBlock(placeContext, request, hand, hitResult, placeConfig, itemStack, itemUsageContext)
        }
        return ActionResult.PASS
    }

    private fun SafeContext.useOnBlock(
        placeContext: PlaceContext,
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

        return place(placeContext, request, hand, hitResult, placeConfig, item, ItemPlacementContext(context))
    }

    private fun SafeContext.place(
        placeContext: PlaceContext,
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
            addPendingPlace(
                PlaceInfo(placeContext, request.onPlace, request.pendingInteractions, placeConfig)
            )
        }

        if (placeConfig.airPlace == PlaceConfig.AirPlaceMode.Grim) {
            val placeHand = if (hand == Hand.MAIN_HAND) Hand.OFF_HAND else Hand.MAIN_HAND
            airPlaceOffhandSwap()
            sendPlacePacket(placeHand, hitResult)
            airPlaceOffhandSwap()
        } else {
            sendPlacePacket(hand, hitResult)
        }

        if (placeConfig.swing) {
            swingHand(placeConfig.swingType, hand)

            if (!stackInHand.isEmpty && (stackInHand.count != stackCountPre || interaction.hasCreativeInventory())) {
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand)
            }
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

    private fun addPendingPlace(info: PlaceInfo) {
        pendingPlacements.add(info)
        info.pendingInteractionsList.add(info.context)
    }

    private fun removePendingPlace(info: PlaceInfo) {
        pendingPlacements.remove(info)
        info.pendingInteractionsList.remove(info.context)
    }

    private data class PlaceInfo(
        val context: PlaceContext,
        val onPlace: () -> Unit,
        val pendingInteractionsList: MutableCollection<BuildContext>,
        val placeConfig: PlaceConfig
    )

    override fun preEvent(): Event = UpdateManagerEvent.Place().post()
}